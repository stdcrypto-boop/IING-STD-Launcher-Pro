package com.iingstd.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.graphics.drawable.Drawable
import android.content.*
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val aqua = 0xff32e4c9.toInt()
    private val muted = 0xffa6c7c6.toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val apps = mutableListOf<App>()
    private data class App(val name: String, val pkg: String, val launch: Intent, val icon: android.graphics.drawable.Drawable)
    private lateinit var root: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var network: TextView
    private lateinit var signalBars: SignalBars
    private var signalLevel = 0
    private lateinit var battery: TextView
    private lateinit var speed: TextView
    private lateinit var content: FrameLayout
    private var drawer = false
    private var panel = false
    private var page = 0
    private var columns = 4
    private var lastBytes = -1L
    private var lastAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var gestureX = 0f
    private var gestureY = 0f
    private var showWallpaper = true
    private val favorites = linkedSetOf<String>()
    private var favoritesInitialized = false
    private lateinit var dockBar: LinearLayout
    private val tick = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) {
                clock.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                date.text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())
                updateStatus()
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (::battery.isInitialized) battery.text = if (level >= 0 && scale > 0) "▰ ${level * 100 / scale}%" else "BAT --"
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun text(s: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }
    private fun shape(color: Int, radius: Int = 20, outline: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (outline) setStroke(dp(1), aqua)
    }
    private inner class SignalBars(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val barWidth = dp(7).toFloat()
            val gap = dp(4).toFloat()
            val base = height - dp(4).toFloat()
            for (i in 0..3) {
                val h = dp(9 + i * 7).toFloat()
                paint.color = if (i < signalLevel) aqua else 0xff315754.toInt()
                val x = dp(2).toFloat() + i * (barWidth + gap)
                canvas.drawRoundRect(x, base - h, x + barWidth, base, dp(3).toFloat(), dp(3).toFloat(), paint)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xff061a1d.toInt()
        window.decorView.systemUiVisibility = 0
        window.navigationBarColor = 0xff061a1d.toInt()
        columns = getPreferences(MODE_PRIVATE).getInt("columns", 4).coerceIn(4, 6)
        showWallpaper = getPreferences(MODE_PRIVATE).getBoolean("wallpaper", true)
        favoritesInitialized = getPreferences(MODE_PRIVATE).contains("favorites_order")
        getPreferences(MODE_PRIVATE).getString("favorites_order", "")?.split("|")?.filter { it.isNotBlank() }?.forEach { favorites.add(it) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xff061719.toInt(), 0xff104441.toInt(), 0xff082126.toInt()))
        }
        setContentView(root)
        applyWallpaper()
        val brand = text("IING STD  /  LAUNCHER PRO", 13f, aqua).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(35)))
        val status = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        signalBars = SignalBars(this)
        status.addView(signalBars, LinearLayout.LayoutParams(dp(48), dp(40)))
        network = text("Memeriksa jaringan", 13f, aqua).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        status.addView(network, LinearLayout.LayoutParams(0, dp(40), 1f))
        speed = text("↓ -- KB/s", 12f, muted)
        status.addView(speed, LinearLayout.LayoutParams(0, dp(38), 1f))
        battery = text("BAT --", 16f, aqua)
        status.addView(battery)
        root.addView(status)
        clock = text("", 43f).apply { setTypeface(null, Typeface.BOLD) }
        root.addView(clock, LinearLayout.LayoutParams(-1, dp(75)))
        date = text("", 13f, muted)
        root.addView(date, LinearLayout.LayoutParams(-1, dp(28)))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val home = text("⌂  BERANDA", 12f, aqua)
        home.setOnClickListener { drawer = false; panel = false; render() }
        actions.addView(home, LinearLayout.LayoutParams(0, dp(48), 1f))
        val all = text("⊞  APLIKASI", 12f, aqua)
        all.setOnClickListener { drawer = true; panel = false; page = 0; render() }
        actions.addView(all, LinearLayout.LayoutParams(0, dp(48), 1f))
        val controls = text("☷  PANEL", 12f, aqua)
        controls.setOnClickListener { panel = !panel; render() }
        actions.addView(controls, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = shape(0xdd103c3b.toInt(), 24, true)
        }
        root.addView(dock, LinearLayout.LayoutParams(-1, dp(74)))
        dock.tag = "dock"
        dockBar = dock
        loadApps()
        buildDock(dock)
        render()
        handler.post(tick)
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureX = ev.rawX
            gestureY = ev.rawY
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP && !panel) {
            val dx = ev.rawX - gestureX
            val dy = ev.rawY - gestureY
            if (!drawer && kotlin.math.abs(dx) > dp(90) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                val maxPage = ((favorites.size.coerceAtLeast(1) - 1) / (columns * 4))
                page = (page + if (dx < 0) 1 else -1).coerceIn(0, maxPage)
                render()
                return true
            }
            if (!drawer && dy < -dp(100) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                drawer = true; page = 0; render(); return true
            }
            if (drawer && dy > dp(120) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                drawer = false; page = 0; render(); return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }
    private fun applyWallpaper() {
        if (showWallpaper) {
            try {
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x99051719.toInt(), 0x770b2527.toInt(), 0xbb061719.toInt()))
            } catch (_: Exception) {
                root.setBackgroundColor(0xff061719.toInt())
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xff061719.toInt(), 0xff104441.toInt(), 0xff082126.toInt()))
        }
    }
    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                try { startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 100) }
                catch (_: Exception) { openHomeSettings() }
                return
            }
        }
        openHomeSettings()
    }
    private fun openHomeSettings() {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: Exception) { Toast.makeText(this, "Pilih IING STD sebagai aplikasi Home di Pengaturan", Toast.LENGTH_LONG).show() }
    }
    private fun loadApps() {
        apps.clear()
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(query, 0).forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) return@forEach
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            apps.add(App(info.loadLabel(packageManager).toString(), pkg, launch, info.loadIcon(packageManager)))
        }
        apps.sortBy { it.name.lowercase(Locale.getDefault()) }
        if (!favoritesInitialized) {
            apps.filter { app -> listOf("camera", "chrome", "whatsapp", "setting", "phone", "message").any { app.name.contains(it, true) || app.pkg.contains(it, true) } }
                .take(8).forEach { favorites.add(it.pkg) }
            saveFavorites()
        }
        if (::dockBar.isInitialized) buildDock(dockBar)
    }
    private fun iconCell(app: App, width: Int, height: Int): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), dp(9), dp(3), dp(5))
            isClickable = true; isFocusable = true
            contentDescription = "Buka ${app.name}"
        }
        cell.addView(ImageView(this).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        cell.addView(text(app.name, 11f).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, dp(39)).apply { topMargin = dp(5) })
        cell.setOnClickListener { try { startActivity(app.launch) } catch (_: Exception) { Toast.makeText(this, "Tidak dapat membuka ${app.name}", Toast.LENGTH_SHORT).show() } }
        cell.setOnLongClickListener { appMenu(app); true }
        cell.layoutParams = GridLayout.LayoutParams().apply { this.width = width; this.height = height }
        return cell
    }
    private fun saveFavorites() {
        favoritesInitialized = true
        getPreferences(MODE_PRIVATE).edit().putString("favorites_order", favorites.joinToString("|")).apply()
    }
    private fun appMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.name)
            .setItems(arrayOf("Buka", if (favorites.contains(app.pkg)) "Hapus dari beranda" else "Tambahkan ke beranda", "Pindahkan ikon ke awal", "Info aplikasi", "Uninstall")) { _, option ->
                try {
                    when (option) {
                        0 -> startActivity(app.launch)
                        1 -> { if (!favorites.add(app.pkg)) favorites.remove(app.pkg); saveFavorites(); render() }
                        2 -> { favorites.remove(app.pkg); val old = favorites.toList(); favorites.clear(); favorites.add(app.pkg); favorites.addAll(old); saveFavorites(); page = 0; render() }
                        3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                        4 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                    }
                } catch (_: Exception) { Toast.makeText(this, "Tindakan tidak tersedia", Toast.LENGTH_SHORT).show() }
            }.show()
    }
    private fun buildDock(dock: LinearLayout) {
        dock.removeAllViews()
        val preferred = listOf("dialer", "messag", "camera", "chrome")
        preferred.forEach { key ->
            val app = apps.firstOrNull { it.pkg.contains(key, true) || it.name.contains(key, true) }
            val image = ImageView(this)
            if (app != null) {
                image.setImageDrawable(app.icon)
                image.setOnClickListener { try { startActivity(app.launch) } catch (_: Exception) {} }
                image.setOnLongClickListener { appMenu(app); true }
            } else {
                image.setImageResource(android.R.drawable.ic_menu_help)
                image.alpha = 0.3f
            }
            image.setPadding(dp(10), dp(10), dp(10), dp(10))
            dock.addView(image, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
    }
    private fun render() {
        content.removeAllViews()
        when {
            panel -> renderPanel()
            drawer -> renderDrawer()
            else -> renderHome()
        }
    }
    private fun renderHome() {
        // Decorative signature is drawn behind the workspace and never consumes touches.
        val signature = TextView(this).apply {
            text = "IING SUTADI"
            textSize = 38f
            setTextColor(aqua)
            setTypeface(Typeface.create("cursive", Typeface.ITALIC))
            gravity = Gravity.CENTER
            alpha = 0.35f
            isClickable = false
            isLongClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, _ -> false }
        }
        content.addView(signature, FrameLayout.LayoutParams(-1, dp(100), Gravity.CENTER))
        val area = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(area, FrameLayout.LayoutParams(-1, -1))
        val heading = text("BERANDA  •  IING STD", 13f, aqua).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.BOLD)
        }
        area.addView(heading, LinearLayout.LayoutParams(-1, dp(40)))
        val grid = GridLayout(this).apply { columnCount = columns }
        val homeScroll = ScrollView(this).apply { isFillViewport = true; addView(grid) }
        area.addView(homeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val perPage = columns * 4
        val homeApps = favorites.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
        val maxPage = ((homeApps.size.coerceAtLeast(1) - 1) / perPage)
        page = page.coerceIn(0, maxPage)
        val width = (resources.displayMetrics.widthPixels - dp(32)) / columns
        homeApps.drop(page * perPage).take(perPage).forEach {
            grid.addView(iconCell(it, width, dp(106)))
        }
        val indicator = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (i in 0..maxPage.coerceAtMost(12)) {
            val dot = View(this).apply {
                background = shape(if (i == page) aqua else 0xff42605f.toInt(), 10)
                setOnClickListener { page = i; render() }
            }
            indicator.addView(dot, LinearLayout.LayoutParams(dp(if (i == page) 19 else 7), dp(7)).apply {
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
        }
        area.addView(indicator, LinearLayout.LayoutParams(-1, dp(26)))
        val hint = text("← Geser halaman →  •  ↑ Semua aplikasi  •  Tahan ikon untuk mengatur", 11f, muted)
        hint.setOnClickListener { drawer = true; page = 0; render() }
        area.addView(hint, LinearLayout.LayoutParams(-1, dp(35)))
    }
    private fun renderDrawer() {
        val area = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(area, FrameLayout.LayoutParams(-1, -1))
        val search = EditText(this).apply {
            hint = "Cari aplikasi"; setSingleLine(true); textSize = 16f
            setTextColor(Color.WHITE); setHintTextColor(muted)
        }
        area.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        val pager = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val prev = text("‹", 30f, aqua)
        pager.addView(prev, LinearLayout.LayoutParams(dp(50), dp(43)))
        val count = text("", 12f, aqua)
        pager.addView(count, LinearLayout.LayoutParams(0, dp(43), 1f))
        val next = text("›", 30f, aqua)
        pager.addView(next, LinearLayout.LayoutParams(dp(50), dp(43)))
        val layout = text("⊞", 24f, aqua)
        pager.addView(layout, LinearLayout.LayoutParams(dp(45), dp(43)))
        area.addView(pager)
        val grid = GridLayout(this).apply { columnCount = columns }
        val scroll = ScrollView(this).apply { addView(grid); isFillViewport = true }
        area.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun redraw() {
            grid.removeAllViews()
            val filtered = apps.filter { it.name.contains(search.text.toString(), true) }
            val perPage = columns * 5
            val max = ((filtered.size - 1).coerceAtLeast(0)) / perPage
            page = page.coerceIn(0, max)
            count.text = "${page + 1} / ${max + 1}  •  ${columns} KOLOM"
            val width = (resources.displayMetrics.widthPixels - dp(32)) / columns
            filtered.drop(page * perPage).take(perPage).forEach { grid.addView(iconCell(it, width, dp(114))) }
        }
        prev.setOnClickListener { page--; redraw() }
        next.setOnClickListener { page++; redraw() }
        layout.setOnClickListener { columns = if (columns == 4) 5 else if (columns == 5) 6 else 4; grid.columnCount = columns; page = 0; redraw() }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { page = 0; redraw() }
            override fun afterTextChanged(s: Editable?) {}
        })
        scroll.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) { startX = e.x; return false }
                if (e.action == MotionEvent.ACTION_UP && kotlin.math.abs(e.x - startX) > dp(60)) {
                    page += if (e.x < startX) 1 else -1
                    redraw(); return true
                }
                return false
            }
        })
        redraw()
    }
    private fun renderPanel() {
        val area = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollPanel = ScrollView(this)
        scrollPanel.addView(area)
        content.addView(scrollPanel, FrameLayout.LayoutParams(-1, -1))
        area.addView(text("PANEL TOSCA  •  KONTROL CEPAT", 16f, aqua), LinearLayout.LayoutParams(-1, dp(55)))
        val options = listOf("Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
            "Internet" to Settings.ACTION_WIRELESS_SETTINGS,
            "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "Tampilan" to Settings.ACTION_DISPLAY_SETTINGS,
            "Baterai" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "Pengaturan" to Settings.ACTION_SETTINGS)
        options.chunked(2).forEach { pair ->
            val row = LinearLayout(this)
            area.addView(row)
            pair.forEach { (title, action) ->
                val tile = text(title, 17f, aqua).apply {
                    background = shape(0xff124441.toInt(), 22, true)
                    setOnClickListener { try { startActivity(Intent(action)) } catch (_: Exception) {} }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(dp(4), dp(5), dp(4), dp(5)) })
            }
        }
        val layoutSetting = text("TATA LETAK IKON   ${columns} KOLOM   •   UBAH", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener { columns = if (columns == 4) 5 else if (columns == 5) 6 else 4; getPreferences(MODE_PRIVATE).edit().putInt("columns", columns).apply(); page = 0; render() }
        }
        area.addView(layoutSetting, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(10) })
        val wallpaperSetting = text("WALLPAPER HP  •  " + if (showWallpaper) "AKTIF" else "NONAKTIF", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener {
                showWallpaper = !showWallpaper
                getPreferences(MODE_PRIVATE).edit().putBoolean("wallpaper", showWallpaper).apply()
                applyWallpaper(); render()
            }
        }
        area.addView(wallpaperSetting, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(10) })
        val defaultSetting = text("JADIKAN LAUNCHER UTAMA  ⌂", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener { requestDefaultHome() }
        }
        area.addView(defaultSetting, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(10) })
        area.addView(text("Panel ini membuka pengaturan Android; tidak mengganti Quick Settings sistem.", 12f, muted),
            LinearLayout.LayoutParams(-1, dp(70)))
    }
    private fun updateStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork)
        val connected = cap != null
        network.text = when {
            cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            cap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Seluler"
            else -> "Offline"
        }
        // Android may hide radio strength from ordinary apps. Do not invent a full signal.
        val dbm = if (Build.VERSION.SDK_INT >= 29) cap?.signalStrength ?: Int.MIN_VALUE else Int.MIN_VALUE
        signalLevel = when {
            !connected -> 0
            dbm == Int.MIN_VALUE -> 0
            dbm >= -60 -> 4
            dbm >= -75 -> 3
            dbm >= -90 -> 2
            else -> 1
        }
        signalBars.contentDescription = if (dbm == Int.MIN_VALUE) "Kekuatan sinyal tidak tersedia" else "Kekuatan sinyal $signalLevel dari 4"
        signalBars.invalidate()
        val now = SystemClock.elapsedRealtime()
        val bytes = TrafficStats.getTotalRxBytes()
        if (lastAt > 0 && bytes >= lastBytes && bytes >= 0) {
            val rate = (bytes - lastBytes) * 1000 / (now - lastAt).coerceAtLeast(1)
            speed.text = if (rate > 1048576) "↓ %.1f MB/s".format(Locale.US, rate / 1048576.0) else "↓ ${rate / 1024} KB/s"
        }
        lastBytes = bytes; lastAt = now
    }
    override fun onStart() {
        super.onStart()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (::content.isInitialized) { loadApps(); render() }
    }
    override fun onStop() { unregisterReceiver(receiver); super.onStop() }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panel || drawer) { panel = false; drawer = false; render() }
        else super.onBackPressed()
    }
}
