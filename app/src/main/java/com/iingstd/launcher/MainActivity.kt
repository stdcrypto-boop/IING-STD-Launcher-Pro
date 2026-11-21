package com.iingstd.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val accent = Color.rgb(43, 226, 197)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var battery: TextView
    private lateinit var grid: GridLayout
    private lateinit var search: EditText
    private val apps = mutableListOf<AppEntry>()
    private data class AppEntry(val label: String, val intent: Intent, val icon: android.graphics.drawable.Drawable)

    private val ticker = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            date.text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000L)
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            battery.text = if (level >= 0 && scale > 0) "⚡ ${level * 100 / scale}%" else "BAT --"
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 25, 28)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.navigationBarColor = Color.rgb(5, 25, 28)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xff061619.toInt(), 0xff0d3435.toInt(), 0xff061619.toInt()))
        }
        setContentView(root)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val signal = label("◢  5G  •  Wi-Fi", 13f, accent)
        top.addView(signal, LinearLayout.LayoutParams(0, dp(38), 1f))
        val brand = label("IING STD  /  LAUNCHER", 15f, accent).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(34)))
        battery = label("BAT --", 14f, accent)
        top.addView(battery)
        root.addView(top)
        clock = label("", 46f).apply { setTypeface(null, Typeface.BOLD) }
        root.addView(clock, LinearLayout.LayoutParams(-1, dp(75)))
        date = label("", 14f, 0xffb5c7d5.toInt())
        root.addView(date, LinearLayout.LayoutParams(-1, dp(28)))
        val subtitle = label("YOUR SPACE  •  YOUR STYLE", 11f, accent)
        root.addView(subtitle, LinearLayout.LayoutParams(-1, dp(30)))
        val divider = View(this).apply { setBackgroundColor(0x552be2c5) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(14) })
        search = EditText(this).apply {
            hint = "⌕  Cari aplikasi..."
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0xffa5b8c7.toInt())
            backgroundTintList = ColorStateList.valueOf(accent)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(54)))
        val title = label("SEMUA APLIKASI", 13f, accent).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(42)))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        grid = GridLayout(this).apply { columnCount = 4 }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val dock = label("⌂   BERANDA     •     IING STD LAUNCHER PRO", 12f, accent).apply {
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0xff103a39.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), accent)
            }
        }
        root.addView(dock, LinearLayout.LayoutParams(-1, dp(46)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderApps(s.toString())
            override fun afterTextChanged(s: Editable?) {}
        })
        loadApps()
        handler.post(ticker)
    }
    private fun loadApps() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps.clear()
        packageManager.queryIntentActivities(query, 0).forEach { info ->
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            apps.add(AppEntry(info.loadLabel(packageManager).toString(), launch, info.loadIcon(packageManager)))
        }
        apps.sortBy { it.label.lowercase(Locale.getDefault()) }
        renderApps(search.text.toString())
    }
    private fun renderApps(filter: String) {
        grid.removeAllViews()
        val width = (resources.displayMetrics.widthPixels - dp(32)) / 4
        apps.filter { it.label.contains(filter, ignoreCase = true) }.forEach { app ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
                isClickable = true
                isFocusable = true
                contentDescription = "Buka ${app.label}"
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            cell.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
            val name = label(app.label, 11f).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            cell.addView(name, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(5) })
            cell.setOnClickListener {
                try { startActivity(app.intent) }
                catch (e: Exception) { Toast.makeText(this, "Gagal membuka ${app.label}", Toast.LENGTH_SHORT).show() }
            }
            grid.addView(cell, GridLayout.LayoutParams().apply {
                this.width = width
                this.height = dp(116)
            })
        }
    }
    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (::grid.isInitialized) loadApps()
    }
    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
