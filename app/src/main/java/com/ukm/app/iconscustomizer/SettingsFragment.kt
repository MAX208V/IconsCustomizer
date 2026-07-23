package com.ukm.app.iconscustomizer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
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
            val firstPack = packs.firstOrNull()
            if (firstPack != null) {
                val fragment = AllAppsFragment().apply {
                    arguments = Bundle().apply {
                        putString("EXTRA_ICON_PACK", firstPack)
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

        // Build dialog content
        val scrollContent = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(8f).toInt(), dpToPx(8f).toInt(),
                dpToPx(8f).toInt(), dpToPx(4f).toInt()
            )
        }
        scrollContent.addView(container)

        // List of packs
        val packListContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        container.addView(packListContainer)

        fun refreshPackList() {
            packListContainer.removeAllViews()
            packs.forEachIndexed { idx, pkg ->
                val label = getIconPackLabel(pkg)
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(4f).toInt(), dpToPx(8f).toInt(), 0, dpToPx(8f).toInt())
                }
                // Priority number badge
                row.addView(TextView(requireContext()).apply {
                    text = "${idx + 1}."
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(requireContext(), com.google.android.material.R.color.material_on_surface_emphasis_high_type))
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(32f).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                // Pack name
                row.addView(TextView(requireContext()).apply {
                    text = label
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
                // Remove button
                row.addView(TextView(requireContext()).apply {
                    text = getString(R.string.remove_icon_pack)
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), com.google.android.material.R.color.material_dynamic_primary50))
                    setPadding(dpToPx(12f).toInt(), dpToPx(6f).toInt(), dpToPx(12f).toInt(), dpToPx(6f).toInt())
                    setOnClickListener {
                        packs.removeAt(idx)
                        refreshPackList()
                    }
                })
                packListContainer.addView(row)
            }
            // Show hint when empty
            if (packs.isEmpty()) {
                packListContainer.addView(TextView(requireContext()).apply {
                    text = getString(R.string.icon_pack_none)
                    textSize = 14f
                    alpha = 0.6f
                    setPadding(0, dpToPx(12f).toInt(), 0, dpToPx(12f).toInt())
                })
            }
        }

        refreshPackList()

        // Add button
        val addBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.add_icon_pack)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12f).toInt() }
            setOnClickListener {
                // Available packs = installed packs not already in the list
                val available = installedIconPacks.filter { it.packageName !in packs }
                if (available.isEmpty()) {
                    Toast.makeText(requireContext(), "All icon packs already added", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val names = available.map { it.name }.toTypedArray()
                val pkgs = available.map { it.packageName }.toTypedArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.add_icon_pack))
                    .setItems(names) { _, which ->
                        packs.add(pkgs[which])
                        refreshPackList()
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

        val names = mutableListOf(getString(R.string.none))
        val pkgs = mutableListOf("none")
        // Show all installed packs + packs currently in the priority list
        val allKnown = mutableSetOf<String>()
        allKnown.addAll(installedIconPacks.map { it.packageName })
        allKnown.addAll(getIconPackList(prefs))
        val sorted = allKnown.sortedBy { getIconPackLabel(it) }
        for (pkg in sorted) {
            pkgs.add(pkg)
            names.add(getIconPackLabel(pkg))
        }

        val currentIndex = pkgs.indexOf(currentFallback).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.fallback_icon_pack))
            .setSingleChoiceItems(names.toTypedArray(), currentIndex) { dialog, which ->
                val selected = pkgs[which]
                UIHelpers.pushRemotePref("fallback_icon_pack", selected)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
                dialog.dismiss()
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

    /** Build inline list of priority packs with remove buttons */
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
            // Badge
            row.addView(TextView(requireContext()).apply {
                text = "${idx + 1}"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                val size = dpToPx(22f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(0, 0, dpToPx(10f).toInt(), 0)
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33000000"))
                    cornerRadius = dpToPx(11f)
                }
                setTextColor(Color.parseColor("#99000000"))
            })
            // Name
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