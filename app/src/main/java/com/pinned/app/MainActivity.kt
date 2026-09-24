package com.pinned.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Bumped on every resume so permission / battery state is re-checked
    // after the user comes back from system settings.
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PinnedTheme { PinnedApp(resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

@Composable
private fun PinnedTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun PinnedApp(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pins = remember { mutableStateListOf<Pin>().apply { addAll(Store.load(ctx)) } }
    var composerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pin?>(null) }
    // Pin waiting for the "Mark as done?" confirmation.
    var confirming by remember { mutableStateOf<Pin?>(null) }
    // Pins confirmed as done, briefly showing their check before they leave the list.
    val completing = remember { mutableStateListOf<Int>() }

    val notificationsOn = remember(resumeTick) {
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    val batteryFree = remember(resumeTick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsOn) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Make sure every stored pin is actually showing (e.g. after permission is granted).
    LaunchedEffect(notificationsOn) {
        if (notificationsOn) Notifier.showAll(ctx)
    }

    fun persist() = Store.save(ctx, pins.toList())

    // Long-press drag to reorder. The dragged card follows the finger via dragOffset and
    // swaps places with a neighbour once its centre crosses into that neighbour.
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun dragBy(delta: Float) {
        dragOffset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == draggingId } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = items.firstOrNull {
            it.key is Int && it.key != draggingId && center.toInt() in it.offset..(it.offset + it.size)
        } ?: return
        val from = pins.indexOfFirst { it.id == draggingId }
        val to = pins.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        pins.add(to, pins.removeAt(from))
        // The card's slot moved; shift the offset so it stays under the finger.
        dragOffset += current.offset - target.offset
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun endDrag() {
        if (draggingId != null) persist()
        draggingId = null
        dragOffset = 0f
    }

    fun done(pin: Pin) {
        val index = pins.indexOfFirst { it.id == pin.id }
        if (index < 0) return
        pins.removeAt(index)
        persist()
        Notifier.cancel(ctx, pin.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                "Done · ${pin.title}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pins.add(index.coerceAtMost(pins.size), pin)
                persist()
                Notifier.show(ctx, pin)
            }
        }
    }

    // Called once the user confirms: show the check for a beat, then remove.
    fun complete(pin: Pin) {
        completing.add(pin.id)
        scope.launch {
            delay(320)
            completing.remove(pin.id)
            done(pin)
        }
    }

    fun save(title: String, note: String) {
        val current = editing
        val pin = if (current == null) {
            Pin(Store.nextId(ctx), title, note, System.currentTimeMillis()).also { pins.add(0, it) }
        } else {
            current.copy(title = title, note = note).also { updated ->
                val i = pins.indexOfFirst { it.id == updated.id }
                if (i >= 0) pins[i] = updated
            }
        }
        persist()
        Notifier.show(ctx, pin, silent = current != null)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; composerOpen = true },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Pin something")
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { Header(pins.size) }

            if (!notificationsOn) {
                item(key = "hint-notif") {
                    Hint("Notifications are off", "Pins can't appear until you allow them.", "Allow") {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                }
            } else if (!batteryFree && pins.isNotEmpty()) {
                item(key = "hint-battery") {
                    Hint("Keep pins alive", "Let Pinned run in the background so swiped pins come back.", "Allow") {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }
                }
            }

            if (pins.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }

            items(pins, key = { it.id }) { pin ->
                val dragging = pin.id == draggingId
                val lift by animateFloatAsState(if (dragging) 1f else 0f, label = "lift")
                PinRow(
                    pin = pin,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            scaleX = 1f + 0.03f * lift
                            scaleY = 1f + 0.03f * lift
                            shadowElevation = 12.dp.toPx() * lift
                            shape = RoundedCornerShape(24.dp)
                        }
                        .pointerInput(pin.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingId = pin.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragBy(amount.y)
                                },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                            )
                        },
                    checked = pin.id in completing,
                    onRequestDone = { confirming = pin },
                    onClick = { editing = pin; composerOpen = true },
                )
            }
        }
    }

    Composer(
        visible = composerOpen,
        initial = editing,
        onDismiss = { composerOpen = false },
        onSave = { title, note -> save(title, note); composerOpen = false },
        onRequestDone = { confirming = editing; composerOpen = false },
    )

    confirming?.let { pin ->
        ConfirmDone(
            pin = pin,
            onConfirm = { confirming = null; complete(pin) },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun Header(count: Int) {
    Column(Modifier.padding(start = 4.dp, top = 24.dp, bottom = 14.dp)) {
        Text(
            "Pinned",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        AnimatedContent(count, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "count") { n ->
            Text(
                when (n) {
                    0 -> "Nothing on your mind"
                    1 -> "1 thing to remember"
                    else -> "$n things to remember"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hint(title: String, body: String, action: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(12.dp))
            Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_pin),
                contentDescription = null,
                modifier = Modifier.size(36.dp).rotate(35f),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Nothing pinned", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap + to pin something\nyou don't want to forget.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinRow(
    pin: Pin,
    modifier: Modifier,
    dragging: Boolean,
    checked: Boolean,
    onRequestDone: () -> Unit,
    onClick: () -> Unit,
) {
    // A full swipe only asks for confirmation; the card always springs back.
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) onRequestDone()
            false
        },
        positionalThreshold = { width -> width * 0.55f },
    )
    val shape = RoundedCornerShape(24.dp)

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = !dragging,
        enableDismissFromEndToStart = !dragging,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
    ) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pin.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                    )
                    if (pin.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            pin.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pinned " + DateUtils.getRelativeTimeSpanString(
                            pin.created, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                        ).toString().replaceFirstChar { it.lowercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                CheckCircle(checked) { if (!checked) onRequestDone() }
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, label = "fill"
    )
    val ring by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "ring"
    )
    IconButton(onClick = onClick) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(checked, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Mark done",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Bottom panel that rides on top of the keyboard. It lives in the activity window (not a
 * separate sheet window), so the panel and keyboard slide up together in one motion.
 */
@Composable
private fun Composer(
    visible: Boolean,
    initial: Pin?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onRequestDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    fun close(action: () -> Unit) {
        focusManager.clearFocus()
        action()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close(onDismiss) }
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            BackHandler { close(onDismiss) }

            // Start with the cursor after the existing text when editing.
            var title by remember { mutableStateOf(endOf(initial?.title ?: "")) }
            var note by remember { mutableStateOf(endOf(initial?.note ?: "")) }
            val focus = remember { FocusRequester() }
            // Focus on the very first frame so the keyboard opens together with the panel.
            LaunchedEffect(Unit) { focus.requestFocus() }

            // Drag the panel down to close it; a short drag springs back.
            val scope = rememberCoroutineScope()
            val dragY = remember { Animatable(0f) }
            val closeDistance = with(LocalDensity.current) { 110.dp.toPx() }

            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragY.value.roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { dragY.snapTo((dragY.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = { velocity ->
                            if (dragY.value > closeDistance || velocity > 1800f) {
                                close(onDismiss)
                            } else {
                                dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                    ),
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (initial == null) "New pin" else "Edit pin",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(
                        title, { title = it }, "What should you remember?",
                        MaterialTheme.typography.headlineSmall,
                        Modifier.focusRequester(focus),
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(note, { note = it }, "Add a note", MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (initial != null) {
                            TextButton(onClick = { close(onRequestDone) }) { Text("Mark done") }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { close { onSave(title.text.trim(), note.text.trim()) } },
                            enabled = title.text.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                        ) {
                            Text(if (initial == null) "Pin it" else "Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDone(pin: Pin, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
        title = { Text("Mark as done?") },
        text = {
            Text(
                "“${pin.title}” will be unpinned from your notifications.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(14.dp)) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

private fun endOf(text: String) = TextFieldValue(text, TextRange(text.length))

@Composable
private fun Field(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                inner()
            }
        },
    )
}
