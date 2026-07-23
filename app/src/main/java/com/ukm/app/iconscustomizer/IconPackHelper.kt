package com.ukm.app.iconscustomizer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import org.xmlpull.v1.XmlPullParser

object IconPackHelper {

    private const val TAG = "UKMTAG"
    @Volatile
    private var cachedAppFilterMap: Map<String, String>? = null
    @Volatile
    private var currentIconPack: String? = null

    @SuppressLint("QueryPermissionsNeeded", "DiscouragedApi")
    fun getAppFilterMap(context: Context, iconPackPackageName: String): Map<String, String> {
        synchronized(this) {
            if (iconPackPackageName == currentIconPack && cachedAppFilterMap != null) {
                return cachedAppFilterMap!!
            }

            val iconMap = mutableMapOf<String, String>()
            try {
                val iconPackContext =
                    context.createPackageContext(
                        iconPackPackageName,
                        Context.CONTEXT_IGNORE_SECURITY
                    )
                val resId =
                    iconPackContext.resources.getIdentifier("appfilter", "xml", iconPackPackageName)

                if (resId == 0) {
                    Log.e(TAG, "appfilter.xml not found in $iconPackPackageName")
                    return iconMap
                }

                iconPackContext.resources.getXml(resId).use { parser ->
                    var eventType = parser.eventType

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val component = parser.getAttributeValue(null, "component")?.trim()
                            val drawable = parser.getAttributeValue(null, "drawable")?.trim()

                            if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                                iconMap[component] = drawable
                            }
                        }
                        eventType = parser.next()
                    }
                }

                cachedAppFilterMap = iconMap
                currentIconPack = iconPackPackageName
                // 清除旧的 overlay 缓存，下一轮会重新解析
                currentOverlayIconPack = null
                cachedOverlayInfo = null

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse appfilter: ${e.message}")
            }
            return iconMap
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables", "DiscouragedApi")
    fun loadIcon(context: Context, iconPackPackageName: String, drawableName: String): Drawable? {
        return try {
            val iconPackContext =
                context.createPackageContext(iconPackPackageName, Context.CONTEXT_IGNORE_SECURITY)
            val resId = iconPackContext.resources.getIdentifier(
                drawableName,
                "drawable",
                iconPackPackageName
            )
            if (resId != 0) {
                iconPackContext.getDrawable(resId)?.mutate()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon $drawableName: ${e.message}")
            null
        }
    }


    @SuppressLint("DiscouragedApi")
    fun getAllIconsFromPack(context: Context, iconPackPackageName: String): List<String> {
        val icons = mutableSetOf<String>()
        try {
            val iconPackContext =
                context.createPackageContext(iconPackPackageName, Context.CONTEXT_IGNORE_SECURITY)
            val resId =
                iconPackContext.resources.getIdentifier("drawable", "xml", iconPackPackageName)

            if (resId != 0) {
                iconPackContext.resources.getXml(resId).use { parser ->
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (drawable != null) icons.add(drawable)
                        }
                        eventType = parser.next()
                    }
                }
            }

            if (icons.isEmpty()) {
                val map = getAppFilterMap(context, iconPackPackageName)
                icons.addAll(map.values)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all icons: ${e.message}")
        }

        return icons.toList().sorted()
    }

    private fun applyCustomColors(icon: Drawable?, bgColorInt: Int?, fgColorInt: Int?): Drawable? {
        if (icon == null) return null
        val mutatedIcon = icon.mutate()

        if (mutatedIcon is AdaptiveIconDrawable) {
            if (bgColorInt != null) {
                val background = mutatedIcon.background?.mutate()
                background?.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        bgColorInt,
                        BlendModeCompat.SRC_ATOP
                    )
            }
            if (fgColorInt != null) {
                val foreground = mutatedIcon.foreground?.mutate()
                foreground?.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        fgColorInt,
                        BlendModeCompat.SRC_ATOP
                    )
            }
            return mutatedIcon
        }

        if (mutatedIcon is LayerDrawable) {
            if (bgColorInt != null && mutatedIcon.numberOfLayers > 0) {
                val bgLayer = mutatedIcon.getDrawable(0).mutate()
                bgLayer.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    bgColorInt,
                    BlendModeCompat.SRC_ATOP
                )
                mutatedIcon.setDrawable(0, bgLayer)
            }
            if (fgColorInt != null && mutatedIcon.numberOfLayers > 1) {
                val fgLayer = mutatedIcon.getDrawable(1).mutate()
                fgLayer.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    fgColorInt,
                    BlendModeCompat.SRC_ATOP
                )
                mutatedIcon.setDrawable(1, fgLayer)
            }
            return mutatedIcon
        }

        if (fgColorInt != null) {
            mutatedIcon.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                fgColorInt,
                BlendModeCompat.SRC_ATOP
            )
        }

        return mutatedIcon
    }


    // =========================================================================
    // FALLBACK / GENERIC ICON SUPPORT
    // =========================================================================

    /** 纯兜底通用图标的常见命名（仅当没有 overlay 层时使用） */
    private val FALLBACK_DRAWABLE_NAMES = arrayOf(
        "icon", "default", "app", "app_icon", "ic_default", "anonymous", "ic_launcher"
    )

    @Volatile
    private var cachedOverlayInfo: FallbackOverlayInfo? = null
    @Volatile
    private var currentOverlayIconPack: String? = null

    /**
     * ADW / Nova Launcher 标准的兜底图标合成信息
     * 从 appfilter.xml 的 <iconback>/<iconupon>/<iconmask>/<scale> 标签解析
     */
    data class FallbackOverlayInfo(
        val scale: Float,
        val iconBackNames: List<String>,
        val iconUponNames: List<String>,
        val iconMaskNames: List<String>
    )

    /**
     * 从图标包直接加载通用兜底 drawable（无需合成）
     * 仅在 overlay 合成不可用时作为最后手段
     */
    fun loadFallbackDrawable(context: Context, iconPackPackageName: String): Drawable? {
        for (name in FALLBACK_DRAWABLE_NAMES) {
            val drawable = loadIcon(context, iconPackPackageName, name)
            if (drawable != null) {
                Log.d(TAG, "Found fallback drawable: $name in $iconPackPackageName")
                return drawable
            }
        }
        return null
    }

    /**
     * 解析 appfilter.xml 中的 <iconback> / <iconupon> / <iconmask> / <scale>
     * 这些标签定义了如何为未适配的应用生成统一风格的兜底图标
     */
    fun parseFallbackOverlayInfo(context: Context, iconPackPackageName: String): FallbackOverlayInfo? {
        synchronized(this) {
            if (iconPackPackageName == currentOverlayIconPack && cachedOverlayInfo != null) {
                return cachedOverlayInfo
            }
            try {
                val iconPackContext = context.createPackageContext(
                    iconPackPackageName, Context.CONTEXT_IGNORE_SECURITY
                )
                val resId = iconPackContext.resources.getIdentifier(
                    "appfilter", "xml", iconPackPackageName
                )
                if (resId == 0) return null

                var scale = 0.85f
                val iconBack = mutableListOf<String>()
                val iconUpon = mutableListOf<String>()
                val iconMask = mutableListOf<String>()

                iconPackContext.resources.getXml(resId).use { parser ->
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "iconback" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        val v = parser.getAttributeValue(i)
                                        if (!v.isNullOrEmpty()) iconBack.add(v)
                                    }
                                }
                                "iconupon" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        val v = parser.getAttributeValue(i)
                                        if (!v.isNullOrEmpty()) iconUpon.add(v)
                                    }
                                }
                                "iconmask" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        val v = parser.getAttributeValue(i)
                                        if (!v.isNullOrEmpty()) iconMask.add(v)
                                    }
                                }
                                "scale" -> {
                                    parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let {
                                        scale = it.coerceIn(0.1f, 1.5f)
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }

                val info = FallbackOverlayInfo(
                    scale = scale,
                    iconBackNames = iconBack,
                    iconUponNames = iconUpon,
                    iconMaskNames = iconMask
                )
                cachedOverlayInfo = info
                currentOverlayIconPack = iconPackPackageName
                return info
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse fallback overlay info: ${e.message}")
                return null
            }
        }
    }

    /**
     * 将任意 Drawable 渲染到指定尺寸的 Bitmap 中（不改变原 drawable 状态）
     */
    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        val bmp = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }

    /**
     * 根据 ADW / Nova 图标包规范为未适配应用合成兜底图标。
     *
     * 实现严格参照 CM11 IconPackHelper.IconCustomizer.createIconBitmap():
     * https://github.com/ResurrectionRemix/android_frameworks_base/commit/9973d6d
     *
     * 合成步骤（同一 Canvas，依次使用不同 PorterDuff 模式）：
     *
     *   ① 画缩放后的原图标                        → 正常绘制
     *   ② iconMask 裁剪（DST_OUT）               → 蒙版透明处保留图标
     *   ③ iconBack 背景（DST_OVER）               → 绘制在已裁切图标**后面**
     *   ④ iconUpon 前景                           → 正常绘制在最上层
     *
     * ⚠ Xfermode 必须通过 canvas.drawBitmap(bitmap, paint) 生效，
     *   不能仅设在一个 Paint 上然后调用 drawable.draw()，
     *   因为 drawable.draw() 使用 drawable 自身的 Paint，不会用传进去的 Paint。
     *
     * DST_OUT  = 清除目标中来源覆盖的部分（透明蒙版=保留图标，不透明蒙版=移除图标）
     * DST_OVER = 绘制在目标内容之后（已有图标的区域不受影响，透明处补上背景）
     */
    fun generateOverlayFallbackIcon(
        context: Context,
        iconPackPackageName: String,
        originalIcon: Drawable,
        overlayInfo: FallbackOverlayInfo
    ): Drawable? {
        return try {
            // ---- 选取各层变体 ----
            val backName  = overlayInfo.iconBackNames.ifEmpty { null }?.random()
            val uponName  = overlayInfo.iconUponNames.ifEmpty { null }?.random()
            val maskName  = overlayInfo.iconMaskNames.ifEmpty { null }?.random()

            val backDrawable  = backName?.let { loadIcon(context, iconPackPackageName, it) }
            val uponDrawable  = uponName?.let { loadIcon(context, iconPackPackageName, it) }
            val maskDrawable  = maskName?.let { loadIcon(context, iconPackPackageName, it) }

            // 至少需要 <iconback> 或 <iconmask> 之一才能合成
            if (backDrawable == null && maskDrawable == null) return null

            // ---- 尺寸 ----
            val size = originalIcon.let {
                val w = it.intrinsicWidth; val h = it.intrinsicHeight
                if (w > 0 && h > 0) maxOf(w, h) else 192
            }

            // ---- 主画布 ----
            val resultBitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // ═══════════════════════════════════════════════════════
            // 第 ① 步：画缩放后的原图标（正常绘制）
            // ═══════════════════════════════════════════════════════
            val icon = originalIcon.mutate()
            icon.setBounds(0, 0, size, size)
            canvas.save()
            canvas.scale(overlayInfo.scale, overlayInfo.scale, size / 2f, size / 2f)
            icon.draw(canvas)
            canvas.restore()
            // → 画布内容: [缩放后的原图标]

            // ═══════════════════════════════════════════════════════
            // 第 ② 步：iconMask 裁剪（DST_OUT）
            //   DST_OUT = [Da*(1-Sa), Dc*(1-Sa)]
            //   蒙版透明处(Sa≈0) → 保留图标(Sa≈Da)
            //   蒙版不透明(Sa≈1) → 移除图标(Sa≈0)
            // ═══════════════════════════════════════════════════════
            if (maskDrawable != null) {
                val maskBmp = drawableToBitmap(maskDrawable, size)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                canvas.drawBitmap(maskBmp, 0f, 0f, paint)
                paint.xfermode = null
            }
            // → 画布内容: [在蒙版"窗口"内可见的原图标]

            // ═══════════════════════════════════════════════════════
            // 第 ③ 步：iconBack 背景（DST_OVER）
            //   DST_OVER = [Sc*(1-Da)+Dc, Sa+Da-Sa*Da]
            //   图标区域(Da>0) → 背景绘制在图标**后面**，不遮挡图标
            //   透明区域(Da=0) → 背景完全可见，补上底色
            // ═══════════════════════════════════════════════════════
            if (backDrawable != null) {
                val backBmp = drawableToBitmap(backDrawable, size)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                canvas.drawBitmap(backBmp, 0f, 0f, paint)
                paint.xfermode = null
            }
            // → 画布内容: [背景(底层) + 裁切图标(上层)]

            // ═══════════════════════════════════════════════════════
            // 第 ④ 步：iconUpon 前景（正常绘制在最上层）
            // ═══════════════════════════════════════════════════════
            if (uponDrawable != null) {
                uponDrawable.setBounds(0, 0, size, size)
                uponDrawable.draw(canvas)
            }
            // → 画布内容: [背景(底层) + 裁切图标(中层) + 前景(顶层)]

            BitmapDrawable(context.resources, resultBitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate overlay fallback icon: ${e.message}")
            null
        }
    }

    /**
     * 生成内置通用兜底图标。
     * 当图标包没有提供任何 <iconback>/<iconmask> 或通用 drawable 时使用。
     *
     * 样式：浅色圆角矩形背景 + 居中缩小的原图标
     *
     * @param size 输出图标尺寸（像素），应从应用设置的 icon_size 读取
     */
    fun generateBuiltInFallbackIcon(
        context: Context,
        originalIcon: Drawable,
        size: Int
    ): Drawable? {
        return try {
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // 圆角矩形背景（使用半透明白色，在任何壁纸上都有柔和效果）
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#18FFFFFF")
            }
            val radius = size * 0.18f
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, bgPaint)

            // 选中边框
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#0DFFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, strokePaint)

            // 居中画缩放后的原图标
            val icon = originalIcon.mutate()
            val scale = 0.65f
            icon.setBounds(0, 0, size, size)
            canvas.save()
            canvas.scale(scale, scale, size / 2f, size / 2f)
            icon.draw(canvas)
            canvas.restore()

            BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate built-in fallback: ${e.message}")
            null
        }
    }

    /**
     * 综合获取兜底图标：
     *
     * 1. 从 appfilter.xml 读 <iconback>/<iconupon>/<iconmask>/<scale>
     *    用这些定义将原图标与背景/蒙版/前景组合（ADW 标准）
     * 2. 如果图标包没有定义 overlay，尝试直接加载通用 drawable
     * 3. 都失败则使用内置兜底图标（圆角矩形+居中图标）
     *
     * @param iconSize 输出图标尺寸（像素），从应用设置的 icon_size 读取
     */
    fun getFallbackIcon(
        context: Context,
        iconPackPackageName: String,
        originalIcon: Drawable,
        iconSize: Int = 192
    ): Drawable? {
        // ===== 方式一（优先）：从 appfilter.xml 读 overlay 定义 =====
        val overlayInfo = parseFallbackOverlayInfo(context, iconPackPackageName)
        if (overlayInfo != null && (overlayInfo.iconBackNames.isNotEmpty() || overlayInfo.iconMaskNames.isNotEmpty())) {
            val result = generateOverlayFallbackIcon(
                context, iconPackPackageName, originalIcon, overlayInfo
            )
            if (result != null) return result
        }

        // ===== 方式二：直接加载通用 drawable =====
        val generic = loadFallbackDrawable(context, iconPackPackageName)
        if (generic != null) return generic

        // ===== 方式三（最终兜底）：内置通用图标 =====
        return generateBuiltInFallbackIcon(context, originalIcon, iconSize)
    }

    fun putColorIntoDrawable(
        context: Context,
        icon: Drawable?,
        bgColorInt: Int?,
        fgColorInt: Int?
    ): Drawable? {
        if (icon == null) return null
        val tintedDrawable = applyCustomColors(icon, bgColorInt, fgColorInt) ?: return null
        val width = if (tintedDrawable.intrinsicWidth > 0) tintedDrawable.intrinsicWidth else 192
        val height = if (tintedDrawable.intrinsicHeight > 0) tintedDrawable.intrinsicHeight else 192
        tintedDrawable.setBounds(0, 0, width, height)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        tintedDrawable.draw(canvas)
        return bitmap.toDrawable(context.resources)
    }
}