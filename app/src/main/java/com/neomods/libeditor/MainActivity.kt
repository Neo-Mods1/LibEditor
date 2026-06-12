package com.neomods.libeditor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.neomods.libeditor.domain.ThemeMode
import com.neomods.libeditor.storage.SettingsManager
import com.neomods.libeditor.ui.navigation.LibEditorNavGraph
import com.neomods.libeditor.ui.theme.LibEditorTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun attachBaseContext(newBase: Context) {
        settingsManager = SettingsManager(newBase)
        val localeCode = settingsManager.getLocaleCodeSync()
        val locale = if (localeCode.isNotEmpty()) {
            Locale(localeCode)
        } else {
            Locale.getDefault()
        }
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsManager.themeMode.collectAsState()
            val dynamicColors by settingsManager.dynamicColors.collectAsState()

            LibEditorTheme(themeMode = themeMode, dynamicColor = dynamicColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LibEditorNavGraph()
                }
            }
        }
    }
}
