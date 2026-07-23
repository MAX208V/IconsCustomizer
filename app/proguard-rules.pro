#
# IconsCustomizer ProGuard/R8 Rules
# ==================================
# This module hooks into MIUI Home launcher.
# R8 must keep everything accessible to the system and LSPosed framework.
#

# ---- LSPosed entry point (instantiated by framework via reflection) ----
-keep class com.ukm.app.iconscustomizer.MainHook { *; }
-keep class com.ukm.app.iconscustomizer.MainHook$Companion { *; }

# ---- Application & Activities (declared in AndroidManifest.xml) ----
-keep class com.ukm.app.iconscustomizer.App { *; }
-keep class com.ukm.app.iconscustomizer.MainActivity { *; }
-keep class com.ukm.app.iconscustomizer.ColorPickerActivity { *; }
-keep class com.ukm.app.iconscustomizer.IconPickerActivity { *; }

# ---- Fragments (instantiated by AndroidX / FragmentManager) ----
-keep class com.ukm.app.iconscustomizer.SettingsFragment { *; }
-keep class com.ukm.app.iconscustomizer.AllAppsFragment { *; }

# ---- Module logic / helpers (called from MainHook / Activities / Fragments) ----
-keep class com.ukm.app.iconscustomizer.IconPackHelper { *; }
-keep class com.ukm.app.iconscustomizer.IconPackHelper$FallbackOverlayInfo { *; }
-keep class com.ukm.app.iconscustomizer.XposedHelpers { *; }
-keep class com.ukm.app.iconscustomizer.UIHelpers { *; }

# ---- Kotlin metadata (required for some library features) ----
-keep class kotlin.Metadata { *; }

# ---- Gson / Kotlin serialization (if used) ----

# ---- Material Components / DynamicColors (consumer POM handles its own rules) ----

# ---- Parcelable (for any custom Parcelable types) ----

# ---- XML / Resources (accessed by resource identifier) ----