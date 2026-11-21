package com.iingstd.launcher

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView

class DecorationService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel = "iing_decoration"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "Dekorasi IING STD", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, DecorationService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        notification.setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Dekorasi IING STD aktif")
            .setContentText("Watermark transparan aktif • ketuk Sembunyikan untuk mematikan")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Sembunyikan", stopIntent)
        startForeground(21, notification.build())
        showDecoration()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (overlay == null) showDecoration()
        return START_NOT_STICKY
    }
    private fun showDecoration() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        val saved = getSharedPreferences("MainActivity", MODE_PRIVATE).getString("signature_photo", null)
        val photo = try { saved?.let { contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream) } } } catch (_: Exception) { null }
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        group.addView(object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            override fun onDraw(canvas: Canvas) {
                val bitmap = photo ?: return
                val w = width.toFloat(); val h = height.toFloat()
                val heart = Path().apply {
                    moveTo(w / 2, h * .93f)
                    cubicTo(-w * .12f, h * .47f, w * .05f, h * .05f, w * .28f, h * .08f)
                    cubicTo(w * .43f, h * .07f, w * .50f, h * .23f, w / 2, h * .25f)
                    cubicTo(w * .60f, h * .04f, w * .84f, h * .03f, w * .95f, h * .20f)
                    cubicTo(w * 1.16f, h * .53f, w * .72f, h * .83f, w / 2, h * .93f)
                    close()
                }
                canvas.save(); canvas.clipPath(heart)
                val scale = maxOf(w / bitmap.width, h / bitmap.height)
                val dw = bitmap.width * scale; val dh = bitmap.height * scale
                canvas.drawBitmap(bitmap, null, RectF((w-dw)/2, (h-dh)/2, (w+dw)/2, (h+dh)/2), paint)
                canvas.restore()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(2).toFloat()
                paint.color = 0xff32e4c9.toInt()
                canvas.drawPath(heart, paint)
                paint.style = Paint.Style.FILL
            }
        }, LinearLayout.LayoutParams(dp(140), dp(140)))
        group.addView(TextView(this).apply {
            text = "IING SUTADI"
            textSize = 30f
            setTextColor(0xff32e4c9.toInt())
            typeface = Typeface.create("cursive", Typeface.ITALIC)
            gravity = Gravity.CENTER
            alpha = .90f
        }, LinearLayout.LayoutParams(-1, dp(55)))
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val params = WindowManager.LayoutParams(dp(280), dp(210),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER
            alpha = 0.22f
        }
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager?.addView(group, params)
            overlay = group
        } catch (_: Exception) { stopSelf() }
    }
    override fun onDestroy() {
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
        super.onDestroy()
    }
}
