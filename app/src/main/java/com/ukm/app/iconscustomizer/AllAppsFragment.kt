package com.ukm.app.iconscustomizer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class AllAppsFragment : Fragment(R.layout.fragment_all_apps) {

    private lateinit var iconPackList: List<String>
    private lateinit var adapter: AppAdapter
    private var allApps = listOf<AppInfo>()
    /** 每个图标包的 appFilterMap: pack -> (component -> drawableName) */
    private var packAppFilterMaps: Map<String, Map<String, String>> = emptyMap()
    private var packLabels: Map<String, String> = emptyMap()
    private var showThemedIcons = true

    private fun getPrefs(): SharedPreferences {
        return requireContext().getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    }

    private fun loadPackList(): List<String> {
        val json = arguments?.getString("EXTRA_ICON_PACK_LIST") ?: getPrefs().getString("icon_pack_list", null)
        if (!json.isNullOrEmpty()) {
            try { return JSONArray(json).let { (0 until it.length()).map { i -> it.getString(i) } } } catch (_: Exception) {}
        }
        val single = arguments?.getString("EXTRA_ICON_PACK") ?: getPrefs().getString("icon_pack", "none")
        return if (single != null && single != "none") listOf(single) else emptyList()
    }

    private fun getPackLabel(packageName: String): String {
        if (packageName in packLabels) return packLabels[packageName]!!
        val label = try {
            val pm = requireContext().packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) { packageName }
        packLabels = packLabels + (packageName to label)
        return label
    }

    data class AppInfo(
        val name: String,
        val componentString: String,
        val stockIcon: Drawable,
        val packageName: String
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = requireActivity().findViewById<MaterialToolbar>(R.id.materialToolbar)
        toolbar.title = getString(R.string.apply_custom_icon_title)
        val searchEditText =
            view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchAppEditText)
        val recyclerView = view.findViewById<RecyclerView>(R.id.appRecyclerView)
        iconPackList = loadPackList()
        if (iconPackList.isEmpty()) { toolbar.title = getString(R.string.icon_pack_none); return }

        showThemedIcons = getPrefs().getBoolean("preview_themed_icons", true)
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        adapter = AppAdapter(emptyList(), requireContext())
        recyclerView.adapter = adapter

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                val toggleItem = menu.add(
                    Menu.NONE,
                    101,
                    Menu.NONE,
                    getString(R.string.show_themed_icons)
                )
                toggleItem.isCheckable = true
                toggleItem.isChecked = showThemedIcons
                toggleItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == 101) {
                    showThemedIcons = !showThemedIcons
                    menuItem.isChecked = showThemedIcons
                    UIHelpers.pushLocalPref(
                        requireContext(),
                        "preview_themed_icons",
                        showThemedIcons
                    )
                    adapter.toggleThemedMode(showThemedIcons)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            // 为每个图标包加载 appFilterMap
            val maps = mutableMapOf<String, Map<String, String>>()
            for (pkg in iconPackList) {
                maps[pkg] = withContext(Dispatchers.IO) {
                    IconPackHelper.getAppFilterMap(requireContext(), pkg)
                }
            }
            packAppFilterMaps = maps
            allApps = withContext(Dispatchers.IO) { loadInstalledApps() }
            adapter.updateData(allApps)
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.clearCache()   // 清除缓存，强制重新加载图标
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::adapter.isInitialized) {
            adapter.cleanUp()
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)

        val appsList = mutableListOf<AppInfo>()
        for (resolveInfo in activities) {
            val pkg = resolveInfo.activityInfo.packageName
            val cls = resolveInfo.activityInfo.name
            val name = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val componentString = "ComponentInfo{$pkg/$cls}"
            appsList.add(AppInfo(name, componentString, icon, pkg))
        }
        return appsList.sortedBy { it.name.lowercase() }
    }

    inner class AppAdapter(
        private var filteredApps: List<AppInfo>,
        private val adapterContext: Context
    ) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        private val prefs = getPrefs()
        private val isMonetEnabled = prefs.getBoolean("enable_monet_colors", false)
        private val monetBgColor: Int? =
            if (prefs.getInt("monet_bg_color", 0) != 0) ContextCompat.getColor(
                adapterContext,
                prefs.getInt("monet_bg_color", 0)
            ) else null
        private val monetFgColor: Int? =
            if (prefs.getInt("monet_fg_color", 0) != 0) ContextCompat.getColor(
                adapterContext,
                prefs.getInt("monet_fg_color", 0)
            ) else null

        private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
        private val memoryCache = LruCache<String, Drawable>(150)
        private var isThemedMode = showThemedIcons

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.appIcon)
            val appName: TextView = view.findViewById(R.id.appName)
            val appAssignedIcon: TextView = view.findViewById(R.id.appAssignedIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_installed_app, parent, false)
            return AppViewHolder(view)
        }

        override fun getItemCount(): Int = filteredApps.size

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = filteredApps[position]
            holder.appName.text = app.name

            // 级联查找：手动覆盖 → pack[0] → pack[1] → ... → pack[n]
            var sourcePack: String? = null  // 最终来源图标包
            var targetDrawableName: String? = null

            // 阶段1: 检查所有图标包的手动覆盖
            for (pkg in iconPackList) {
                val m = prefs.getString("custom_icon_${pkg}_${app.componentString}", null)
                if (!m.isNullOrEmpty()) {
                    sourcePack = pkg
                    targetDrawableName = m
                    break
                }
            }

            // 阶段2: 按优先级级联查找 appfilter 匹配
            if (targetDrawableName == null) {
                for (pkg in iconPackList) {
                    val map = packAppFilterMaps[pkg] ?: continue
                    val dn = map[app.componentString] ?: map.entries.firstOrNull {
                        it.key.startsWith("ComponentInfo{${app.packageName}/")
                    }?.value
                    if (dn != null) {
                        sourcePack = pkg
                        targetDrawableName = dn
                        break
                    }
                }
            }

            // 显示来源状态
            if (sourcePack != null && targetDrawableName != null) {
                val packLabel = getPackLabel(sourcePack)
                holder.appAssignedIcon.text = "使用 $packLabel 图标"
                holder.appAssignedIcon.setTextColor(0xFF4CAF50.toInt())
            } else {
                holder.appAssignedIcon.text = getString(R.string.unthemed_stock)
                holder.appAssignedIcon.setTextColor(0xFFE53935.toInt())
            }

            // 加载预览图标（从来源包加载）
            if (!isThemedMode) {
                holder.appIcon.tag = "stock_${app.componentString}"
                holder.appIcon.setImageDrawable(app.stockIcon)
            } else if (sourcePack != null && targetDrawableName != null) {
                holder.appIcon.tag = targetDrawableName
                val cachedIcon = memoryCache.get(targetDrawableName)
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon.setImageDrawable(app.stockIcon)
                    adapterScope.launch {
                        val finalDrawable = withContext(Dispatchers.IO) {
                            val rawDrawable = IconPackHelper.loadIcon(
                                adapterContext, sourcePack!!, targetDrawableName!!
                            )
                            if (rawDrawable != null && isMonetEnabled) {
                                applyCustomizedColorToIcon(rawDrawable)
                            } else { rawDrawable }
                        }
                        if (finalDrawable != null) {
                            memoryCache.put(targetDrawableName!!, finalDrawable)
                            if (holder.appIcon.tag == targetDrawableName) {
                                holder.appIcon.setImageDrawable(finalDrawable)
                            }
                        }
                    }
                }
            } else {
                holder.appIcon.tag = app.componentString
                holder.appIcon.setImageDrawable(app.stockIcon)
            }

            holder.itemView.setOnClickListener { openIconPicker(app) }
        }

        fun applyCustomizedColorToIcon(drawable: Drawable): Drawable {
            if (monetFgColor == 0 && monetBgColor == 0) return drawable

            return IconPackHelper.putColorIntoDrawable(
                adapterContext,
                drawable,
                monetBgColor,
                monetFgColor
            )!!
        }

        private fun openIconPicker(app: AppInfo) {
            val pickIntent = Intent(adapterContext, IconPickerActivity::class.java).apply {
                putExtra("EXTRA_ICON_PACK_LIST", JSONArray(iconPackList).toString())
                putExtra("EXTRA_APP_NAME", app.name)
                putExtra("EXTRA_COMPONENT_STRING", app.componentString)
            }
            startActivity(pickIntent)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newApps: List<AppInfo>) {
            filteredApps = newApps
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun filter(query: String) {
            filteredApps = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.name.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun toggleThemedMode(isThemed: Boolean) {
            isThemedMode = isThemed
            notifyDataSetChanged()
        }

        fun cleanUp() {
            adapterScope.cancel()
            memoryCache.evictAll()
        }

        /** 清除图标缓存，强制下次绑定重新加载 */
        fun clearCache() {
            memoryCache.evictAll()
        }
    }
}
