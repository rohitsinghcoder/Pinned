package com.pinned.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReminderActivity : ComponentActivity() {
    private var activePin by mutableStateOf<Pin?>(null)
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

        if (!loadPinFromIntent(intent)) {
            finishAndRemoveTask()
            return
        }

        val themeMode = Store.theme(this)
        setContent {
            PinnedTheme(themeMode) {
                val pin = activePin
                if (pin != null) {
                    // Auto-stop vibration after 60s so unattended phones don't vibrate indefinitely.
                    LaunchedEffect(pin.id) {
                        startAttentionVibration()
                        delay(60_000L)
                        stopVibration()
                    }
                    FullScreenReminder(
                        pin = pin,
                        onSnooze = { minutes -> snooze(pin, minutes) },
                        onKeepPinned = { keepPinned(pin) },
                        onMarkDone = { markDone(pin) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (loadPinFromIntent(intent)) {
            startAttentionVibration()
        }
    }

    override fun onPause() {
        super.onPause()
        stopVibration()
    }

    override fun onDestroy() {
        stopVibration()
        super.onDestroy()
    }

    private fun loadPinFromIntent(intent: Intent): Boolean {
        val pinId = intent.getIntExtra(Reminders.EXTRA_PIN_ID, -1)
        val pin = Store.load(this).find { it.id == pinId } ?: return false
        Reminders.dismissAlert(this, pin.id)
        activePin = pin
        return true
    }

    private fun startAttentionVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        stopVibration()
        // Strong, attention-seeking alarm cadence at full motor intensity (255), zero sound.
        val timings = longArrayOf(0, 400, 120, 400, 120, 550, 650)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            v.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    private fun snooze(pin: Pin, minutes: Int) {
        stopVibration()
        val target = System.currentTimeMillis() + minutes * 60_000L
        Store.updateReminder(this, pin.id, target)?.let { updated ->
            Reminders.schedule(this, updated)
            Notifier.show(this, updated, silent = true)
        }
        Toast.makeText(this, "Snoozed for ${minutes}m", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }

    private fun keepPinned(pin: Pin) {
        stopVibration()
        Reminders.dismissAlert(this, pin.id)
        Notifier.show(this, pin.copy(remindAt = null), silent = true)
        finishAndRemoveTask()
    }

    private fun markDone(pin: Pin) {
        stopVibration()
        Reminders.cancel(this, pin.id)
        Store.markDone(this, pin.id)
        Notifier.cancel(this, pin.id)
        Toast.makeText(this, "Done · ${pin.title}", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }
}

@Composable
private fun FullScreenReminder(
    pin: Pin,
    onSnooze: (Int) -> Unit,
    onKeepPinned: () -> Unit,
    onMarkDone: () -> Unit,
) {
    BackHandler { onKeepPinned() }

    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    val timeNow = DateFormat.getTimeFormat(ctx).format(Date())
    val pinnedAgo = DateUtils.getRelativeTimeSpanString(
        pin.created, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString().replaceFirstChar { it.lowercase() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            // Top badge + current time
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            ) {
                Text(
                    "PINNED REMINDER · $timeNow",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            // Center hero section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                    Box(
                        Modifier
                            .size(124.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                        Color.Transparent,
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_pin),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).rotate(35f),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    pin.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                if (pin.note.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        pin.note,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Pinned $pinnedAgo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Snooze chips
            Text(
                "Snooze",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(5, 10, 15).forEach { mins ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSnooze(mins)
                            },
                    ) {
                        Text(
                            "+${mins}m",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Primary action: dismiss alert, keep ongoing pin
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onKeepPinned()
                },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Keep pinned",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Secondary action: deliberate 0.8s hold to prevent accidental completion
            HoldToDoneButton(onCompleted = onMarkDone)

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun HoldToDoneButton(onCompleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    var pressing by remember { mutableStateOf(false) }
    var showNudge by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(showNudge) {
        if (showNudge) {
            delay(1600L)
            showNudge = false
        }
    }

    val buttonScale by animateFloatAsState(
        targetValue = if (pressing) 0.98f else 1f,
        animationSpec = spring(),
        label = "holdScale",
    )
    val labelColor by animateColorAsState(
        targetValue = if (progress.value > 0.5f) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "holdLabelColor",
    )

    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (pressing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(buttonScale)
            .clip(shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (completed) return@awaitEachGesture
                    pressing = true
                    showNudge = false
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    val holdJob = scope.launch {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 800, easing = LinearEasing),
                        )
                        completed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCompleted()
                    }

                    waitForUpOrCancellation()
                    pressing = false
                    if (!completed) {
                        val reached = progress.value
                        holdJob.cancel()
                        if (reached < 0.85f) showNudge = true
                        scope.launch {
                            progress.animateTo(0f, animationSpec = spring())
                        }
                    }
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Left-to-right progress fill sweep
            if (progress.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        pressing -> "Keep holding…"
                        showNudge -> "Hold longer to mark done"
                        else -> "Hold to mark done"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                )
            }
        }
    }
}
