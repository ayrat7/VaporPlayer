package com.vapor.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    private fun audioPerm() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPerm() =
        ContextCompat.checkSelfPermission(this, audioPerm()) == PackageManager.PERMISSION_GRANTED

    private fun perms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm(), Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(audioPerm())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var granted by remember { mutableStateOf(hasPerm()) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                granted = hasPerm()
                if (granted) vm.load()
            }
            LaunchedEffect(Unit) { if (granted) vm.load() else launcher.launch(perms()) }
            VaporTheme {
                if (granted) VaporScreen(vm) else PermissionScreen { launcher.launch(perms()) }
            }
        }
    }
}
