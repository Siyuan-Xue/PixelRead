package com.codexue.pixelread

import android.content.Context
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

fun Context.readReaderThemeMode(): ReaderThemeMode {
    val savedThemeMode = getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
        .getString("themeMode", null)
    return enumValues<ReaderThemeMode>().firstOrNull { it.name == savedThemeMode } ?: ReaderThemeMode.DARK
}

fun ComponentActivity.applyPixelReadSystemBars(themeMode: ReaderThemeMode) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { themeMode == ReaderThemeMode.DARK },
        ),
        navigationBarStyle = SystemBarStyle.auto(
            lightScrim = readerThemeTokens(ReaderThemeMode.LIGHT).background.toInt(),
            darkScrim = readerThemeTokens(ReaderThemeMode.DARK).background.toInt(),
            detectDarkMode = { themeMode == ReaderThemeMode.DARK },
        ),
    )
}
