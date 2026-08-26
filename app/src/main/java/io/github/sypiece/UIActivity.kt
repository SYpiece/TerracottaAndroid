package io.github.sypiece

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.sypiece.TerracottaState.Profile.Kind.*
import io.github.sypiece.ui.theme.TerracottaAndroidTheme
import kotlin.random.Random


class UIActivity : ComponentActivity() {
    companion object {
        const val REQUEST_POST_NOTIFICATIONS = 2183
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TerracottaAndroidTheme {
                Scaffold { innerPadding ->
                    App(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this@UIActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@UIActivity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
            }
        }

        val intent = VpnService.prepare(this)
        if (intent != null) {
            val startVpnActivity = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (it.resultCode != RESULT_OK) {
                    Toast.makeText(this, "拒绝启用VPN可能会导致应用运行不正常", Toast.LENGTH_LONG).show()
                }
            }
            startVpnActivity.launch(intent)
        }
        startForegroundService(Intent(this, TerracottaService::class.java))
    }

    @Composable
    fun Home(navController: NavController) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = {
                    navController.navigate(Destination.HOST)
                },
            ) {
                Text("我想当房主")
            }
            OutlinedButton(
                onClick = {
                    navController.navigate(Destination.GUEST)
                },
            ) {
                Text("我想当房客")
            }
            OutlinedButton(
                onClick = {
                    navController.navigate(Destination.ABOUT)
                }
            ) {
                Text("关于")
            }
        }
    }

    @Composable
    fun Host(navController: NavController) {
        var room by remember { mutableStateOf("") }
        var player by remember { mutableStateOf("") }

        var state by remember { mutableStateOf(Terracotta.getState()) }

        DisposableEffect(Unit) {
            val stateListener = Terracotta.StateListener { _, newState -> state = newState }
            Terracotta.addStateListener(stateListener)
            onDispose {
                Terracotta.removeStateListener(stateListener)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when(val currentState = state) {
                is TerracottaState.Waiting -> {
                    OutlinedTextField(
                        value = room,
                        singleLine = true,
                        onValueChange = { room = it },
                        label = { Text("房间ID（可空）") },
                    )
                    OutlinedTextField(
                        value = player,
                        placeholder = { Text("Terracotta Anonymous Host") },
                        singleLine = true,
                        onValueChange = { player = it },
                        label = { Text("房主ID（可空）") },
                    )
                    OutlinedButton(
                        onClick = {
                            val roomID = room.ifEmpty { null }
                            val playerID = player.ifEmpty { "Terracotta Anonymous Host${Random.nextInt(100, 1000)}" }
                            Terracotta.setScanning(roomID, playerID)
                        }
                    ) {
                        Text("创建房间")
                    }
                }

                is TerracottaState.Host.Scanning -> {
                    Text("扫描局域网中...")
                    OutlinedButton(
                        onClick = {
                            Terracotta.setWaiting()
                        }
                    ) {
                        Text("取消")
                    }
                }

                is TerracottaState.Host.Starting -> {
                    Text("正在创建房间：${currentState.room}")
                    OutlinedButton(
                        onClick = {
                            Terracotta.setWaiting()
                        }
                    ) {
                        Text("取消")
                    }
                }

                is TerracottaState.Host.OK -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("房间创建成功：")
                        CopyableText(currentState.room)
                    }
                    PlayerListCard(currentState.profiles)
                    OutlinedButton(
                        onClick = {
                            Terracotta.setWaiting()
                        }
                    ) {
                        Text("关闭房间")
                    }
                }

                is TerracottaState.Exception -> {
                    Text("遇到错误")
                    CopyableText(state.toString())
                }

                else -> {}
            }

            OutlinedButton(
                onClick = {
                    navController.navigateUp()
                    Terracotta.setWaiting()
                }
            ) {
                Text("退出")
            }
        }
    }

    @Composable
    fun Guest(navController: NavController) {
        var room by remember { mutableStateOf("") }
        var player by remember { mutableStateOf("") }

        var state by remember { mutableStateOf(Terracotta.getState()) }

        DisposableEffect(Unit) {
            val stateListener = Terracotta.StateListener { _, newState -> state = newState }
            Terracotta.addStateListener(stateListener)
            onDispose {
                Terracotta.removeStateListener(stateListener)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when(val currentState = state) {
                is TerracottaState.Waiting -> {
                    OutlinedTextField(
                        value = room,
                        singleLine = true,
                        onValueChange = { room = it },
                        label = {
                            Text("房间ID")
                        },
                    )
                    OutlinedTextField(
                        value = player,
                        placeholder = { Text("Terracotta Anonymous Player") },
                        singleLine = true,
                        onValueChange = { str ->
                            player = str
                        },
                        label = {
                            Text("玩家ID（可空）")
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            val playerID = player.ifEmpty { "Terracotta Anonymous Player${Random.nextInt(100, 1000)}" }
                            if (!Terracotta.setGuesting(room, playerID)) {
                                Toast.makeText(this@UIActivity, "加入房间失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("加入房间")
                    }
                }

                is TerracottaState.Guest.Connecting -> {
                    Text("正在连接：${currentState.room}")
                    OutlinedButton(
                        onClick = {
                            Terracotta.setWaiting()
                        }
                    ) {
                        Text("取消")
                    }
                }

                is TerracottaState.Guest.Starting -> {
                    Text("正在加入：${currentState.room}")
                    OutlinedButton(
                        onClick = {
                            Terracotta.setWaiting()
                        }
                    ) {
                        Text("取消")
                    }
                }

                is TerracottaState.Guest.OK -> {
                    Text("加入房间成功")
                    PlayerListCard(currentState.profiles)
                    OutlinedButton(
                        onClick = {
                            Terracotta.setWaiting()
                        }
                    ) {
                        Text("退出房间")
                    }
                }

                is TerracottaState.Exception -> {
                    Text("遇到错误")
                    CopyableText(state.toString())
                }

                else -> {}
            }
            OutlinedButton(
                onClick = {
                    navController.navigateUp()
                    Terracotta.setWaiting()
                }
            ) {
                Text("退出")
            }
        }
    }

    @Composable
    fun About(navController: NavController) {
        var showLogDialog by remember { mutableStateOf(false) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val metadata = Terracotta.metadata
            Text("陶瓦核心版本：${metadata.terracottaVersion}")
            Text("EasyTier版本：${metadata.easyTierVersion}")
            OutlinedButton(
                onClick = {
                    showLogDialog = true
                }
            ) {
                Text("查看日志")
            }
            OutlinedButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text("返回")
            }
        }

        if (showLogDialog) {
            AlertDialog(
                onDismissRequest = { showLogDialog = false },
                title = { Text("应用日志") },
                text = {
                    val reader = Terracotta.collectLogs()
                    val log = try {
                        reader.use {
                            it.readText()
                        }
                    } catch (e: Throwable) {
                        "读取日志失败：${e.message}"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.verticalScroll(rememberScrollState(Int.MAX_VALUE))
                    ) {
                        CopyableText(log)
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            showLogDialog = false
                        }
                    ) {
                        Text("关闭")
                    }
                }
            )
        }
    }

    object Destination {
        const val HOME = "home"
        const val HOST = "host"
        const val GUEST = "guest"
        const val ABOUT = "about"
    }

    @Composable
    fun App(modifier: Modifier = Modifier) {
        val navController = rememberNavController()

        var lastBackPressTime by remember { mutableLongStateOf(0L) }

        @Composable
        fun BoxWrapper(content: @Composable (BoxScope.() -> Unit)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }

        fun backPressProtector() {
            if (Terracotta.getState() is TerracottaState.Host || Terracotta.getState() is TerracottaState.Guest) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    navController.navigateUp()
                    Terracotta.setWaiting()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(this@UIActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            } else {
                navController.navigateUp()
                Terracotta.setWaiting()
            }
        }

        NavHost(
            navController = navController,
            startDestination = Destination.HOME,
            modifier = modifier
        ) {
            composable(Destination.HOME) {
                BoxWrapper { Home(navController) }
            }
            composable(Destination.HOST) {
                BoxWrapper { Host(navController) }
                BackHandler { backPressProtector() }
            }
            composable(Destination.GUEST) {
                BoxWrapper { Guest(navController) }
                BackHandler { backPressProtector() }
            }
            composable(Destination.ABOUT) {
                BoxWrapper { About(navController) }
            }
        }

        when(Terracotta.getState()) {
            is TerracottaState.Host -> navController.navigate(Destination.HOST)
            is TerracottaState.Guest -> navController.navigate(Destination.GUEST)
            else -> Terracotta.setWaiting()
        }
    }

    @Composable
    fun CopyableText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        autoSize: TextAutoSize? = null,
        fontSize: TextUnit = TextUnit.Unspecified,
        fontStyle: FontStyle? = null,
        fontWeight: FontWeight? = null,
        fontFamily: FontFamily? = null,
        letterSpacing: TextUnit = TextUnit.Unspecified,
        textDecoration: TextDecoration? = null,
        textAlign: TextAlign? = null,
        lineHeight: TextUnit = TextUnit.Unspecified,
        overflow: TextOverflow = TextOverflow.Clip,
        softWrap: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
        minLines: Int = 1,
        onTextLayout: ((TextLayoutResult) -> Unit)? = null,
        style: TextStyle = LocalTextStyle.current
    ) {
        Text(
            text,
            modifier.clickable {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Copyable Text", text))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
            color,
            autoSize,
            fontSize,
            fontStyle,
            fontWeight,
            fontFamily,
            letterSpacing,
            textDecoration,
            textAlign,
            lineHeight,
            overflow,
            softWrap,
            maxLines,
            minLines,
            onTextLayout,
            style
        )
    }

    @Composable
    fun PlayerListCard(profiles: List<TerracottaState.Profile>) {
        OutlinedCard(
            modifier = Modifier
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(4.dp)
                    .width(IntrinsicSize.Max)
            ) {
                Text("玩家列表")
                profiles.forEach {
                    PlayerCard(it)
                }
            }
        }
    }

    @Composable
    fun PlayerCard(profile: TerracottaState.Profile) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(profile.name)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        fontSize = 12.sp,
                        text = when(profile.kind) {
                            HOST -> "房主"
                            GUEST -> "房客"
                            LOCAL -> "你"
                        }
                    )
                }
                Text(
                    text = profile.vendor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }

    @Composable
    @Preview
    fun PlayerListCardPreview() {
        PlayerListCard(listOf(
            TerracottaState.Profile(
                GUEST,
                "",
                "dfasnjk",
                "dafsjlbjkawf"
            ),
            TerracottaState.Profile(
                LOCAL,
                "",
                "asdgbre",
                "g3qbteqangra"
            ),
            TerracottaState.Profile(
                HOST,
                "",
                "rgaerb35q5",
                "l689liyfm"
            ),
        ))
    }
}
