package com.devtools.displayhelper

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class OverlayService : Service() {

    companion object {
        fun anyOn() = Prefs.b("fps_on", false) || Prefs.b("circle_on", false) || Prefs.b("fab_on", false)

        /** Starts/stops the service according to the current toggles. */
        fun sync(ctx: Context) {
            if (anyOn() && Settings.canDrawOverlays(ctx)) {
                ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
            } else ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var fpsView: TextView? = null
    private var circleView: CircleView? = null
    private var fab: TextView? = null
    private var panel: LinearLayout? = null
    private var screenW = 0
    private var screenH = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(applicationContext)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val ch = "ov"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(ch, "Overlay", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Dev Display Helper").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        stopFps()
        listOf(fpsView, circleView, fab, panel).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        super.onDestroy()
    }

    private fun lp(w: Int, h: Int, touch: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    fun refresh() {
        val dm = resources.displayMetrics
        screenW = dm.widthPixels; screenH = dm.heightPixels
        refreshFps(); refreshCircle(); refreshFab()
        if (!anyOn()) stopSelf()
    }

    // ---------------- FPS panel ----------------
    private val frameCb = object : Choreographer.FrameCallback {
        var frames = 0; var last = 0L
        override fun doFrame(t: Long) {
            if (fpsView == null) return
            if (last == 0L) last = t
            frames++
            if (t - last >= 500_000_000L) {
                val fps = frames * 1e9 / (t - last)
                val hz = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(0).refreshRate
                fpsView?.text = String.format("%.0f FPS\n%.0f Hz", fps, hz)
                frames = 0; last = t
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopFps() { Choreographer.getInstance().removeFrameCallback(frameCb); frameCb.last = 0; frameCb.frames = 0 }

    private fun refreshFps() {
        if (!Prefs.b("fps_on", false)) {
            fpsView?.let { runCatching { wm.removeView(it) } }; fpsView = null; stopFps(); return
        }
        val x = (Prefs.f("fps_x", 0.02f) * screenW).toInt()
        val y = (Prefs.f("fps_y", 0.05f) * screenH).toInt()
        if (fpsView == null) {
            val tv = TextView(this).apply {
                setTextColor(Color.GREEN); setBackgroundColor(Color.argb(140, 0, 0, 0))
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f)); typeface = Typeface.MONOSPACE; text = "-- FPS"
            }
            fpsView = tv
            wm.addView(tv, lp(-2, -2, false).apply { this.x = x; this.y = y })
            Choreographer.getInstance().postFrameCallback(frameCb)
        }
        fpsView!!.textSize = Prefs.f("fps_size", 14f)
        fpsView!!.alpha = Prefs.f("fps_alpha", 0.85f)
        val p = fpsView!!.layoutParams as WindowManager.LayoutParams
        p.x = x; p.y = y
        wm.updateViewLayout(fpsView, p)
    }

    // ---------------- Visual circle (static, centered) ----------------
    class CircleView(c: Context) : View(c) {
        var color = Color.RED
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        override fun onDraw(canvas: Canvas) {
            paint.color = color
            val r = minOf(width, height) / 2f - 4f
            canvas.drawCircle(width / 2f, height / 2f, r, paint)
        }
    }

    private fun refreshCircle() {
        if (!Prefs.b("circle_on", false)) {
            circleView?.let { runCatching { wm.removeView(it) } }; circleView = null; return
        }
        val size = dp(Prefs.f("circle_r", 60f) * 2)
        if (circleView == null) {
            circleView = CircleView(this)
            wm.addView(circleView, lp(size, size, false))
        }
        circleView!!.color = if (Prefs.b("circle_green", false)) Color.GREEN else Color.RED
        circleView!!.alpha = Prefs.f("circle_alpha", 0.8f)
        circleView!!.invalidate()
        val p = circleView!!.layoutParams as WindowManager.LayoutParams
        p.width = size; p.height = size
        p.x = (screenW - size) / 2; p.y = (screenH - size) / 2
        wm.updateViewLayout(circleView, p)
    }

    // ---------------- Floating button + quick panel ----------------
    private fun refreshFab() {
        if (!Prefs.b("fab_on", false)) {
            fab?.let { runCatching { wm.removeView(it) } }; fab = null
            panel?.let { runCatching { wm.removeView(it) } }; panel = null; return
        }
        val size = dp(Prefs.f("fab_size", 48f))
        if (fab == null) {
            val tv = TextView(this).apply {
                text = "⚙"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 20f
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#3367D6")) }
            }
            fab = tv
            val params = lp(size, size, true).apply {
                x = Prefs.f("fab_x", screenW - size * 1.5f).toInt(); y = Prefs.f("fab_y", screenH * 0.4f).toInt()
            }
            tv.setOnTouchListener(object : View.OnTouchListener {
                var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
                override fun onTouch(v: View, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY; moved = false }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                            params.x = sx + dx; params.y = sy + dy
                            wm.updateViewLayout(tv, params)
                        }
                        MotionEvent.ACTION_UP -> {
                            if (moved) { Prefs.setF("fab_x", params.x.toFloat()); Prefs.setF("fab_y", params.y.toFloat()) }
                            else togglePanel(params)
                        }
                    }
                    return true
                }
            })
            wm.addView(tv, params)
        }
        val p = fab!!.layoutParams as WindowManager.LayoutParams
        p.width = size; p.height = size
        fab!!.alpha = Prefs.f("fab_alpha", 0.85f)
        wm.updateViewLayout(fab, p)
    }

    private fun togglePanel(anchor: WindowManager.LayoutParams) {
        if (panel != null) { runCatching { wm.removeView(panel) }; panel = null; return }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(235, 30, 30, 30)); setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        fun btn(t: String, a: () -> Unit) = col.addView(Button(this).apply { text = t; setOnClickListener { a() } })
        val hz = RefreshCtl.supported(this).maxOrNull() ?: 60
        btn("رفع إلى $hz Hz") { Thread { RefreshCtl.boost(hz) }.start() }
        btn("تثبيت الإطارات") { Thread { RefreshCtl.lock(hz) }.start() }
        btn("إلغاء التثبيت") { Thread { RefreshCtl.unlock() }.start() }
        btn("عداد FPS") { Prefs.setB("fps_on", !Prefs.b("fps_on", false)); refresh() }
        btn("الدائرة") { Prefs.setB("circle_on", !Prefs.b("circle_on", false)); refresh() }
        btn("فتح التطبيق") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        btn("إغلاق") { togglePanel(anchor) }
        panel = col
        wm.addView(col, lp(-2, -2, true).apply { x = (anchor.x - dp(150f)).coerceAtLeast(0); y = anchor.y + anchor.height })
    }
}
