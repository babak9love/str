package dev.cluvex.strata.feature.plugin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.strata.R
import dev.cluvex.strata.core.data.SettingsRepository
import dev.cluvex.strata.core.plugin.PluginInfo
import dev.cluvex.strata.core.plugin.PluginPins
import dev.cluvex.strata.core.plugin.PluginProtocol
import dev.cluvex.strata.core.plugin.PluginTrust
import dev.cluvex.strata.core.plugin.Plugins
import dev.cluvex.strata.core.plugin.TextApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Installed(val plugins: List<PluginInfo>, val textApps: List<TextApp>)

/** Installed file plugins (separate apps speaking Strata's plugin protocol), their trust state, and how to use them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val pins by repo.pluginPins.collectAsStateWithLifecycle(PluginPins.EMPTY)

    // Plugins are installed and removed outside Strata: look again whenever the screen comes back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val installed by produceState<Installed?>(null, refresh) {
        value = withContext(Dispatchers.IO) { Installed(Plugins.discover(context), Plugins.textApps(context)) }
    }

    Scaffold(
        modifier = Modifier.testTag("plugins-screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugin_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back)) } },
            )
        },
    ) { padding ->
        val found = installed
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Section(stringResource(R.string.plugin_files_title), stringResource(R.string.plugin_files_body)) }

            if (found != null) {
                if (found.plugins.isEmpty()) {
                    item { Text(stringResource(R.string.plugin_none_installed), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("plugins-none")) }
                }
                items(found.plugins, key = { it.packageName + "/" + it.activityName }) { p ->
                    PluginCard(p, pins) { scope.launch { repo.forgetPlugin(p.packageName) } }
                }
                val missing = pins.packages - found.plugins.map { it.packageName }.toSet()
                items(missing.sorted(), key = { "missing-$it" }) { pkg ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pkg, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.plugin_trusted_not_installed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { scope.launch { repo.forgetPlugin(pkg) } }) { Text(stringResource(R.string.plugin_forget)) }
                        }
                    }
                }

                item { Section(stringResource(R.string.plugin_text_title), stringResource(R.string.plugin_text_body)) }
                if (found.textApps.isEmpty()) {
                    item { Text(stringResource(R.string.plugin_no_text_apps), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(found.textApps, key = { "text-" + it.packageName + "/" + it.activityName }) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(a.label, style = MaterialTheme.typography.bodyLarge)
                                Text(a.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { Section(stringResource(R.string.plugin_writing_title), stringResource(R.string.plugin_writing_body, PluginProtocol.ACTION_FILE)) }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PluginCard(p: PluginInfo, pins: PluginPins, onForget: () -> Unit) {
    val trust = pins.trust(p)
    Card(Modifier.fillMaxWidth().testTag("plugin-card-${p.packageName}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    p.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Text(p.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                p.extensions?.sorted()?.joinToString(", ") { ".$it" } ?: stringResource(R.string.plugin_any_file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.api > PluginProtocol.API) {
                Text(stringResource(R.string.plugin_newer_api, p.api), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                when (trust) {
                    PluginTrust.NEW -> stringResource(R.string.plugin_status_new)
                    PluginTrust.TRUSTED -> stringResource(R.string.plugin_status_trusted)
                    PluginTrust.CHANGED -> stringResource(R.string.plugin_status_changed)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (trust == PluginTrust.CHANGED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Fingerprint(stringResource(R.string.plugin_signed_by), p.signer, "plugin-signer-${p.packageName}")
            if (trust != PluginTrust.NEW) {
                TextButton(onClick = onForget) { Text(stringResource(R.string.plugin_forget_trust)) }
            }
        }
    }
}
