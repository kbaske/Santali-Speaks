package app.santals.speaks.presentation.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.santals.speaks.BuildConfig
import app.santals.speaks.R
import app.santals.speaks.presentation.theme.OlChikiFont

private const val WEBSITE = "https://speaks.santals.in"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    val licenseText by produceState(initialValue = "") {
        value = runCatching {
            context.assets.open("licenses/NotoSansOlChiki-OFL.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.santali_name), style = MaterialTheme.typography.headlineMedium, fontFamily = OlChikiFont)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_speech_engine), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_verification), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_font), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.about_website)) }
            OutlinedButton(onClick = { showLicenses = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_open_source_licenses))
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.about_open_source_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(licenseText.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.dismiss)) }
            },
        )
    }
}
