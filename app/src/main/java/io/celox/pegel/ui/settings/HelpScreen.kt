package io.celox.pegel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HearingDisabled
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.celox.pegel.R
import io.celox.pegel.ui.common.SectionCard

/** Why it may not work, and what to try — the limits are Android's, and saying so is the honest part. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val topics = listOf(
        Triple(R.string.help_how_title, R.string.help_how_text, Icons.Rounded.Lightbulb),
        Triple(R.string.help_nothing_title, R.string.help_nothing_text, Icons.Rounded.HearingDisabled),
        Triple(R.string.help_stuck_title, R.string.help_stuck_text, Icons.Rounded.LinkOff),
        Triple(R.string.help_players_title, R.string.help_players_text, Icons.Rounded.MusicNote),
        Triple(R.string.help_unsupported_title, R.string.help_unsupported_text, Icons.Rounded.Block),
        Triple(R.string.help_bluetooth_title, R.string.help_bluetooth_text, Icons.Rounded.Bluetooth),
        Triple(R.string.help_suggest_title, R.string.help_suggest_text, Icons.Rounded.AutoAwesome),
        Triple(R.string.help_samsung_title, R.string.help_samsung_text, Icons.Rounded.Tune),
        Triple(R.string.help_stops_title, R.string.help_stops_text, Icons.Rounded.BatteryAlert),
        Triple(R.string.help_distortion_title, R.string.help_distortion_text, Icons.Rounded.GraphicEq),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            topics.forEachIndexed { i, (title, text, icon) ->
                SectionCard(stringResource(title), icon, index = i) {
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
