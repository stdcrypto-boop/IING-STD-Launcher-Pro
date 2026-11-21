package com.iingstd.launcher

import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var clock: TextView
    private lateinit var battery: TextView
    private lateinit var search: EditText
    private lateinit var appList: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val apps = mutableListOf<Pair<String, Intent>>()
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            battery.text = if (level >= 0 && scale > 0) "BAT ${level * 100 / scale}%" else "BAT --"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 12, 25)
        window.navigationBarColor = Color.rgb(7, 12, 25)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 35, 28, 18)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7,12,25), Color.rgb(12,33,53), Color.rgb(7,12,25)))
        }
        setContentView(root)
        val heading = text("IING STD  //  LAUNCHER", 16, 0xff38e9ff.toInt())
        heading.setTypeface(null, Typeface.BOLD)
        root.addView(heading)
        clock = text("", 48, Color.WHITE)
        clock.gravity = Gravity.CENTER
        root.addView(clock, LinearLayout.LayoutParams(-1, 110))
        battery = text("BAT --", 15, 0xff38e9ff.toInt())
        battery.gravity = Gravity.END
        root.addView(battery)
        root.addView(text("APLIKASI", 18, Color.WHITE))
        search = EditText(this).apply {
            hint = "Cari aplikasi..."
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xff9caec0.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xff38e9ff.toInt())
        }
        root.addView(search)
        val scroll = ScrollView(this)
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(appList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        loadApps()
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        handler.post(tick)
    }

    private fun text(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        setPadding(8, 12, 8, 12)
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, 0)
        apps.clear()
        resolved.forEach { info ->
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            apps.add(info.loadLabel(packageManager).toString() to launch)
        }
        apps.sortBy { it.first.lowercase(Locale.getDefault()) }
        renderApps("")
    }

    private fun renderApps(query: String) {
        appList.removeAllViews()
        apps.filter { it.first.contains(query, ignoreCase = true) }.forEach { (name, launch) ->
            val button = text("◈  $name", 17, Color.WHITE)
            button.setOnClickListener {
                try { startActivity(launch) }
                catch (e: Exception) { Toast.makeText(this, "Tidak dapat membuka $name", Toast.LENGTH_SHORT).show() }
            }
            appList.addView(button)
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (::appList.isInitialized) loadApps()
    }
    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
