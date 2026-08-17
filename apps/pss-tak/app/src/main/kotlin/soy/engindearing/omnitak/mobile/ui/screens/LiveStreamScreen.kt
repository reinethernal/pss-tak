package soy.engindearing.omnitak.mobile.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.domain.LiveStreamService
import soy.engindearing.omnitak.mobile.domain.LiveStreamState
import soy.engindearing.omnitak.mobile.i18n.Loc
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent
import soy.engindearing.omnitak.mobile.ui.theme.TacticalBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamScreen(onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTAKApp
    val status by LiveStreamState.status.collectAsState()
    val error by LiveStreamState.error.collectAsState()
    val bitrate by LiveStreamState.bitrateKbps.collectAsState()
    val url by LiveStreamState.publishUrl.collectAsState()
    val active by app.serverManager.activeServer.collectAsState()
    val allServers by app.serverManager.servers.collectAsState()
    val server = active ?: allServers.firstOrNull { it.enabled } ?: allServers.firstOrNull()
    var password by remember(server?.id) { mutableStateOf(server?.password.orEmpty()) }
    var bound by remember { mutableStateOf<LiveStreamService?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    DisposableEffect(Unit) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                bound = (service as? LiveStreamService.LocalBinder)?.service()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                bound = null
            }
        }
        context.bindService(Intent(context, LiveStreamService::class.java), conn, Context.BIND_AUTO_CREATE)
        onDispose {
            bound?.detachPreview()
            runCatching { context.unbindService(conn) }
            if (LiveStreamState.status.value != LiveStreamState.Status.Live &&
                LiveStreamState.status.value != LiveStreamState.Status.Starting
            ) {
                LiveStreamService.stop(context)
            }
        }
    }

    fun hasMediaPerms(): Boolean {
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    Scaffold(
        containerColor = TacticalBackground,
        topBar = {
            TopAppBar(
                title = { Text(Loc.t("stream.title"), color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TacticalAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TacticalBackground),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).also { host ->
                            bound?.attachPreview(host)
                        }
                    },
                    update = { host -> bound?.attachPreview(host) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val host = server?.host.orEmpty()
            val user = server?.username.orEmpty()
            Text(
                if (host.isBlank()) Loc.t("stream.noServer")
                else Loc.t("stream.target", host, user.ifBlank { "live" }),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (status == LiveStreamState.Status.Live) {
                Text(
                    Loc.t("stream.live", bitrate.toString(), url.orEmpty()),
                    color = Color(0xFF3DD68C),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let {
                Text(it, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall)
            }

            if (password.isEmpty() || server?.password.isNullOrEmpty()) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(Loc.t("stream.password")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (!hasMediaPerms()) {
                            permLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                            )
                            return@Button
                        }
                        val svc = bound ?: return@Button
                        if (status == LiveStreamState.Status.Live || status == LiveStreamState.Status.Starting) {
                            svc.stopPublish()
                        } else {
                            LiveStreamService.start(context)
                            val path = user.ifBlank { "live" }
                            svc.startPublish(host, path, user, password, 8554)
                        }
                    },
                    enabled = host.isNotBlank() && bound != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status == LiveStreamState.Status.Live) Color(0xFFFF3B30) else TacticalAccent,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (status == LiveStreamState.Status.Live) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        contentDescription = null,
                    )
                    Text(
                        "  " + if (status == LiveStreamState.Status.Live || status == LiveStreamState.Status.Starting) {
                            Loc.t("stream.stop")
                        } else {
                            Loc.t("stream.start")
                        },
                    )
                }
                IconButton(onClick = { bound?.switchCamera() }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = null, tint = TacticalAccent)
                }
            }
        }
    }
}
