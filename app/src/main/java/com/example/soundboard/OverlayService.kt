package com.example.soundboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var lp: WindowManager.LayoutParams
    private var panel: LinearLayout? = null
    private var panelH = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("vox", "VoxDeck", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            1,
            Notification.Builder(this, "vox")
                .setContentTitle("VoxDeck চালু আছে")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        )

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bubble = label("🎙", 24f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Col.ACCENT)
                setStroke(dp(2), 0x66FFFFFF)
            }
        }
        root.addView(bubble, LinearLayout.LayoutParams(dp(52), dp(52)))

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    lp.x = sx + dx
                    lp.y = sy + dy
                    wm.updateViewLayout(root, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    if (panel != null) closePanel() else openPanel()
                }
            }
            true
        }

        try { wm.addView(root, lp) } catch (e: Exception) { stopSelf() }
    }

    private fun buildPanel(): LinearLayout {
        val clips = Store.load(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(0xF2171923.toInt(), 18)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            label("VoxDeck", 15f, Col.TEXT, true).apply { setPadding(dp(8), 0, 0, 0) },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        head.addView(label("⏹", 20f).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { Player.stop() }
        })
        head.addView(label("✕", 18f).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { closePanel() }
        })
        box.addView(head)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (clips.isEmpty()) {
            list.addView(label("অ্যাপে গিয়ে ভয়েস যোগ করুন", 13f, Col.MUTED).apply {
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
        }
        clips.forEach { c ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(5), dp(4), dp(5))
            }
            row.addView(thumbView(c.image, 40))
            val mid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, dp(4), 0)
            }
            mid.addView(label(c.name, 14f, Col.TEXT, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            mid.addView(label(fxOf(c.fx).label, 11f, Col.MUTED))
            row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label("👂", 20f).apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener { Player.play(this@OverlayService, c, true) }
            })
            row.addView(label("▶️", 20f).apply {
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener { Player.play(this@OverlayService, c, false) }
            })
            list.addView(row)
        }
        val h = minOf(dp(300), maxOf(clips.size, 1) * dp(58) + dp(8))
        val sv = ScrollView(this).apply { addView(list) }
        box.addView(sv, LinearLayout.LayoutParams(dp(280), h))
        panelH = h + dp(56)
        return box
    }

    private fun openPanel() {
        val p = buildPanel()
        root.addView(p)
        panel = p
        val dm = resources.displayMetrics
        lp.x = lp.x.coerceIn(0, maxOf(0, dm.widthPixels - dp(300)))
        lp.y = lp.y.coerceAtMost(maxOf(0, dm.heightPixels - dp(52) - panelH - dp(24)))
        try { wm.updateViewLayout(root, lp) } catch (e: Exception) { }
    }

    private fun closePanel() {
        panel?.let { root.removeView(it) }
        panel = null
    }

    override fun onDestroy() {
        Player.stop()
        try { wm.removeView(root) } catch (e: Exception) { }
        super.onDestroy()
    }
}
