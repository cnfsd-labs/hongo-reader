package app.hongo.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hongo.reader.ui.HongoApp
import app.hongo.reader.ui.theme.HongoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as HongoApplication).container
        val appearance = container.appearanceSettings
        // Once per app start (not on rotation): new books from catalogues with automatic import,
        // and new versions of installed dictionaries (offered in Dictionaries, not installed).
        if (savedInstanceState == null) {
            container.opdsAutoImport.start()
            container.dictionaryUpdateChecker.start()
        }
        setContent {
            val accent by appearance.accent.collectAsStateWithLifecycle()
            HongoTheme(accent = accent) {
                HongoApp()
            }
        }
    }
}
