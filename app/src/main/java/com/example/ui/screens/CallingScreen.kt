package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.ui.viewmodel.ChatViewModel

@Composable
fun CallingScreen(viewModel: ChatViewModel) {
    val callState by viewModel.callingState.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    // Pulsing circle animation for Voice Call ringing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F2C24), Color(0xFF070B19))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // VIDEO CAMERA FEED SIMULATOR
        if (callState.isVideo && callState.status == "CONNECTED") {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        if (isFrontCamera) "https://images.unsplash.com/photo-1544005313-94ddf0286df2"
                        else "https://images.unsplash.com/photo-1506744038136-46273834b3fb"
                    ),
                    contentDescription = "Simulated Camera Feed",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Sub-frame self camera bubble (top right)
                Card(
                    modifier = Modifier
                        .size(110.dp, 160.dp)
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter("https://images.unsplash.com/photo-1534528741775-53994a69daeb"),
                        contentDescription = "Self Camera",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // TEXT LABELS OVERLAY
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = if (callState.isVideo) "ConnectChat Video Call" else "ConnectChat Voice Call",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = callState.callerName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (callState.status) {
                        "CONNECTING" -> "Connecting..."
                        "RINGING" -> "Ringing..."
                        "CONNECTED" -> {
                            val duration = callState.durationSec
                            val min = duration / 60
                            val sec = duration % 60
                            String.format("%02d:%02d", min, sec)
                        }
                        "ENDED" -> "Call Ended"
                        else -> "Connecting..."
                    },
                    fontSize = 16.sp,
                    color = if (callState.status == "RINGING") Color.Green else Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }

            // PULSING AVATAR CHIP ON VOICE CALL
            if (!callState.isVideo || callState.status != "CONNECTED") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    // Pulsing animated rings
                    if (callState.status == "RINGING") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFF008069).copy(alpha = 0.2f))
                        )
                    }

                    Image(
                        painter = rememberAsyncImagePainter("https://images.unsplash.com/photo-1544005313-94ddf0286df2"),
                        contentDescription = "Caller Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // CONTROL CHIPS BAR
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (callState.isIncoming && callState.status == "RINGING") {
                    // INCOMING RING ACTION CONTROLS (Accept/Reject)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FloatingActionButton(
                            onClick = { viewModel.rejectOrEndCall() },
                            containerColor = Color.Red,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp).testTag("call_reject")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline Call", modifier = Modifier.size(28.dp))
                        }

                        FloatingActionButton(
                            onClick = { viewModel.acceptCall() },
                            containerColor = Color.Green,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp).testTag("call_accept")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Accept Call", modifier = Modifier.size(28.dp))
                        }
                    }
                } else {
                    // ACTIVE ON-CALL CONTROLS (Mute, Speaker, Camera flip, Hangup)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speaker switch
                        IconButton(
                            onClick = { isSpeakerOn = !isSpeakerOn },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f),
                                contentColor = if (isSpeakerOn) Color.Black else Color.White
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "Speaker Toggle"
                            )
                        }

                        // Video switch
                        if (callState.isVideo) {
                            IconButton(
                                onClick = { isFrontCamera = !isFrontCamera },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Camera")
                            }
                        }

                        // Mute switch
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                                contentColor = if (isMuted) Color.Black else Color.White
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute Toggle"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HANG UP RED FAB
                    FloatingActionButton(
                        onClick = { viewModel.rejectOrEndCall() },
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp).testTag("call_hangup")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
