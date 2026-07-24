package com.ukm.app.iconscustomizer

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class IconPickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UKMTAG"
    }

    /** 图标条目：包含图标所在的包和 drawable 名称 */
    data class IconEntry(
        val pack: String,
        val drawableName: String,
        val packLabel: String
    )

    /** Chip/预览数据：包名、标签、匹配的 drawable 名、匹配的图标 */
    private data class ChipPreviewData(
        val pkg: String,
        val label: String,
        val matchedName: String,
        val matchedIcon: Drawable?
    )

    private lateinit var appName: String
    private lateinit var componentString: String
    private lateinit var adapter: IconGridAdapter
    private val allEntries = mutableListOf<IconEntry>()
    private val activePacks = mutableSetOf<String>()
    private var iconPackList: List<String> = emptyList()
    private var packLabels: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_icon_picker)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val materialToolbar = findViewById<MaterialToolbar>(R.id.materialToolbar)
        setSupportActionBar(materialToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        materialToolbar.setNavigationOnClickListener { finish() }

        // 读取传入参数
        val packsJson = intent.getStringExtra("EXTRA_ICON_PACK_LIST") ?: return finish()
        iconPackList = try {
            JSONArray(packsJson).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (_: Exception) { return finish() }

        appName = intent.getStringExtra("EXTRA_APP_NAME") ?: "App"
        componentString = intent.getStringExtra("EXTRA_COMPONENT_STRING") ?: ""

        // 建立包名→标签映射
        packLabels = iconPackList.associateWith { getPackLabel(it) }

        // UI 初始化
        val currentIconImage = findViewById<ImageView>(R.id.currentIconImage)
        val appNameTextView = findViewById<TextView>(R.id.themingAppName)
        val recyclerView = findViewById<RecyclerView>(R.id.iconRecyclerView)
        val spinner = findViewById<ProgressBar>(R.id.loadingSpinner)
        val searchBox = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchIconEditText)
        val chipGroup = findViewById<ChipGroup>(R.id.packChipGroup)
        findViewById<TextView>(R.id.themingTitle).text = getString(R.string.currently_theming)
        findViewById<TextView>(R.id.currentLabel).text = getString(R.string.current_label)
        searchBox.hint = getString(R.string.search_icons)

        appNameTextView.text = appName
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = IconGridAdapter(emptyList())
        recyclerView.adapter = adapter

        // 先加载当前图标
        lifecycleScope.launch { loadCurrentIcon(currentIconImage) }

        // 异步加载所有图标包的全部图标
        lifecycleScope.launch {
            val allIcons = withContext(Dispatchers.IO) { loadAllEntries() }
            allEntries.addAll(allIcons)
            activePacks.add(iconPackList.firstOrNull() ?: "")
            spinner.visibility = View.GONE

            // 构建 Chip 筛选栏
            buildChips(chipGroup)
            adapter.updateData(allEntries)
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { adapter.filter(s.toString()) }
        })
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.icon_picker_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuResetDefault -> {
                resetIconToDefault(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ====================================================================
    // 数据加载
    // ====================================================================

    /** 从所有配置的图标包加载全部图标 */
    private fun loadAllEntries(): List<IconEntry> {
        val result = mutableListOf<IconEntry>()
        for (pkg in iconPackList) {
            try {
                val icons = IconPackHelper.getAllIconsFromPack(this, pkg)
                val label = packLabels[pkg] ?: pkg
                for (name in icons) {
                    result.add(IconEntry(pkg, name, label))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load icons from $pkg: ${e.message}")
            }
        }
        return result
    }

    /** 获取图标包的显示名称 */
    private fun getPackLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) { packageName }
    }

    // ====================================================================
    // 匹配预览 + Chip 筛选栏
    // ====================================================================

    private fun buildChips(chipGroup: ChipGroup) {
        chipGroup.removeAllViews()
        if (iconPackList.size <= 1) return
        chipGroup.visibility = View.VISIBLE
        chipGroup.isSingleSelection = true

        lifecycleScope.launch {
            val chipData = withContext(Dispatchers.IO) {
                iconPackList.map { pkg ->
                    val label = packLabels[pkg] ?: pkg
                    val map = IconPackHelper.getAppFilterMap(this@IconPickerActivity, pkg)
                    val matchedName = map[componentString] ?: map.entries.firstOrNull {
                        it.key.startsWith("ComponentInfo{${componentString.substringAfter("{").substringBefore("/")}/")
                    }?.value ?: ""
                    val matchedIcon = if (matchedName.isNotEmpty()) {
                        IconPackHelper.loadIcon(this@IconPickerActivity, pkg, matchedName)
                    } else null
                    ChipPreviewData(pkg, label, matchedName, matchedIcon)
                }
            }

            buildPackPreview(chipData)

            for (idx in chipData.indices) {
                val data = chipData[idx]
                val chip = Chip(this@IconPickerActivity).apply {
                    text = data.label
                    isCheckable = true
                    isChecked = idx == 0
                    if (data.matchedIcon != null) {
                        chipIcon = data.matchedIcon
                        chipIconSize = dpToPx(22f)
                    }
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) activePacks.add(data.pkg)
                        else activePacks.remove(data.pkg)
                        adapter.filterActivePacks(activePacks)
                    }
                }
                chipGroup.addView(chip)
            }
        }
    }

    /** Chip 上方：各图标包匹配该应用的专属图标预览 */
    private fun buildPackPreview(chipData: List<ChipPreviewData>) {
        val container = findViewById<LinearLayout>(R.id.packPreviewContainer) ?: return
        val scroll = findViewById<HorizontalScrollView>(R.id.packPreviewScroll) ?: return
        container.removeAllViews()

        var hasMatch = false
        for (data in chipData) {
            if (data.matchedIcon == null) continue
            hasMatch = true

            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(80f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dpToPx(4f).toInt(), 0, dpToPx(4f).toInt(), 0) }
                radius = dpToPx(12f)
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT
                ))
            }

            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(dpToPx(8f).toInt(), dpToPx(8f).toInt(),
                    dpToPx(8f).toInt(), dpToPx(8f).toInt())
            }

            inner.addView(ImageView(this).apply {
                setImageDrawable(data.matchedIcon)
                val s = dpToPx(56f).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
            inner.addView(TextView(this).apply {
                text = data.matchedName; textSize = 10f; maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setTextColor(androidx.core.content.ContextCompat.getColorStateList(
                    this@IconPickerActivity, android.R.color.secondary_text_light
                ) ?: android.content.res.ColorStateList.valueOf(0x99000000.toInt()))
            })
            inner.addView(TextView(this).apply {
                text = data.label; textSize = 8f; alpha = 0.6f; maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(dpToPx(6f).toInt(), dpToPx(1f).toInt(), dpToPx(6f).toInt(), dpToPx(1f).toInt())
            })

            card.addView(inner)
            card.setOnClickListener {
                val entry = IconEntry(data.pkg, data.matchedName, data.label)
                saveIconChoice(entry)
            }
            container.addView(card)
        }
        scroll.visibility = if (hasMatch) View.VISIBLE else View.GONE
    }

    private fun dpToPx(dp: Float): Float =
        android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    // ====================================================================
    // 当前图标加载
    // ====================================================================

    private suspend fun loadCurrentIcon(imageView: ImageView) {
        val prefs = App.mService?.getRemotePreferences(MainActivity.PREF_NAME)
            ?: getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        val currentLabel = findViewById<TextView>(R.id.currentLabel)

        // 检查所有图标包的手动覆盖
        for (pkg in iconPackList) {
            val manualKey = "custom_icon_${pkg}_$componentString"
            val drawableName = prefs.getString(manualKey, null)
            if (!drawableName.isNullOrEmpty()) {
                val d = withContext(Dispatchers.IO) {
                    IconPackHelper.loadIcon(this@IconPickerActivity, pkg, drawableName)
                }
                if (d != null) {
                    imageView.setImageDrawable(d)
                    currentLabel.text = packLabels[pkg] ?: pkg
                    return
                }
            }
        }

        // 找第一个匹配的图标包
        for (pkg in iconPackList) {
            val map = withContext(Dispatchers.IO) {
                IconPackHelper.getAppFilterMap(this@IconPickerActivity, pkg)
            }
            val dn = map[componentString] ?: map.entries.firstOrNull {
                it.key.startsWith("ComponentInfo{${componentString.substringAfter("{").substringBefore("/")}/")
            }?.value
            if (dn != null) {
                val d = withContext(Dispatchers.IO) {
                    IconPackHelper.loadIcon(this@IconPickerActivity, pkg, dn)
                }
                if (d != null) {
                    imageView.setImageDrawable(d)
                    currentLabel.text = packLabels[pkg] ?: pkg
                    return
                }
            }
        }

        // 回退到原生图标
        currentLabel.text = getString(R.string.unthemed_stock)
        loadStockAppIcon(imageView)
    }

    private fun loadStockAppIcon(imageView: ImageView) {
        try {
            val pkgName = componentString.substringAfter("{").substringBefore("/")
            imageView.setImageDrawable(packageManager.getApplicationIcon(pkgName))
        } catch (_: Exception) {}
    }

    // ====================================================================
    // 保存 / 重置
    // ====================================================================

    private fun saveIconChoice(entry: IconEntry) {
        val manualOverrideKey = "custom_icon_${entry.pack}_$componentString"
        // 清除该组件在其他包的所有旧覆盖，避免 Phase 1 优先返回旧包覆盖
        for (pkg in iconPackList) {
            if (pkg == entry.pack) continue
            val oldKey = "custom_icon_${pkg}_$componentString"
            UIHelpers.pushRemotePref(oldKey, "" as Any)  // 写空字符串 ≈ 清除
            getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE).edit(commit = true) {
                remove(oldKey)
            }
        }
        // 保存新覆盖
        UIHelpers.pushLocalPref(this, manualOverrideKey, entry.drawableName)
        val remoteSaved = UIHelpers.pushRemotePref(manualOverrideKey, entry.drawableName)
        val toastMsg = if (remoteSaved) {
            getString(R.string.icon_set, entry.drawableName, entry.packLabel)
        } else {
            "⚠️ 远程保存失败: ${entry.drawableName}"
        }
        if (!remoteSaved && App.mService != null) {
            Log.w(TAG, "pushRemotePref returned false but mService not null for key=$manualOverrideKey")
        }
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        UIHelpers.restartLauncher(this)
        finish()
    }

    private fun resetIconToDefault() {
        val remotePrefs = App.mService?.getRemotePreferences(MainActivity.PREF_NAME)
            ?: getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        val localPrefs = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)

        for (pkg in iconPackList) {
            val key = "custom_icon_${pkg}_$componentString"
            remotePrefs.edit { remove(key) }
            localPrefs.edit(commit = true) { remove(key) }
        }
        Toast.makeText(this, getString(R.string.icon_reset), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::adapter.isInitialized) adapter.cleanUp()
    }

    // ====================================================================
    // 适配器
    // ====================================================================

    inner class IconGridAdapter(private var displayed: List<IconEntry>) :
        RecyclerView.Adapter<IconGridAdapter.ViewHolder>() {

        private val cache = LruCache<String, Drawable>(200)
        private val jobs = mutableMapOf<String, Job>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconImage: ImageView = view.findViewById(R.id.singleIconImage)
            val iconName: TextView = view.findViewById(R.id.iconName)
            val packLabel: TextView = view.findViewById(R.id.iconPackLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_icon, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = displayed.size

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val entry = displayed[pos]
            h.iconName.text = entry.drawableName
            h.packLabel.text = entry.packLabel

            jobs[entry.drawableName]?.cancel()
            h.iconImage.tag = entry.drawableName
            h.iconImage.setImageDrawable(null)

            val cached = cache.get(entry.drawableName)
            if (cached != null) {
                h.iconImage.setImageDrawable(cached)
            } else {
                jobs[entry.drawableName] = lifecycleScope.launch {
                    val d = withContext(Dispatchers.IO) {
                        IconPackHelper.loadIcon(this@IconPickerActivity, entry.pack, entry.drawableName)
                    }
                    if (d != null) {
                        cache.put(entry.drawableName, d)
                        if (h.iconImage.tag == entry.drawableName) {
                            h.iconImage.setImageDrawable(d)
                        }
                    }
                }
            }

            h.itemView.setOnClickListener {
                saveIconChoice(entry)
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(data: List<IconEntry>) { displayed = data; notifyDataSetChanged() }

        fun filterActivePacks(active: Set<String>) {
            displayed = allEntries.filter { it.pack in active }
            notifyDataSetChanged()
        }

        fun filter(query: String) {
            displayed = if (query.isEmpty()) {
                allEntries.filter { it.pack in activePacks }
            } else {
                allEntries.filter { it.pack in activePacks && it.drawableName.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }

        fun cleanUp() { cache.evictAll() }
    }
}