package io.github.sypiece

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.sypiece.ui.theme.TerracottaAndroidTheme
import net.burningtnt.terracotta.TerracottaAndroidAPI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val metadata = TerracottaAndroidAPI.initialize(this) {
            Log.i("TerracottaAndroidAPI", "TerracottaAndroidAPI Initialized")
        }
        setContent {
            TerracottaAndroidTheme {
                App(metadata)
            }
        }
        Log.i("TerracottaAndroidAPI", "TerracottaVersion: ${metadata.terracottaVersion} TerracottaCompileTime: ${metadata.terracottaCompileTime} EasyTierVersion: ${metadata.easyTierVersion}")
    }
}

@Composable
fun Home(navController: NavController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = {
                navController.navigate(HostDestination.route)
            },
        ) {
            Text("我想当房主")
        }
        OutlinedButton(
            onClick = {
                navController.navigate(GuestDestination.route)
            },
        ) {
            Text("我想当房客")
        }
        OutlinedButton(
            onClick = {
                navController.navigate(AboutDestination.route)
            }
        ) {
            Text("关于")
        }
    }
}

object HomeDestination {
    const val route = "home"
}

@Composable
fun Host(navController: NavController) {
    Column {
        OutlinedButton(
            onClick = {
                navController.navigateUp()
            }
        ) {
            Text("返回")
        }
    }
}

object HostDestination {
    const val route = "host"
}

@Composable
fun Guest(navController: NavController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            var room = ""
            OutlinedTextField(
                value = room,
                onValueChange = { str ->
                    room = str
                },
                label = {
                    Text("房间ID")
                },
            )
            OutlinedButton(
                onClick = {
                    if (!TerracottaAndroidAPI.setGuesting(room, null)) {
                    }
                }
            ) {
                Text("加入房间")
            }
        }
        OutlinedButton(
            onClick = {
                navController.navigateUp()
            }
        ) {
            Text("返回")
        }
    }
}


object GuestDestination {
    const val route = "guest"
}

@Composable
fun About(navController: NavController, metadata: TerracottaAndroidAPI.Metadata) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("陶瓦核心版本：${metadata.terracottaVersion}")
        Text("EasyTier版本：${metadata.easyTierVersion}")
        OutlinedButton(
            onClick = {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("提示") },
                    text = { Text("是否确认操作？") },
                    confirmButton = {
                        TextButton(onClick = { /* 确认逻辑 */ showDialog = false }) {
                            Text("确认")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("取消")
                        }
                    }
                )
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
}

object AboutDestination {
    const val route = "about"
}

@Composable
fun App(metadata: TerracottaAndroidAPI.Metadata) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeDestination.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(HomeDestination.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Home(navController)
            }
        }
        composable(HostDestination.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Host(navController)
            }
        }
        composable(GuestDestination.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Guest(navController)
            }
        }
        composable(AboutDestination.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                About(navController, metadata)
            }
        }
    }
}