package com.example.modmenu

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: DragLayout? = null
    private var web: WebView? = null
    private val ui = Handler(Looper.getMainLooper())
    private var density = 1f
    private var startX = 0
    private var startY = 0
    private var isOpen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) {
            startForegroundCompat()
            createOverlay()
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("menu", "Mod Menu", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "menu")
        else Notification.Builder(this)
        val n = builder
            .setContentTitle("Mod Menu работает")
            .setContentText("Нажми, чтобы открыть приложение")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        density = dm.density
        val size = (74 * density).toInt()

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dm.heightPixels / 3
        }

        val webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/mod_menu.html")
        }
        web = webView

        val layout = DragLayout(this).apply {
            fabPx = size.toFloat()
            addView(webView, FrameLayout.LayoutParams(-1, -1))
            onDown = { startX = lp.x; startY = lp.y }
            onMove = { dx, dy ->
                lp.x = startX + dx.toInt()
                lp.y = startY + dy.toInt()
                clampPos()
                wm.updateViewLayout(this, lp)
            }
            onTap = { webView.evaluateJavascript("toggleMenu()", null) }
            onEnd = {
                if (!isOpen) {
                    val sw = resources.displayMetrics.widthPixels
                    lp.x = if (lp.x + lp.width / 2 < sw / 2) 0 else sw - lp.width
                    wm.updateViewLayout(this, lp)
                }
            }
        }
        root = layout
        wm.addView(layout, lp)
    }

    private fun clampPos() {
        val dm = resources.displayMetrics
        lp.x = lp.x.coerceIn(0, max(0, dm.widthPixels - lp.width))
        lp.y = lp.y.coerceIn(0, max(0, dm.heightPixels - lp.height))
    }

    private fun resize(w: Int, h: Int, fab: Int, open: Boolean) {
        val r = root ?: return
        val dm = resources.displayMetrics
        isOpen = open
        lp.width = min((w * density).toInt(), dm.widthPixels)
        lp.height = min((h * density).toInt(), dm.heightPixels)
        r.fabPx = fab * density
        clampPos()
        wm.updateViewLayout(r, lp)
    }

    /** Сюда приходят события из меню. Добавляй свой код в нужные места. */
    inner class Bridge {
        @JavascriptInterface
        fun onSize(w: Int, h: Int, fab: Int, open: Boolean) {
            ui.post { resize(w, h, fab, open) }
        }

        @JavascriptInterface
        fun onToggle(key: String, value: Boolean) {
            Log.d("ModMenu", "toggle $key = $value")
            // TODO: твой код для переключателей
        }

        @JavascriptInterface
        fun onValue(key: String, value: Int) {
            Log.d("ModMenu", "value $key = $value")
            // TODO: твой код для слайдеров
        }

        @JavascriptInterface
        fun onAction(name: String) {
            Log.d("ModMenu", "action $name")
            // TODO: твой код для кнопок
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            ui.post { Toast.makeText(this@OverlayService, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onDestroy() {
        root?.let { runCatching { wm.removeView(it) } }
        web?.destroy()
        root = null
        web = null
        super.onDestroy()
    }
}

/** Ловит нажатие и перетаскивание круглой кнопки (левый верхний угол окна). */
class DragLayout(ctx: Context) : FrameLayout(ctx) {
    var fabPx = 0f
    var onDown: () -> Unit = {}
    var onMove: (Float, Float) -> Unit = { _, _ -> }
    var onTap: () -> Unit = {}
    var onEnd: () -> Unit = {}

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var active = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && e.x <= fabPx && e.y <= fabPx) {
            downX = e.rawX; downY = e.rawY
            moved = false; active = true
            onDown()
            return true
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!active) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!moved && hypot(dx, dy) > slop) moved = true
                if (moved) onMove(dx, dy)
            }
            MotionEvent.ACTION_UP -> {
                active = false
                if (moved) onEnd() else onTap()
            }
            MotionEvent.ACTION_CANCEL -> active = false
        }
        return true
    }
}
