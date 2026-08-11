package com.mpai.whaleupgamesapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.UserConfig
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.launcher.GamesHubLauncher
import com.mpai.whaleupgamesapp.ui.theme.WhaleupGamesTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Initialize the SDK once when the app opens, before any UI is shown.
        GamesHubSession.initialize(this)
        setContent {
            WhaleupGamesTheme {
                WhaleupGamesApp()
            }
        }
    }
}

@Composable
fun WhaleupGamesApp() {
    val context = LocalContext.current
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var isGameOpen by remember { mutableStateOf(false) }
    var sdkReloadKey by rememberSaveable { mutableStateOf(0) }
    val currentThemeState = rememberSaveable { mutableStateOf("dark") }
    val currentTheme = currentThemeState.value
    var selectedEnvironmentName by rememberSaveable {
        mutableStateOf(AppConnectionEnvironment.PRE_PROD.name)
    }
    val selectedEnvironment = AppConnectionEnvironment.valueOf(selectedEnvironmentName)
    val demoUserId = rememberDemoUserId()

    val userConfig = UserConfig(
        userId = demoUserId,
        apiBaseUrl = selectedEnvironment.apiBaseUrl,
        timezone = "Asia/Kolkata",
        authToken = "your_auth_token",
        name = "Whaleup Demo User",
        avatar = "https://api.dicebear.com/8.x/initials/png?seed=$demoUserId"
    )

    fun createProps() = BiomeSdkProps(
        theme = currentThemeState.value,
        updateTheme = { currentThemeState.value },
        userConfig = userConfig,
        isImageGenEnabled = true,
        onMessage = { msg ->
            Log.d("HostApp", "[${currentThemeState.value}] Received Message: $msg")
            if (msg.action == BiomeMessageAction.LAUNCH_GAME) {
                isGameOpen = true
            }
        },
        onBiomeEvent = { event ->
            Log.d("HostApp", "[${currentThemeState.value}] Received Event: $event")
            if (event.action == BiomeMessageAction.HUB_VIEWED) {
                isGameOpen = false
            }
        },
        onBiomeError = { error ->
            Log.e("HostApp", "[${currentThemeState.value}] Received Error: $error")
        },
        closeBiome = {
            isGameOpen = false
            currentDestination = AppDestinations.HOME
        }
    )

    val gamesProps = remember(currentTheme, selectedEnvironment, demoUserId) { createProps() }

    // Push updated props to the SDK whenever they change (e.g. environment switch).
    // SDK initialization itself is done once in Activity.onCreate — do NOT call it here.
    GamesHubSession.props = gamesProps

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            if (!isGameOpen) {
                AppDestinations.entries.forEach {
                    item(
                        icon = { Icon(it.icon, contentDescription = it.label) },
                        label = { Text(it.label) },
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // GamesHubScreen is always kept in the composition so the fragment
            // is never destroyed and recreated when switching tabs.
            // Visibility is toggled via Modifier instead of conditional composition.
            key(sdkReloadKey) {
                GamesHubScreen(
                    props = gamesProps,
                    modifier = if (currentDestination == AppDestinations.GAMES) {
                        Modifier.fillMaxSize()
                    } else {
                        // Keep fragment alive but invisible; avoids re-creation on tab switch.
                        Modifier
                            .fillMaxSize()
                            .then(Modifier) // identity — hidden via alpha-0 wrapper below
                    },
                    visible = currentDestination == AppDestinations.GAMES
                )
            }

            // Overlay the active non-Games screen on top when not on the Games tab.
            when (currentDestination) {
                AppDestinations.HOME -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    HomeScreen(
                        currentTheme = currentTheme,
                        selectedEnvironment = selectedEnvironment,
                        userConfig = userConfig,
                        onThemeSelected = { selectedTheme ->
                            currentThemeState.value = selectedTheme
                            GamesHubLauncher.updateTheme(selectedTheme)
                        },
                        onEnvironmentSelected = { environment ->
                            if (selectedEnvironment != environment) {
                                selectedEnvironmentName = environment.name
                                sdkReloadKey += 1
                                isGameOpen = false
                            }
                        }
                    )
                }
                AppDestinations.PROFILE -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    Greeting(name = "Profile")
                }
                AppDestinations.GAMES -> { /* GamesHubScreen rendered above */ }
            }
        }
    }
}

@Composable
fun GamesHubScreen(
    props: BiomeSdkProps,
    modifier: Modifier = Modifier.fillMaxSize(),
    visible: Boolean = true
) {
    val context = LocalContext.current
    val fragmentManager = (context as? AppCompatActivity)?.supportFragmentManager

    // Use AndroidView to embed the GamesHub Fragment inside Compose.
    // The factory block runs only ONCE — the fragment is never re-created on tab switches.
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                container.id = View.generateViewId()
                fragmentManager?.beginTransaction()
                    ?.replace(container.id, GamesHubLauncher.getFragment(ctx, props))
                    ?.commit()
                container.setOnApplyWindowInsetsListener { v, insets ->
                    v.onApplyWindowInsets(insets)
                }
                container.requestApplyInsets()
            }
        },
        update = { container ->
            // Show or hide the container without destroying the fragment.
            container.visibility = if (visible) View.VISIBLE else View.GONE
            container.requestApplyInsets()
        }
    )
}

@Composable
private fun rememberDemoUserId(): String {
    val context = LocalContext.current
    return remember {
        val prefs = context.getSharedPreferences("whaleup_demo_user", android.content.Context.MODE_PRIVATE)
        prefs.getString("user_id", null) ?: buildString {
            append("WhaleupUserId")
            append(Random.nextInt(10000, 100000))
        }.also { generatedUserId ->
            prefs.edit().putString("user_id", generatedUserId).apply()
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    GAMES("Games", Icons.Default.PlayArrow),
    PROFILE("Profile", Icons.Default.AccountBox),
}

enum class AppConnectionEnvironment(
    val label: String,
    val apiBaseUrl: String,
) {
    PRE_PROD("Pre-prod", "https://hikeapp-preprod.whaleupco.in/"),
    PROD("Prod", "https://hikeapp-prod.whaleupco.in/"),
}

@Composable
fun HomeScreen(
    currentTheme: String,
    selectedEnvironment: AppConnectionEnvironment,
    userConfig: UserConfig,
    onThemeSelected: (String) -> Unit,
    onEnvironmentSelected: (AppConnectionEnvironment) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        HeaderSection()
        ConnectionSection(
            selectedEnvironment = selectedEnvironment,
            onEnvironmentSelected = onEnvironmentSelected
        )
        UserSection(userConfig = userConfig)
        ThemeSection(currentTheme = currentTheme, onThemeSelected = onThemeSelected)
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Whaleup SDK Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text(
                text = "Built ${formatBuildDate(BuildConfig.BUILD_DATE)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionSection(
    selectedEnvironment: AppConnectionEnvironment,
    onEnvironmentSelected: (AppConnectionEnvironment) -> Unit
) {
    SectionSurface {
        SectionHeader(title = "Connection", value = "Reloads SDK")
        AppConnectionEnvironment.entries.forEach { environment ->
            ConnectionOption(
                environment = environment,
                selected = environment == selectedEnvironment,
                onSelected = { onEnvironmentSelected(environment) }
            )
        }
    }
}

@Composable
private fun ConnectionOption(
    environment: AppConnectionEnvironment,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelected),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, onClick = onSelected)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = environment.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selected) {
                        StatusPill(text = "Active")
                    }
                }
                Text(
                    text = environment.apiBaseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UserSection(userConfig: UserConfig) {
    SectionSurface {
        SectionHeader(title = "User", value = "Persistent until uninstall")
        InfoRow(label = "ID", value = userConfig.userId)
        InfoRow(label = "Name", value = userConfig.name.orEmpty())
        InfoRow(label = "Timezone", value = userConfig.timezone.orEmpty())
        InfoRow(label = "API", value = userConfig.apiBaseUrl.orEmpty())
    }
}

@Composable
private fun ThemeSection(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    SectionSurface {
        SectionHeader(title = "SDK Theme", value = currentTheme.replaceFirstChar { it.uppercase() })
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeChoice(
                label = "Dark",
                selected = currentTheme == "dark",
                onClick = { onThemeSelected("dark") },
                modifier = Modifier.weight(1f)
            )
            ThemeChoice(
                label = "Light",
                selected = currentTheme == "light",
                onClick = { onThemeSelected("light") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.32f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .padding(0.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF36B37E),
                    shape = CircleShape,
                    content = {}
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatBuildDate(rawValue: String): String {
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
        Instant.parse(rawValue)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }.getOrElse { rawValue }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
