package com.mpai.whaleupgamesapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.UserConfig
import com.whaleup.gameshub.launcher.BiomeSdkProps
import com.whaleup.gameshub.launcher.GamesHubLauncher
import com.mpai.whaleupgamesapp.ui.theme.WhaleupGamesTheme
import kotlin.random.Random

const val PROD_API_BASE_URL = "https://whaleup-platform-prod.whaleupco.in"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    var showSdk by rememberSaveable { mutableStateOf(false) }
    var lastSdkMessage by remember { mutableStateOf("") }
    var sdkMessageCount by remember { mutableStateOf(0) }
    val demoUserId = rememberDemoUserId()

    val userConfig = remember(demoUserId) {
        UserConfig(
            userId = demoUserId,
            apiBaseUrl = PROD_API_BASE_URL,
            timezone = "Asia/Kolkata",
            authToken = "your_auth_token",
            userAgent = "X-User-Agent: Mozilla/5.0 (Linux; Android 10; ONEPLUS A6010 Build/QKQ1.190716.003) FKUA/Retail/2291126/Android/Mobile (OnePlus/ONEPLUS A6010/dde41b1e061443d95c058e38891c396a)",
            name = "WhaleupFan" + demoUserId.replace("whaleupId", ""),
            avatar = "https://cdn/avatar.png",
            sessionId = "abc-123-c9c",
            allowedDomains = listOf("https://*.dev/", "https://whaleupco.in/games/*")
        )
    }

    val gamesProps = remember(userConfig) {
        BiomeSdkProps(
            userConfig = userConfig,
            onMessage = { msg ->
                Log.d("HostApp", "Received Message: $msg")
                sdkMessageCount += 1
                lastSdkMessage = "${msg.type.uppercase()}: ${msg.action}\n${msg.data ?: ""}"
            },
            onWhaleupSDKEvent = { event ->
                Log.d("HostApp", "Received Event: $event")
            },
            onWhaleupSDKError = { error ->
                Log.e("HostApp", "Received Error: $error")
            },
            onClose = {
                showSdk = false
            }
        )
    }

    GamesHubSession.props = gamesProps

    if (showSdk) {
        BackHandler {
            showSdk = false
        }
        GamesHubScreen(
            props = gamesProps,
            modifier = Modifier.fillMaxSize(),
            visible = true
        )
    } else {
        HomeScreen(
            userConfig = userConfig,
            lastSdkMessage = lastSdkMessage,
            sdkMessageCount = sdkMessageCount,
            onOpenSdk = { showSdk = true }
        )
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
            append("whaleupId")
            append(Random.nextInt(10000, 100000))
        }.also { generatedUserId ->
            prefs.edit().putString("user_id", generatedUserId).apply()
        }
    }
}

@Composable
fun HomeScreen(
    userConfig: UserConfig,
    lastSdkMessage: String,
    sdkMessageCount: Int,
    onOpenSdk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_example_app),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_whaleup_coin_large),
                contentDescription = null,
                modifier = Modifier
                    .size(110.dp)
                    .padding(top = 20.dp)
            )

            Image(
                painter = painterResource(id = R.drawable.img_screen_title),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(3.2f)
                    .padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .shadow(8.dp, RoundedCornerShape(30.dp))
                    .clickable(onClick = onOpenSdk)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.btn_open_sdk),
                    contentDescription = "Open SDK",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4.2f)
                )
            }

            Text(
                text = "Touch the button above to launch the SDK interface",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF2A5A8A),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFDFDFDFD)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEBF8FD))
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A6FD6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Current Participant Config",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A4C8C),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFD0E4F5))

                    ConfigRowItem(
                        icon = Icons.Default.Person,
                        label = "USER ID",
                        value = userConfig.userId
                    )

                    HorizontalDivider(color = Color(0xFFD0E4F5))

                    ConfigRowItem(
                        icon = Icons.Default.AccountBox,
                        label = "DISPLAY NAME",
                        value = userConfig.name.orEmpty()
                    )

                    HorizontalDivider(color = Color(0xFFD0E4F5))

                    ConfigRowItem(
                        icon = Icons.Default.Home,
                        label = "API URL",
                        value = userConfig.apiBaseUrl.orEmpty()
                    )
                }
            }

            if (lastSdkMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xF2FFF3E0)
                ) {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Text(
                            text = "Last SDK Message ($sdkMessageCount total):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = lastSdkMessage,
                            fontSize = 12.sp,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ConfigRowItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFDFF0FB)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF5096D6),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7A9CBB),
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A2F4A)
            )
        }
    }
}
