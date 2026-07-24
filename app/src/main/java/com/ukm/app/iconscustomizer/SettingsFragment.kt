package com.ukm.app.iconscustomizer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import io.github.libxposed.service.XposedService
import org.json.JSONArray

class SettingsFragment : Fragment(), App.ServiceStateListener {

    private var mService: XposedService? = null
    private var isUpdatingUI = false

    data class IconPackInfo(val name: String, val packageName: String)

    private var installedIconPacks = listOf<IconPackInfo>()
    lateinit var switchEnableTheming: MaterialSwitch
    lateinit var switchHomescreenOnly: MaterialSwitch
    lateinit var switchFallbackIcons: MaterialSwitch
    lateinit var rowIconPackList: LinearLayout
    lateinit var tvIconPackSummary: TextView
    lateinit var iconPackContainer: LinearLayout
    lateinit var rowFallbackPack: LinearLayout
    lateinit var tvFallbackPack: TextView
    lateinit var rowApplyCustom: LinearLayout
    lateinit var sliderIconSize: Slider
    lateinit var sliderFallbackIconSize: Slider

    lateinit var sliderDockCornerRadius: Slider
    lateinit var switchMonet: MaterialSwitch
    lateinit var rowMonetFg: LinearLayout
    lateinit var rowMonetBg: LinearLayout
    lateinit var switchEnableDock: MaterialSwitch
    lateinit var switchEnableMonetDockFolder: MaterialSwitch
    lateinit var rowDockBg: LinearLayout
    lateinit var sliderDockOpacity: Slider
    lateinit var switchMonetClock: MaterialSwitch
    lateinit var rowClockColor: LinearLayout

    private val colorPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val colorId = result.data?.getIntExtra("RETURNED_COLOR_ID", 0) ?: 0
            val targetKey = result.data?.getStringExtra("RETURNED_TARGET_KEY")

            view?.let { v ->
                when (targetKey) {
                    "monet_fg_color" -> updateColorPreview(v, R.id.img_preview_fg, colorId)
                    "monet_bg_color" -> updateColorPreview(v, R.id.img_preview_bg, colorId)
                    "monet_folder_dock_bg_color" -> updateColorPreview(
                        v,
                        R.id.img_preview_dock_bg,
                        colorId
                    )

                    "monet_clock_color" -> updateColorPreview(v, R.id.img_preview_clock, colorId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = requireActivity().findViewById<MaterialToolbar>(R.id.materialToolbar)
        toolbar.title = getString(R.string.app_name)
        installedIconPacks = getInstalledIconPacks(requireContext())
        setupInteractions(view)
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        Log.i(MainHook.TAG, "Service State Changed")
        this.mService = service
        requireActivity().runOnUiThread {
            applyServiceStateToUI(view)
        }
    }

    private fun setupInteractions(view: View) {
        switchEnableTheming = view.findViewById(R.id.switch_enable_theming)
        switchHomescreenOnly = view.findViewById(R.id.switch_homescreen_only)
        switchFallbackIcons = view.findViewById(R.id.switch_fallback_icons)
        rowIconPackList = view.findViewById(R.id.row_icon_pack_list)
        tvIconPackSummary = view.findViewById(R.id.tv_icon_pack_summary)
        iconPackContainer = view.findViewById(R.id.icon_pack_container)
        rowFallbackPack = view.findViewById(R.id.row_fallback_icon_pack)
        tvFallbackPack = view.findViewById(R.id.tv_fallback_pack)
        rowApplyCustom = view.findViewById(R.id.row_apply_custom)
        sliderIconSize = view.findViewById(R.id.slider_icon_size)
        sliderFallbackIconSize = view.findViewById(R.id.slider_fallback_icon_size)

        switchEnableTheming.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_themed_icons", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        switchHomescreenOnly.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("themed_icons_homescreen_only", isChecked)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        switchFallbackIcons.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_fallback_icons", isChecked)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowIconPackList.setOnClickListener {
            showIconPackManager(view)
        }

        rowFallbackPack.setOnClickListener {
            showFallbackPackPicker(view)
        }

        rowApplyCustom.setOnClickListener {
            val prefs = getRemotePrefs()
            val packs = getIconPackList(prefs)
            if (packs.isNotEmpty()) {
                val fragment = AllAppsFragment().apply {
                    arguments = Bundle().apply {
                        putString("EXTRA_ICON_PACK_LIST", JSONArray(packs).toString())
                    }
                }
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        sliderIconSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref("icon_size", value.toInt())
            }
        }
        sliderIconSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UIHelpers.restartLauncher(requireContext())
            }
        })

        sliderFallbackIconSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref("fallback_icon_size", value.toInt())
            }
        }
        sliderFallbackIconSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UIHelpers.restartLauncher(requireContext())
            }
        })

        switchMonet = view.findViewById(R.id.switch_enable_monet)
        rowMonetFg = view.findViewById(R.id.row_monet_fg)
        rowMonetBg = view.findViewById(R.id.row_monet_bg)

        switchMonet.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_monet_colors", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowMonetFg.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", getString(R.string.foreground_color))
                putExtra("EXTRA_TARGET_KEY", "monet_fg_color")
                putExtra("EXTRA_TYPE_KEY", "selected_fg_color_name")
            }
            colorPickerLauncher.launch(intent)
        }

        rowMonetBg.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", getString(R.string.background_color))
                putExtra("EXTRA_TARGET_KEY", "monet_bg_color")
                putExtra("EXTRA_TYPE_KEY", "selected_bg_color_name")
            }
            colorPickerLauncher.launch(intent)
        }

        switchEnableDock = view.findViewById(R.id.switch_enable_dock)
        switchEnableMonetDockFolder = view.findViewById(R.id.switch_monet_dock_folder)
        rowDockBg = view.findViewById(R.id.row_dock_bg)
        sliderDockOpacity = view.findViewById(R.id.slider_dock_opacity)
        sliderDockCornerRadius = view.findViewById(R.id.slider_dock_corners)

        switchEnableDock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_dock", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        switchEnableMonetDockFolder.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("theme_dock_folder", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowDockBg.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", getString(R.string.dock_bg_color))
                putExtra("EXTRA_TARGET_KEY", "monet_folder_dock_bg_color")
                putExtra("EXTRA_TYPE_KEY", "selected_monet_folder_dock_bg_color")
            }
            colorPickerLauncher.launch(intent)
        }

        sliderDockOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref("dock_folder_opacity", value.toInt())
            }
        }
        sliderDockCornerRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref("dock_corner_radius", value.toInt())
            }
        }
        sliderDockOpacity.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UIHelpers.restartLauncher(requireContext())
            }
        })
        sliderDockCornerRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UIHelpers.restartLauncher(requireContext())
            }
        })

        switchMonetClock = view.findViewById(R.id.switch_monet_clock)
        rowClockColor = view.findViewById(R.id.row_clock_color)

        switchMonetClock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_themed_clock", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowClockColor.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", getString(R.string.clock_color))
                putExtra("EXTRA_TARGET_KEY", "monet_clock_color")
                putExtra("EXTRA_TYPE_KEY", "selected_monet_clock_color")
            }
            colorPickerLauncher.launch(intent)
        }

        view.findViewById<MaterialButton>(R.id.btn_restart_launcher).setOnClickListener {
            if (mService != null) {
                val success = UIHelpers.restartLauncher(requireContext())
                Toast.makeText(
                    context,
                    if (success) getString(R.string.launcher_restarted) else getString(R.string.error_restarting),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ====================================================================
    // Icon Pack Priority Management
    // ====================================================================

    private fun getRemotePrefs(): SharedPreferences? {
        return mService?.getRemotePreferences(PREF_NAME)
    }

    private fun getIconPackList(prefs: SharedPreferences?): List<String> {
        val json = prefs?.getString("icon_pack_list", null)
        if (!json.isNullOrEmpty()) {
            try {
                val arr = JSONArray(json)
                return (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {}
        }
        // Backward compat: migrate from old single pack
        val single = prefs?.getString("icon_pack", "none")
        return if (single != null && single != "none") listOf(single) else emptyList()
    }

    private fun getIconPackLabel(packageName: String): String {
        return installedIconPacks.find { it.packageName == packageName }?.name ?: packageName
    }

    private fun showIconPackManager(view: View) {
        val prefs = getRemotePrefs() ?: return
        val packs = getIconPackList(prefs).toMutableList()

        // 对话框内容容器
        val scrollContent = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt())
        }
        scrollContent.addView(container)

        // RecyclerView（支持拖拽排序）
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            id = View.generateViewId()
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        container.addView(recyclerView)

        // 适配器
        class PackAdapter : RecyclerView.Adapter<PackAdapter.VH>() {
            inner class VH(view: View) : RecyclerView.ViewHolder(view) {
                val dragHandle: TextView = view.findViewById(R.id.dragHandle)
                val number: TextView = view.findViewById(R.id.itemNumber)
                val icon: ImageView = view.findViewById(R.id.itemIcon)
                val name: TextView = view.findViewById(R.id.itemName)
                val removeBtn: TextView = view.findViewById(R.id.itemRemove)
            }

            override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pack_drag, parent, false)
                return VH(view)
            }

            override fun getItemCount(): Int = packs.size

            override fun onBindViewHolder(h: VH, pos: Int) {
                val pkg = packs[pos]
                val label = getIconPackLabel(pkg)
                h.number.text = "${pos + 1}"
                h.name.text = label
                loadPackIcon(pkg)?.let { h.icon.setImageDrawable(it) }
                h.removeBtn.setOnClickListener {
                    packs.removeAt(pos)
                    notifyDataSetChanged()
                }
            }
        }

        val adapter = PackAdapter()
        recyclerView.adapter = adapter

        // 拖拽排序
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, dragged: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = dragged.adapterPosition
                val to = target.adapterPosition
                if (from < 0 || to < 0) return false
                val item = packs.removeAt(from)
                packs.add(to, item)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }
            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                viewHolder.itemView.alpha = 1f
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)

        // 长按拖拽手柄启动拖拽
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val vh = recyclerView.getChildViewHolder(child)
                    if (vh is PackAdapter.VH) {
                        vh.dragHandle.setOnLongClickListener {
                            touchHelper.startDrag(vh)
                            true
                        }
                    }
                }
            }
        })

        // 添加图标包按钮
        val addBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.add_icon_pack)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12f).toInt() }
            setOnClickListener {
                val available = installedIconPacks.filter { it.packageName !in packs }
                if (available.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.all_packs_added), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val names = available.map { it.name }.toTypedArray()
                val pkgs = available.map { it.packageName }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.add_icon_pack))
                    .setItems(names) { _, which ->
                        packs.add(pkgs[which])
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
        container.addView(addBtn)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.icon_pack_priority))
            .setMessage(getString(R.string.choose_icon_pack_manage))
            .setView(scrollContent)
            .setPositiveButton(getString(R.string.done)) { _, _ ->
                saveIconPackList(packs)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveIconPackList(packs: List<String>) {
        val json = JSONArray(packs).toString()
        UIHelpers.pushRemotePref("icon_pack_list", json)
        // Keep old single field in sync for backward compat
        UIHelpers.pushRemotePref("icon_pack", packs.firstOrNull() ?: "none")
    }

    // ====================================================================
    // Fallback Icon Pack Selector
    // ====================================================================

    private fun showFallbackPackPicker(view: View) {
        val prefs = getRemotePrefs() ?: return
        val currentFallback = prefs.getString("fallback_icon_pack", "none") ?: "none"

        // 所有可选的包
        val allKnown = mutableListOf("none")
        allKnown.addAll(installedIconPacks.map { it.packageName })
        // 去重
        val seen = mutableSetOf<String>()
        val items = allKnown.filter { seen.add(it) }

        val currentIndex = items.indexOf(currentFallback).coerceAtLeast(0)

        // 构建带图标的对话框列表
        val contentView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentView.addView(listContainer)

        val selectedIndex = intArrayOf(currentIndex)

        fun renderList() {
            listContainer.removeAllViews()
            items.forEachIndexed { idx, pkg ->
                val isNone = pkg == "none"
                val label = if (isNone) getString(R.string.none) else getIconPackLabel(pkg)
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(16f).toInt(), dpToPx(10f).toInt(), dpToPx(16f).toInt(), dpToPx(10f).toInt())
                    background = if (idx == selectedIndex[0])
                        GradientDrawable().apply { setColor(Color.parseColor("#18000000")); cornerRadius = dpToPx(8f) }
                    else null
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedIndex[0] = idx
                        renderList()
                    }
                }
                // 选中标记
                row.addView(TextView(requireContext()).apply {
                    text = if (idx == selectedIndex[0]) "●" else "○"
                    textSize = 16f
                    setTextColor(if (idx == selectedIndex[0]) ContextCompat.getColor(requireContext(), com.google.android.material.R.color.material_dynamic_primary50)
                        else Color.parseColor("#888888"))
                    layoutParams = LinearLayout.LayoutParams(dpToPx(28f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                // APK 图标（None 时无图标）
                if (!isNone) {
                    row.addView(createPackIconView(pkg).apply {
                        (layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = dpToPx(10f).toInt()
                    })
                } else {
                    row.addView(TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(28f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                    })
                }
                // 名称
                row.addView(TextView(requireContext()).apply {
                    text = label
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                listContainer.addView(row)
            }
        }
        renderList()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.fallback_icon_pack))
            .setView(contentView)
            .setPositiveButton(getString(R.string.done)) { _, _ ->
                val selected = items[selectedIndex[0]]
                UIHelpers.pushRemotePref("fallback_icon_pack", selected)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ====================================================================
    // UI State
    // ====================================================================

    private fun applyServiceStateToUI(view: View?) {
        if (view == null) return

        if (mService == null) {
            context?.let {
                Toast.makeText(it, getString(R.string.service_not_started), Toast.LENGTH_SHORT).show()
            }
            setUiEnabled(view, false)
            return
        }
        setUiEnabled(view, true)

        val prefs = mService!!.getRemotePreferences(PREF_NAME)

        val isThemingEnabled = prefs.getBoolean("enable_themed_icons", false)
        val isMonetCustomization = prefs.getBoolean("enable_monet_colors", false)
        val isThemeDockFolder = prefs.getBoolean("theme_dock_folder", false)
        val isDockEnabled = prefs.getBoolean("enable_dock", false)
        val isThemeClockWidget = prefs.getBoolean("enable_themed_clock", false)

        val iconSize = prefs.getInt("icon_size", 180).toFloat()
        val dockOpacity = prefs.getInt("dock_folder_opacity", 200).toFloat()
        val dockRadius = prefs.getInt("dock_corner_radius", 60).toFloat()

        isUpdatingUI = true

        switchEnableTheming.isChecked = isThemingEnabled
        switchHomescreenOnly.isChecked = prefs.getBoolean("themed_icons_homescreen_only", false)
        switchFallbackIcons.isChecked = prefs.getBoolean("enable_fallback_icons", true)
        switchMonet.isChecked = isMonetCustomization
        switchEnableDock.isChecked = prefs.getBoolean("enable_dock", false)
        switchEnableMonetDockFolder.isChecked = isThemeDockFolder
        switchMonetClock.isChecked = isThemeClockWidget
        sliderIconSize.value = iconSize
        sliderFallbackIconSize.value = prefs.getInt("fallback_icon_size", 150).toFloat()
        sliderDockOpacity.value = dockOpacity
        sliderDockCornerRadius.value = dockRadius
        isUpdatingUI = false

        // Update icon pack summary
        val packList = getIconPackList(prefs)
        if (packList.isNotEmpty()) {
            tvIconPackSummary.text = getString(R.string.icon_pack_summary, packList.size)
        } else {
            tvIconPackSummary.text = getString(R.string.icon_pack_none)
        }
        buildPackListInline(packList)

        // Update fallback pack
        val fbPack = prefs.getString("fallback_icon_pack", "none") ?: "none"
        tvFallbackPack.text = if (fbPack == "none") getString(R.string.none) else getIconPackLabel(fbPack)

        updateColorPreview(view, R.id.img_preview_fg, prefs.getInt("monet_fg_color", 0))
        updateColorPreview(view, R.id.img_preview_bg, prefs.getInt("monet_bg_color", 0))
        updateColorPreview(
            view,
            R.id.img_preview_dock_bg,
            prefs.getInt("monet_folder_dock_bg_color", 0)
        )
        updateColorPreview(view, R.id.img_preview_clock, prefs.getInt("monet_clock_color", 0))

        // Toggle visibility
        val vTheming = if (isThemingEnabled) View.VISIBLE else View.GONE
        switchHomescreenOnly.visibility = vTheming
        view.findViewById<View>(R.id.div_homescreen_only).visibility = vTheming
        switchFallbackIcons.visibility = vTheming
        view.findViewById<View>(R.id.div_fallback_icons).visibility = vTheming
        view.findViewById<View>(R.id.row_icon_pack_list).visibility = vTheming
        view.findViewById<View>(R.id.div_icon_pack).visibility = vTheming
        iconPackContainer.visibility = vTheming
        rowFallbackPack.visibility = vTheming
        view.findViewById<View>(R.id.title_monet_colors).visibility = vTheming
        view.findViewById<View>(R.id.card_monet_colors).visibility = vTheming

        val anyPack = packList.firstOrNull()
        val vApplyCustom = if (isThemingEnabled && anyPack != null) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_apply_custom).visibility = vApplyCustom
        view.findViewById<View>(R.id.div_apply_custom).visibility = vApplyCustom

        val vMonetColors = if (isMonetCustomization) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_monet_fg).visibility = vMonetColors
        view.findViewById<View>(R.id.div_monet_colors).visibility = vMonetColors
        view.findViewById<View>(R.id.row_monet_bg).visibility = vMonetColors
        view.findViewById<View>(R.id.div_monet_bg).visibility = vMonetColors

        val vDockMonet = if (isThemeDockFolder) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_dock_bg).visibility = vDockMonet
        view.findViewById<View>(R.id.div_dock_bg).visibility = vDockMonet

        val vDockFolder = if (isThemeDockFolder) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_dock_opacity).visibility = vDockFolder
        view.findViewById<View>(R.id.div_dock_folder).visibility = vDockFolder

        val vDockCornerRadius = if (isDockEnabled) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_dock_corners).visibility = vDockCornerRadius

        val vClock = if (isThemeClockWidget) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_clock_color).visibility = vClock
        view.findViewById<View>(R.id.div_clock_color).visibility = vClock
    }

    /** 加载图标包 App 自身的图标 */
    private fun loadPackIcon(packageName: String): Drawable? {
        return try {
            val pm = requireContext().packageManager
            pm.getApplicationIcon(packageName)
        } catch (_: Exception) { null }
    }

    /** 创建带 APK 图标的 ImageView（统一的尺寸和圆角） */
    private fun createPackIconView(packageName: String): ImageView {
        val iconView = ImageView(requireContext())
        val iconSizeDp = dpToPx(28f).toInt()
        iconView.layoutParams = LinearLayout.LayoutParams(iconSizeDp, iconSizeDp)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        loadPackIcon(packageName)?.let { iconView.setImageDrawable(it) }
        return iconView
    }

    private fun buildPackListInline(packs: List<String>) {
        iconPackContainer.removeAllViews()
        if (packs.isEmpty()) {
            iconPackContainer.visibility = View.GONE
            return
        }
        iconPackContainer.visibility = View.VISIBLE
        packs.forEachIndexed { idx, pkg ->
            val label = getIconPackLabel(pkg)
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    dpToPx(16f).toInt(), dpToPx(4f).toInt(),
                    dpToPx(16f).toInt(), dpToPx(4f).toInt()
                )
            }
            // 优先级编号
            row.addView(TextView(requireContext()).apply {
                text = "${idx + 1}"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                val size = dpToPx(22f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(0, 0, dpToPx(6f).toInt(), 0)
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33000000"))
                    cornerRadius = dpToPx(11f)
                }
                setTextColor(Color.parseColor("#99000000"))
            })
            // APK 图标（右边距 6dp 产生图标与名称之间的间隔）
            row.addView(createPackIconView(pkg).apply {
                (layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = dpToPx(6f).toInt()
            })
            // 包名（紧接着图标，无额外间距）
            row.addView(TextView(requireContext()).apply {
                text = label
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            iconPackContainer.addView(row)
        }
    }

    private fun updateColorPreview(view: View, imageViewId: Int, savedColor: Int) {
        val imageView = view.findViewById<ImageView>(imageViewId) ?: return
        val resolvedColor = try {
            if (savedColor != 0) ContextCompat.getColor(
                requireContext(),
                savedColor
            ) else Color.TRANSPARENT
        } catch (_: Exception) {
            Color.TRANSPARENT
        }
        val colorIcon = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12f)
            setColor(resolvedColor)
            setStroke(dpToPx(1f).toInt(), "#33000000".toColorInt())
        }
        imageView.setImageDrawable(colorIcon)
    }

    private fun setUiEnabled(view: View, isEnabled: Boolean) {
        switchEnableTheming.parent?.requestLayout()
        view.isEnabled = isEnabled
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableListOf<IconPackInfo>()
        val actions = arrayOf("com.novalauncher.THEME", "org.adw.launcher.THEMES")
        for (action in actions) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (info in resolveInfos) {
                val packageName = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                if (iconPacks.none { it.packageName == packageName }) {
                    iconPacks.add(IconPackInfo(label, packageName))
                }
            }
        }
        return iconPacks.sortedBy { it.name }
    }
}