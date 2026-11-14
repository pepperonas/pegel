package io.celox.pegel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.celox.pegel.service.ServiceStarter
import io.celox.pegel.service.handleSessionIntent
import io.celox.pegel.ui.profiles.ProfilesScreen
import io.celox.pegel.ui.sound.SoundScreen
import io.celox.pegel.ui.theme.PegelTheme
import io.celox.pegel.ui.theme.ThemeMode
import io.celox.pegel.ui.theme.ThemeSettings

/** The three top-level tabs. */
enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    SOUND("sound", R.string.nav_sound, Icons.Rounded.Equalizer),
    PROFILES("profiles", R.string.nav_profiles, Icons.Rounded.LibraryMusic),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by ThemeSettings.mode(this).collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamic by ThemeSettings.dynamicColor(this).collectAsStateWithLifecycle(false)
            PegelTheme(mode = mode, dynamicColor = dynamic) { PegelRoot() }
        }
        handleSessionIntent(this, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A player's "Equalizer" button hands over its audio session.
        handleSessionIntent(this, intent)
    }

    override fun onStart() {
        super.onStart()
        // In the foreground a start is always allowed — this is what the "tap to activate"
        // notification leads to, and it revives the service if the system killed it.
        if (container.repository.settings.value.enabled) ServiceStarter.sync(this, enabled = true)
    }
}

@Composable
fun PegelRoot() {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    fun go(d: Destination) = nav.navigate(d.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    Scaffold(
        // Every screen brings its own top bar, which handles the status bar itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Sub-screens (help) hide the bar: it belongs to the three tabs only.
            if (current == null || Destination.entries.any { it.route == current }) {
                ShortNavigationBar {
                    Destination.entries.forEach { d ->
                        ShortNavigationBarItem(
                            selected = current == d.route,
                            onClick = { go(d) },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(stringResource(d.label)) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Destination.SOUND.route, modifier = Modifier.padding(padding)) {
            composable(Destination.SOUND.route) { SoundScreen(onOpenProfiles = { go(Destination.PROFILES) }) }
            composable(Destination.PROFILES.route) { ProfilesScreen() }
            composable(Destination.SETTINGS.route) { Placeholder(Destination.SETTINGS) }
        }
    }
}

@Composable
private fun Placeholder(d: Destination) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(d.label)) }
}
