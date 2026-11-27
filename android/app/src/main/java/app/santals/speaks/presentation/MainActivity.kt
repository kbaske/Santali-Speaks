package app.santals.speaks.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.santals.speaks.presentation.theme.SantaliTheme
import app.santals.speaks.service.intent.IncomingTextHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val root: RootViewModel by viewModels()

    @Inject
    lateinit var incomingText: IncomingTextHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { root.settings.value == null }
        enableEdgeToEdge()

        handleIncoming(intent)

        setContent {
            val settings by root.settings.collectAsState()
            val current = settings ?: return@setContent
            SantaliTheme(mode = current.themeMode) {
                SantaliRoot(startOnboarding = !current.onboardingDone)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /**
     * Text from "Speak in Santali" (PROCESS_TEXT) or the share sheet (SEND). It is only loaded into
     * the editor; nothing is spoken until the user taps Speak.
     */
    private fun handleIncoming(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND ->
                if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
            else -> null
        }
        if (!text.isNullOrBlank()) incomingText.offer(text)
    }
}
