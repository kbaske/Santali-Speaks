package app.santals.speaks.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.santals.speaks.R

private data class Page(@androidx.annotation.StringRes val title: Int, @androidx.annotation.StringRes val body: Int)

private val pages = listOf(
    Page(R.string.onboarding_title_1, R.string.onboarding_body_1),
    Page(R.string.onboarding_title_2, R.string.onboarding_body_2),
    Page(R.string.onboarding_title_3, R.string.onboarding_body_3),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val page = pages[index]
    val last = index == pages.lastIndex

    // Notifications are optional. They only carry the playback controls.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(page.title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(page.body),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "${index + 1} / ${pages.size}",
            modifier = Modifier.padding(top = 24.dp),
        )

        if (last && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(
                onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) { Text(stringResource(R.string.onboarding_allow_notifications)) }
        }

        Button(
            onClick = { if (last) onFinished() else index++ },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next))
        }
    }
}
