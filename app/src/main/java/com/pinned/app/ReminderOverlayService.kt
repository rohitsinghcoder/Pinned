package com.pinned.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

/**
 * Displays a true full-screen TYPE_APPLICATION_OVERLAY window while the phone is unlocked.
 * ColorOS / Android 17 blocks background startActivity() from BroadcastReceivers once an app
 * leaves the foreground, whereas TYPE_APPLICATION_OVERLAY works reliably over any open app.
 */
class ReminderOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var activePin by mutableStateOf<Pin?>(null)
    private var vibrating by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pinId = intent?.getIntExtra(Reminders.EXTRA_PIN_ID, -1) ?: -1
        val pin = Store.load(this).find { it.id == pinId }
        if (pin == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        Reminders.dismissAlert(this, pin.id)
        activePin = pin
        if (rootView == null) {
            attachOverlay()
        } else {
            vibrating = ReminderVibrator.start(this)
        }
        return START_NOT_STICKY
    }

    private fun attachOverlay() {
        val themeMode = Store.theme(this)
        val container = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    activePin?.let { keepPinned(it) } ?: dismissOverlay()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeViewModelStoreOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PinnedTheme(themeMode) {
                    val pin = activePin
                    if (pin != null) {
                        LaunchedEffect(pin.id) {
                            vibrating = ReminderVibrator.start(this@ReminderOverlayService)
                            delay(60_000L)
                            stopVibration()
                        }
                        FullScreenReminder(
                            pin = pin,
                            theme = themeMode,
                            vibrating = vibrating,
                            onSilence = { stopVibration() },
                            onSnoozeUntil = { target, label -> snoozeUntil(pin, target, label) },
                            onKeepPinned = { keepPinned(pin) },
                            onMarkDone = { markDone(pin) },
                        )
                    }
                }
            }
        }
        container.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            windowManager?.addView(container, params)
            rootView = container
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun stopVibration() {
        vibrating = false
        ReminderVibrator.stop(this)
    }

    private fun snoozeUntil(pin: Pin, target: Long, toastLabel: String) {
        stopVibration()
        Store.updateReminder(this, pin.id, target)?.let { updated ->
            Reminders.schedule(this, updated)
            Notifier.show(this, updated, silent = true)
        }
        Toast.makeText(this, toastLabel, Toast.LENGTH_SHORT).show()
        dismissOverlay()
    }

    private fun keepPinned(pin: Pin) {
        stopVibration()
        Reminders.dismissAlert(this, pin.id)
        Notifier.show(this, pin.copy(remindAt = null), silent = true)
        dismissOverlay()
    }

    private fun markDone(pin: Pin) {
        stopVibration()
        Reminders.cancel(this, pin.id)
        Store.markDone(this, pin.id)
        Notifier.cancel(this, pin.id)
        Toast.makeText(this, "Done · ${pin.title}", Toast.LENGTH_SHORT).show()
        dismissOverlay()
    }

    private fun dismissOverlay() {
        stopVibration()
        rootView?.let { view ->
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        rootView = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVibration()
        rootView?.let { view ->
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        rootView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
