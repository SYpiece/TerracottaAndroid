package io.github.sypiece

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.sypiece.ui.theme.TerracottaAndroidTheme
import net.burningtnt.terracotta.TerracottaAndroidAPI
import kotlin.concurrent.thread

class UIActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, TerracottaService::class.java)
        startForegroundService(intent)
        bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {}

            override fun onServiceDisconnected(name: ComponentName?) {}
        }, BIND_AUTO_CREATE)

        enableEdgeToEdge()
        setContent {
            TerracottaAndroidTheme {
                App()
            }
        }
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
        Column {
            OutlinedButton(
                onClick = {
                    Terracotta.setScanning()
                }
            ) {
                Text("创建房间")
            }
            OutlinedButton(
                onClick = {
                    Terracotta.setWaiting()
                    navController.navigateUp()
                }
            ) {
                Text("退出")
            }
        }
    }

    @Composable
    fun Guest(navController: NavController) {
        var room by rememberSaveable { mutableStateOf("") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = room,
                singleLine = true,
                onValueChange = { str ->
                    room = str
                },
                label = {
                    Text("房间ID")
                },
            )
            OutlinedButton(
                onClick = {
                    Terracotta.setWaiting()
                    if (!Terracotta.setGuesting(room)) {
                        Toast.makeText(this@UIActivity, "加入房间失败", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("加入房间")
            }
            OutlinedButton(
                onClick = {
                    Terracotta.setWaiting()
                    navController.navigateUp()
                }
            ) {
                Text("退出")
            }
        }
    }

    @Composable
    fun About(navController: NavController) {
        var showLogDialog by rememberSaveable { mutableStateOf(false) }

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
                Text("退出")
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
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = log,
                            Modifier.clickable {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Terracotta Log", log))
                                Toast.makeText(this@UIActivity, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        )
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
    fun App() {
        val navController = rememberNavController()

        @Composable
        fun BoxWrapper(content: @Composable (BoxScope.() -> Unit)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }

        NavHost(
            navController = navController,
            startDestination = Destination.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Destination.HOME) {
                BoxWrapper { Home(navController) }
            }
            composable(Destination.HOST) {
                BoxWrapper { Host(navController) }
            }
            composable(Destination.GUEST) {
                BoxWrapper { Guest(navController) }
            }
            composable(Destination.ABOUT) {
                BoxWrapper { About(navController) }
            }
        }

        when(Terracotta.getState()) {
            is TerracottaState.Host -> navController.navigate(Destination.HOST)
            is TerracottaState.Guest -> navController.navigate(Destination.GUEST)
            else -> {}
        }
    }
}

