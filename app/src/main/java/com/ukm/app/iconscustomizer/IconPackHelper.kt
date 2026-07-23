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
     * 使用 ADW 标准的 iconback / iconupon / iconmask 机制
     * 为未适配的应用合成统一风格的兜底图标。
     *
     * 正确合成顺序：
     * 1. 将 iconback（背景层）与缩放后的原图标组合在一起
     * 2. 用 iconmask（蒙版）统一裁剪整个组合，确保背景+图标形状一致
     * 3. 最后绘制 iconupon（前景装饰层）在最上方
     *
     * 所有图层名均来自图标包 appfilter.xml 中定义的
     * <iconback>/<iconupon>/<iconmask>/<scale> 标签，
     * 不硬编码具体资源名称。
     */
    fun generateOverlayFallbackIcon(
        context: Context,
        iconPackPackageName: String,
        originalIcon: Drawable,
        overlayInfo: FallbackOverlayInfo
    ): Drawable? {
        return try {
            // 确定合成尺寸
            val size = if (originalIcon.intrinsicWidth > 0 && originalIcon.intrinsicHeight > 0) {
                maxOf(originalIcon.intrinsicWidth, originalIcon.intrinsicHeight)
            } else 192

            // 从图标包定义中选取各层（如果有多个变体则随机选一个）
            val backName = overlayInfo.iconBackNames.ifEmpty { null }?.random()
            val uponName = overlayInfo.iconUponNames.ifEmpty { null }?.random()
            val maskName = overlayInfo.iconMaskNames.ifEmpty { null }?.random()

            val backDrawable = backName?.let { loadIcon(context, iconPackPackageName, it) }
            val uponDrawable = uponName?.let { loadIcon(context, iconPackPackageName, it) }
            val maskDrawable = maskName?.let { loadIcon(context, iconPackPackageName, it) }

            // 没有背景层则无法合成
            if (backDrawable == null) return null

            val safeOriginal = originalIcon.mutate()

            // 缩放原图标到指定比例
            val factor = overlayInfo.scale
            val scaledSize = (size * factor).toInt().coerceAtLeast(4)
            val offset = (size - scaledSize) / 2
            safeOriginal.setBounds(offset, offset, offset + scaledSize, offset + scaledSize)

            // ===== 第一步：组合背景 + 原图标 =====
            val composite = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Canvas(composite).apply {
                // 1a. 画背景层（填满整个区域）
                backDrawable.setBounds(0, 0, size, size)
                backDrawable.draw(this)
                // 1b. 画缩放后的原图标在背景之上
                safeOriginal.draw(this)
            }

            // ===== 第二步：用蒙版统一裁剪组合结果 =====
            val masked = if (maskDrawable != null) {
                val maskBitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
                Canvas(maskBitmap).apply {
                    maskDrawable.setBounds(0, 0, size, size)
                    maskDrawable.draw(this)
                }

                // 将 composite（背景+图标）用 mask 裁剪
                val clipped = createBitmap(size, size, Bitmap.Config.ARGB_8888)
                Canvas(clipped).apply {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    // 先画组合图像（背景+图标）
                    drawBitmap(composite, 0f, 0f, paint)
                    // 用蒙版裁剪（DST_IN：保留目标图像中与源图像重叠的部分）
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    drawBitmap(maskBitmap, 0f, 0f, paint)
                }
                clipped
            } else {
                composite
            }

            // ===== 第三步：叠加前景装饰层 =====
            val resultBitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Canvas(resultBitmap).apply {
                // 画蒙版裁剪后的组合（背景+图标）
                drawBitmap(masked, 0f, 0f, null)
                // 画前景装饰层（在最上方）
                uponDrawable?.let { d ->
                    d.setBounds(0, 0, size, size)
                    d.draw(this)
                }
            }

            BitmapDrawable(context.resources, resultBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate overlay fallback icon: ${e.message}")
            null
        }
    }

    /**
     * 综合获取兜底图标：
     *
     * 只读取图标包 appfilter.xml 中定义的兜底图标组合。
     * 不硬编码任何资源名，一切以图标包自身的定义为准。
     *
     * 1. 优先从 appfilter.xml 读 <iconback>/<iconupon>/<iconmask>/<scale>
     *    用这些定义将原图标与背景/蒙版/前景组合
     * 2. 如果图标包没有定义 overlay，尝试直接加载通用 drawable（纯兜底）
     * 3. 都失败则返回 null
     */
    fun getFallbackIcon(
        context: Context,
        iconPackPackageName: String,
        originalIcon: Drawable
    ): Drawable? {
        // ===== 方式一（优先）：从 appfilter.xml 读 overlay 定义 =====
        val overlayInfo = parseFallbackOverlayInfo(context, iconPackPackageName)
        if (overlayInfo != null && overlayInfo.iconBackNames.isNotEmpty()) {
            val result = generateOverlayFallbackIcon(
                context, iconPackPackageName, originalIcon, overlayInfo
            )
            if (result != null) return result
        }

        // ===== 方式二（兜底）：直接加载通用 drawable =====
        return loadFallbackDrawable(context, iconPackPackageName)
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