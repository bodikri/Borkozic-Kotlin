package com.borkozic.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ============================================================
// BorkozicTheme — shared Compose theme with per-list color override
// ============================================================

private val AppOrange = Color(0xFFFF7240)
private val AppLightBrown = Color(0xFFD7CCC8)

data class ThemeColor(
    val name: String,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val isLight: Boolean = false
)

val ThemePresets = listOf(
    ThemeColor("Brown",  Color(0xFF5D4037), Color(0xFF4E342E), Color(0xFF5D4037)),
    ThemeColor("Green",  Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF388E3C)),
    ThemeColor("Blue",   Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF1976D2)),
    ThemeColor("Purple", Color(0xFF6A1B9A), Color(0xFF4A148C), Color(0xFF7B1FA2)),
    ThemeColor("Red",    Color(0xFFC62828), Color(0xFFB71C1C), Color(0xFFD32F2F)),
    ThemeColor("Teal",   Color(0xFF00695C), Color(0xFF004D40), Color(0xFF00796B)),
    ThemeColor("Orange", Color(0xFFE65100), Color(0xFFBF360C), Color(0xFFEF6C00)),
    ThemeColor("Navy",   Color(0xFF1A237E), Color(0xFF0D1457), Color(0xFF283593)),
    ThemeColor("Pink",   Color(0xFFAD1457), Color(0xFF880E4F), Color(0xFFC2185B)),
    ThemeColor("Gray",   Color(0xFF37474F), Color(0xFF263238), Color(0xFF455A64)),
    ThemeColor("Cyan",   Color(0xFF00838F), Color(0xFF006064), Color(0xFF0097A7)),
    ThemeColor("Amber",  Color(0xFFFF8F00), Color(0xFFE65100), Color(0xFFFFA000)),
    ThemeColor("Black",  Color(0xFF121212), Color(0xFF0D0D0D), Color(0xFF1E1E1E)),
    ThemeColor("White",  Color.White,        Color.White,        Color(0xFFF5F5F5), isLight = true),
)

fun getListThemeColor(context: Context, listType: String): ThemeColor {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val colorIdx = prefs.getInt("pref_theme_$listType", 0)
    return ThemePresets[colorIdx.coerceIn(0, ThemePresets.lastIndex)]
}

fun getListThemeIndex(context: Context, listType: String): Int {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getInt("pref_theme_$listType", 0)
}

fun setListThemeColor(context: Context, listType: String, colorIdx: Int) {
    PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putInt("pref_theme_$listType", colorIdx)
        .apply()
}

fun buildColorSchemeForTheme(themeColor: ThemeColor): ColorScheme {
    return if (themeColor.isLight) {
        lightColorScheme(
            primary = Color.Black, onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E0E0), onPrimaryContainer = Color.Black,
            secondary = Color(0xFF424242), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEEEEEE), onSecondaryContainer = Color.Black,
            tertiary = Color(0xFF616161), onTertiary = Color.White,
            background = themeColor.darkBackground, onBackground = Color.Black,
            surface = themeColor.darkSurface, onSurface = Color.Black,
            surfaceVariant = themeColor.darkSurfaceVariant, onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
            error = Color(0xFFB3261E), onError = Color.White,
        )
    } else {
        darkColorScheme(
            primary = AppOrange, onPrimary = Color.White,
            primaryContainer = themeColor.darkBackground, onPrimaryContainer = AppLightBrown,
            secondary = AppOrange, onSecondary = Color.White,
            secondaryContainer = Color(0xFF3E2723), onSecondaryContainer = AppLightBrown,
            tertiary = AppOrange, onTertiary = Color.White,
            background = themeColor.darkBackground, onBackground = Color.White,
            surface = themeColor.darkSurface, onSurface = Color.White,
            surfaceVariant = themeColor.darkSurfaceVariant, onSurfaceVariant = AppLightBrown,
            outline = AppLightBrown.copy(alpha = 0.5f), outlineVariant = AppLightBrown.copy(alpha = 0.3f),
            error = Color(0xFFFF5252), onError = Color.White,
        )
    }
}

private val BorkozicDarkColors = buildColorSchemeForTheme(ThemePresets[0])
private val BorkozicLightColors = lightColorScheme(
    primary = Color(0xFF5D4037), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7CCC8), onPrimaryContainer = Color(0xFF5D4037),
    secondary = AppOrange, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF5D4037),
    tertiary = Color(0xFF795548), onTertiary = Color.White,
    background = Color.White, onBackground = Color(0xFF1C1B1F),
    surface = Color.White, onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E), onError = Color.White,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

fun getThemeMode(context: Context): ThemeMode {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    return when (prefs.getString("pref_theme_mode", "dark")) {
        "light" -> ThemeMode.LIGHT
        "system" -> ThemeMode.SYSTEM
        else -> ThemeMode.DARK
    }
}

@Composable
fun BorkozicTheme(
    context: Context = LocalContext.current,
    listType: String = "",
    themeVersion: Int = 0,
    content: @Composable () -> Unit
) {
    val themeMode = remember { getThemeMode(context) }
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme: ColorScheme = if (listType.isNotEmpty()) {
        val themeColor = remember(themeVersion, listType) { getListThemeColor(context, listType) }
        buildColorSchemeForTheme(themeColor)
    } else if (useDarkTheme) {
        BorkozicDarkColors
    } else {
        BorkozicLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
