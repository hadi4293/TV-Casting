package com.hadii.tvcasing.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hadii.tvcasing.streaming.StreamingService
import com.hadii.tvcasing.streaming.StreamingState
import com.hadii.tvcasing.streaming.StreamingViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CastScreen(vm: StreamingViewModel = viewModel()) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    var url by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current

    val pulse by animateFloatAsState(
        targetValue = if (state is StreamingState.Connected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1A2E), Color(0xFF05050A)),
                )
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(Color(0xFF5AC8FA).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFF5AC8FA))
                }
                Column {
                    Text("TV Casting", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (state) {
                            is StreamingState.Connected -> "Bağlı · HTTPS"
                            is StreamingState.Connecting -> "TV aranıyor…"
                            is StreamingState.Playing -> "Yayında"
                            is StreamingState.Error -> "Hata"
                            else -> "Bağlantı yok"
                        },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                ConnectionDot(state)
            }

            Spacer(Modifier.height(8.dp))

            // URL input card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Video linki", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…", color = Color.White.copy(alpha = 0.35f)) },
                        leadingIcon = { Icon(Icons.Default.Link, null, tint = Color(0xFF5AC8FA)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF5AC8FA),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            cursorColor = Color(0xFF5AC8FA),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                focus.clearFocus()
                                val intent = Intent(ctx, StreamingService::class.java).apply {
                                    action = StreamingService.ACTION_START
                                    putExtra(StreamingService.EXTRA_URL, url.trim())
                                }
                                ContextCompatStart(ctx, intent)
                            },
                            enabled = url.isNotBlank() && state !is StreamingState.Playing,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5AC8FA)),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Yayınla", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                ctx.startService(Intent(ctx, StreamingService::class.java).apply {
                                    action = StreamingService.ACTION_STOP
                                })
                            },
                            enabled = state is StreamingState.Playing || state is StreamingState.Connected,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A)),
                        ) {
                            Icon(Icons.Default.Stop, null)
                        }
                    }
                }
            }

            // Transport controls
            AnimatedVisibility(visible = state is StreamingState.Playing, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { vm.togglePause() }) {
                            Icon(
                                if (state is StreamingState.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer hint
            Text(
                "Telefon ve TV aynı Wi-Fi'da olmalı. Bağlantı HTTPS ile şifrelenir.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ConnectionDot(state: StreamingState) {
    val color = when (state) {
        is StreamingState.Connected, is StreamingState.Playing -> Color(0xFF30D158)
        is StreamingState.Connecting -> Color(0xFFFFD60A)
        is StreamingState.Error -> Color(0xFFFF453A)
        else -> Color.White.copy(alpha = 0.3f)
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun ContextCompatStart(ctx: android.content.Context, intent: Intent) {
    androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
}
