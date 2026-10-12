package com.ihvpn.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// İhvpn marka renkleri — koyu lacivert/siyah zemin, turkuaz vurgu
val IhvpnBackground = Color(0xFF0B0F17)
val IhvpnSurface = Color(0xFF121826)
val IhvpnSurfaceVariant = Color(0xFF1B2332)
val IhvpnPrimary = Color(0xFF22D3C7)      // turkuaz vurgu
val IhvpnSecondary = Color(0xFF3B82F6)    // mavi vurgu
val IhvpnError = Color(0xFFEF4444)
val IhvpnOnBackground = Color(0xFFE6EAF2)
val IhvpnOnSurfaceMuted = Color(0xFF8B94A7)

private val IhvpnDarkColorScheme = darkColorScheme(
    primary = IhvpnPrimary,
    secondary = IhvpnSecondary,
    background = IhvpnBackground,
    surface = IhvpnSurface,
    surfaceVariant = IhvpnSurfaceVariant,
    error = IhvpnError,
    onPrimary = Color(0xFF00201C),
    onBackground = IhvpnOnBackground,
    onSurface = IhvpnOnBackground,
    onSurfaceVariant = IhvpnOnSurfaceMuted
)

/**
 * Uygulama HER ZAMAN koyu temada çalışır.
 * Sistem açık temada olsa bile bu değişmez (spesifikasyon gereği).
 */
@Composable
fun IhvpnTheme(content: @Composable () -> Unit) {
    val colorScheme = IhvpnDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = IhvpnBackground.toArgb()
            window.navigationBarColor = IhvpnBackground.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            // Koyu zemin üzerinde açık (light) ikonlar
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IhvpnTypography,
        content = content
    )
}

// isSystemInDarkTheme() kasıtlı olarak kullanılmıyor — tema her koşulda sabit koyu.
