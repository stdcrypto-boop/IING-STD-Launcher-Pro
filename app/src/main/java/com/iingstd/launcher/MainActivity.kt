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
import android.graphics.Path
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyDisplayInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import org.json.JSONArray

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
    private var cellularBarsAvailable = false
    private var radioType = "Seluler"
    private var telephonyListening = false
    private var cellularServiceAvailable = false
    private val phoneListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(strength: SignalStrength?) {
            cellularBarsAvailable = strength != null
            if (strength != null) signalLevel = strength.level.coerceIn(0, 4)
            if (::network.isInitialized) updateStatus()
            if (::signalBars.isInitialized) {
                signalBars.alpha = if (cellularBarsAvailable) 1f else 0.28f
                signalBars.invalidate()
            }
        }
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            cellularServiceAvailable = info.networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN
            radioType = when {
                info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA || info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> "5G"
                info.networkType == TelephonyManager.NETWORK_TYPE_NR -> "5G"
                info.networkType == TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                info.networkType == TelephonyManager.NETWORK_TYPE_HSPAP || info.networkType == TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                info.networkType == TelephonyManager.NETWORK_TYPE_EDGE || info.networkType == TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "Seluler"
            }
            if (::network.isInitialized) updateStatus()
        }
    }
    private fun startTelephony() {
        if (telephonyListening) return
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val flags = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or (if (Build.VERSION.SDK_INT >= 30) PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED else 0)
            tm.listen(phoneListener, flags)
            telephonyListening = true
        } catch (_: Exception) { cellularBarsAvailable = false }
    }
    private fun stopTelephony() {
        if (!telephonyListening) return
        try {
            (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) {}
        telephonyListening = false
        cellularBarsAvailable = false
    }
    private lateinit var battery: TextView
    private lateinit var speed: TextView
    private lateinit var content: FrameLayout
    private var drawer = false
    private var drawerQuery = ""
    private var panel = false
    private var page = 0
    private var columns = 4
    private var lastBytes = -1L
    private var lastTxBytes = -1L
    private var lastAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var gestureX = 0f
    private var gestureY = 0f
    private var showWallpaper = true
    private var signaturePhoto: Bitmap? = null
    private var iconSize = 48
    private var heartSize = 112
    private var heartOpacity = 100
    private var heartOffsetX = 0
    private var heartOffsetY = 0
    private var showSignature = true
    private var twoFingerPanel = true
    private var touchPointerCount = 0
    private var gestureStartedInWorkspace = false
    private val hiddenApps = linkedSetOf<String>()
    private val favorites = linkedSetOf<String>()
    private val drawerOrder = mutableListOf<String>()
    private val dockApps = mutableListOf<String>()
    private var dockCapacity = 4
    private var dockIconSize = 54
    private var favoritesInitialized = false
    private val folders = linkedMapOf<String, MutableList<String>>()
    private val folderNames = linkedMapOf<String, String>()
    private lateinit var dockBar: LinearLayout
    private val tick = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) {
                clock.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                date.text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())
                updateStatus()
                refreshBattery(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
            }
            handler.postDelayed(this, 1000)
        }
    }
    private fun refreshBattery(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val indicator = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "⚡"
            BatteryManager.BATTERY_STATUS_FULL -> "✓"
            else -> "▰"
        }
        if (::battery.isInitialized) {
            val label = if (percent >= 0) "$indicator $percent%" else "$indicator --"
            if (battery.text.toString() != label) battery.text = label
            battery.contentDescription = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Baterai $percent persen, sedang mengisi"
                BatteryManager.BATTERY_STATUS_FULL -> "Baterai penuh"
                else -> "Baterai $percent persen"
            }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshBattery(intent)
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
    // Small symmetric front-facing rooster illustration, independent of emoji fonts.
    private inner class FrontRooster(context: Context) : View(context) {
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val unit = minOf(width / 40f, height / 58f)
            canvas.save()
            canvas.translate(width / 2f, height / 2f)
            canvas.scale(unit, unit)
            fun oval(color: Int, left: Float, top: Float, right: Float, bottom: Float) {
                brush.color = color
                canvas.drawOval(left, top, right, bottom, brush)
            }
            val gold = 0xffd4af37.toInt()
            val cream = 0xffffe3a0.toInt()
            val red = 0xffe35d4f.toInt()
            oval(gold, -14f, 1f, 14f, 24f) // chest
            oval(cream, -12f, -19f, 12f, 7f) // front-facing head
            oval(red, -4f, -27f, 4f, -17f)
            oval(red, -11f, -23f, -3f, -15f)
            oval(red, 3f, -23f, 11f, -15f)
            oval(0xff27221e.toInt(), -7f, -9f, -4f, -6f)
            oval(0xff27221e.toInt(), 4f, -9f, 7f, -6f)
            val beak = Path().apply {
                moveTo(0f, -5f); lineTo(-5f, 1f); lineTo(5f, 1f); close()
            }
            brush.color = 0xffffa640.toInt()
            canvas.drawPath(beak, brush)
            oval(red, -3f, 1f, 3f, 10f)
            oval(cream, -17f, 7f, -10f, 20f)
            oval(cream, 10f, 7f, 17f, 20f)
            brush.color = gold
            brush.strokeWidth = 2.5f
            canvas.drawLine(-7f, 22f, -7f, 27f, brush)
            canvas.drawLine(7f, 22f, 7f, 27f, brush)
            canvas.restore()
        }
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
        iconSize = getPreferences(MODE_PRIVATE).getInt("icon_size", 48).coerceIn(36, 64)
        heartSize = getPreferences(MODE_PRIVATE).getInt("heart_size", 112).coerceIn(72, 168)
        heartOpacity = getPreferences(MODE_PRIVATE).getInt("heart_opacity", 100).coerceIn(20, 100)
        heartOffsetX = getPreferences(MODE_PRIVATE).getInt("heart_offset_x", 0).coerceIn(-120, 120)
        heartOffsetY = getPreferences(MODE_PRIVATE).getInt("heart_offset_y", 0).coerceIn(-180, 180)
        showSignature = getPreferences(MODE_PRIVATE).getBoolean("show_signature", true)
        twoFingerPanel = getPreferences(MODE_PRIVATE).getBoolean("two_finger_panel", true)
        hiddenApps.addAll(getPreferences(MODE_PRIVATE).getString("hidden_apps", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList())
        drawerOrder.addAll(getPreferences(MODE_PRIVATE).getString("drawer_order", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList())
        dockApps.addAll(getPreferences(MODE_PRIVATE).getString("dock_apps", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList())
        dockCapacity = getPreferences(MODE_PRIVATE).getInt("dock_capacity", 4).coerceIn(4, 6)
        dockIconSize = getPreferences(MODE_PRIVATE).getInt("dock_icon_size", 54).coerceIn(42, 62)
        loadSignaturePhoto()
        loadFolders()
        favoritesInitialized = getPreferences(MODE_PRIVATE).contains("favorites_order")
        getPreferences(MODE_PRIVATE).getString("favorites_order", "")?.split("|")?.filter { it.isNotBlank() }?.forEach { favorites.add(it) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(82))
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
        val clockRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        val roosterLeft = FrontRooster(this).apply { contentDescription = "Ayam menghadap depan kiri" }
        clockRow.addView(roosterLeft, LinearLayout.LayoutParams(dp(38), dp(64)))
        clock = text("", 35f).apply {
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0x132A2514)
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), 0x66D4AF37)
            }
            setPadding(dp(12), 0, dp(12), 0)
        }
        clockRow.addView(clock, LinearLayout.LayoutParams(-2, dp(61)))
        val roosterRight = FrontRooster(this).apply { contentDescription = "Ayam menghadap depan kanan" }
        clockRow.addView(roosterRight, LinearLayout.LayoutParams(dp(38), dp(64)))
        root.addView(clockRow, LinearLayout.LayoutParams(-1, dp(70)))
        date = text("", 13f, muted)
        root.addView(date, LinearLayout.LayoutParams(-1, dp(28)))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val home = navPill("⌂  BERANDA", true)
        home.setOnClickListener { drawer = false; panel = false; render() }
        actions.addView(home, LinearLayout.LayoutParams(0, dp(48), 1f))
        val all = navPill("⊞  APLIKASI", false)
        all.setOnClickListener { drawer = true; panel = false; page = 0; render() }
        actions.addView(all, LinearLayout.LayoutParams(0, dp(48), 1f))
        val controls = navPill("☷  PANEL", false)
        controls.setOnClickListener { panel = !panel; render() }
        actions.addView(controls, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.setPadding(dp(5), dp(3), dp(5), dp(3))
        root.addView(actions, LinearLayout.LayoutParams(-1, dp(57)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = null
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
            touchPointerCount = 1
            gestureX = ev.rawX
            gestureY = ev.rawY
            val bounds = IntArray(2)
            content.getLocationOnScreen(bounds)
            gestureStartedInWorkspace = gestureY >= bounds[1] && gestureY <= bounds[1] + content.height
        }
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) touchPointerCount = maxOf(touchPointerCount, ev.pointerCount)
        if (ev.actionMasked == MotionEvent.ACTION_UP && gestureStartedInWorkspace && touchPointerCount >= 2 && twoFingerPanel && ev.rawY - gestureY > dp(110)) {
            panel = true; render(); touchPointerCount = 0; return true
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP && gestureStartedInWorkspace && touchPointerCount < 2 && !panel) {
            val dx = ev.rawX - gestureX
            val dy = ev.rawY - gestureY
            // Only the workspace handles paging gestures; keep the system bars and dock untouched.
            val inWorkspace = gestureStartedInWorkspace
            if (inWorkspace && kotlin.math.abs(dx) > dp(65) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f) {
                if (drawer) {
                    val matches = apps.count { it.pkg !in hiddenApps && it.name.contains(drawerQuery, true) }
                    val max = ((matches.coerceAtLeast(1) - 1) / (columns * 5))
                    if (dx < 0 && page < max) page++
                    else if (dx > 0 && page > 0) page--
                    else if (dx > 0 && page == 0) { drawer = false; drawerQuery = "" }
                } else {
                    val max = ((favorites.size.coerceAtLeast(1) - 1) / (columns * 4))
                    if (dx < 0 && page < max) page++
                    else if (dx > 0 && page > 0) page--
                    else if (dx < 0 && page == max) { drawer = true; page = 0 }
                }
                render()
                return true
            }
            if (inWorkspace && !drawer && dy < -dp(100) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                drawer = true; page = 0; render(); return true
            }
            if (inWorkspace && drawer && dy > dp(120) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                drawer = false; page = 0; render(); return true
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) { touchPointerCount = 0; gestureStartedInWorkspace = false }
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
        apps.sortWith(compareBy<App> { val pos = drawerOrder.indexOf(it.pkg); if (pos < 0) Int.MAX_VALUE else pos }.thenBy { it.name.lowercase(Locale.getDefault()) })
        if (!favoritesInitialized) {
            apps.filter { app -> listOf("camera", "chrome", "whatsapp", "setting", "phone", "message").any { app.name.contains(it, true) || app.pkg.contains(it, true) } }
                .take(8).forEach { favorites.add(it.pkg) }
            saveFavorites()
        }
        if (dockApps.isEmpty() && !getPreferences(MODE_PRIVATE).contains("dock_apps")) {
            listOf("dialer", "messag", "camera", "chrome").forEach { key ->
                apps.firstOrNull { it.pkg.contains(key, true) || it.name.contains(key, true) }?.let { app ->
                    if (app.pkg !in dockApps) dockApps.add(app.pkg)
                }
            }
            saveDock()
        }
        if (::dockBar.isInitialized) buildDock(dockBar)
    }
    private fun loadFolders() {
        try {
            val data = JSONObject(getPreferences(MODE_PRIVATE).getString("folder_data", "{}") ?: "{}")
            val keys = data.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val entry = data.getJSONObject(id)
                folderNames[id] = entry.optString("name", "Folder")
                val items = entry.optJSONArray("apps") ?: JSONArray()
                folders[id] = (0 until items.length()).map { items.getString(it) }.toMutableList()
            }
        } catch (_: Exception) { folders.clear(); folderNames.clear() }
    }
    private fun saveFolders() {
        val data = JSONObject()
        folders.forEach { (id, items) ->
            data.put(id, JSONObject().put("name", folderNames[id] ?: "Folder")
                .put("apps", JSONArray(items)))
        }
        getPreferences(MODE_PRIVATE).edit().putString("folder_data", data.toString()).apply()
        saveFavorites()
    }
    private fun moveIntoFolder(source: String, target: String) {
        if (source == target || !favorites.contains(source)) return
        val id = if (target.startsWith("folder:")) target.removePrefix("folder:")
                 else UUID.randomUUID().toString()
        val list = folders.getOrPut(id) { mutableListOf() }
        if (!target.startsWith("folder:")) {
            if (!favorites.contains(target)) return
            list.add(target)
            favorites.remove(target)
            folderNames[id] = "Folder Baru"
            favorites.add("folder:$id")
        }
        if (!list.contains(source)) list.add(source)
        favorites.remove(source)
        saveFolders(); render()
    }
    private fun folderCell(id: String, width: Int, height: Int): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), dp(9), dp(3), dp(5))
            isClickable = true
        }
        val preview = FrameLayout(this).apply {
            background = shape(0xff124441.toInt(), 14, true)
        }
        val mini = GridLayout(this).apply { columnCount = 2 }
        folders[id].orEmpty().take(4).forEach { pkg ->
            apps.firstOrNull { it.pkg == pkg }?.let { app ->
                mini.addView(ImageView(this).apply { setImageDrawable(app.icon) },
                    GridLayout.LayoutParams().apply { this.width = dp(23); this.height = dp(23) })
            }
        }
        preview.addView(mini, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
        cell.addView(preview, LinearLayout.LayoutParams(dp(50), dp(50)))
        cell.addView(text(folderNames[id] ?: "Folder", 11f),
            LinearLayout.LayoutParams(-1, dp(39)).apply { topMargin = dp(5) })
        cell.setOnClickListener { showFolder(id) }
        cell.setOnLongClickListener { showFolderActions(id); true }
        cell.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                DragEvent.ACTION_DRAG_ENTERED -> { cell.alpha = 0.6f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { cell.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    cell.alpha = 1f
                    val source = event.localState as? String
                    if (source != null) moveIntoFolder(source, "folder:$id")
                    true
                }
                else -> true
            }
        }
        cell.layoutParams = GridLayout.LayoutParams().apply { this.width = width; this.height = height }
        return cell
    }
    private fun renameFolder(id: String) {
        val field = EditText(this).apply { setText(folderNames[id] ?: "Folder") }
        AlertDialog.Builder(this).setTitle("Nama folder").setView(field)
            .setPositiveButton("Simpan") { _, _ ->
                folderNames[id] = field.text.toString().trim().ifEmpty { "Folder" }
                saveFolders(); render()
            }.setNegativeButton("Batal", null).show()
    }
    private fun showFolderActions(id: String) {
        val choices = arrayOf("Buka folder", "Ubah nama", "Hapus folder")
        AlertDialog.Builder(this).setTitle(folderNames[id] ?: "Folder")
            .setItems(choices) { _, selected ->
                when (selected) {
                    0 -> showFolder(id)
                    1 -> renameFolder(id)
                    2 -> AlertDialog.Builder(this).setTitle("Hapus folder?")
                        .setMessage("Aplikasi di dalam folder akan dikembalikan ke beranda.")
                        .setPositiveButton("Hapus") { _, _ ->
                            val members = folders.remove(id).orEmpty()
                            folderNames.remove(id)
                            favorites.remove("folder:$id")
                            members.forEach { favorites.add(it) }
                            saveFolders()
                            render()
                        }.setNegativeButton("Batal", null).show()
                }
            }.show()
    }
    private fun showFolder(id: String) {
        val items = folders[id].orEmpty().mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
        val labels = items.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle(folderNames[id] ?: "Folder")
            .setItems(labels) { _, index -> startActivity(items[index].launch) }
            .setPositiveButton("Ubah nama") { _, _ -> renameFolder(id) }
            .setNeutralButton("Kelola") { _, _ ->
                AlertDialog.Builder(this).setTitle("Keluarkan aplikasi")
                    .setItems(labels) { _, index ->
                        val pkg = items[index].pkg
                        folders[id]?.remove(pkg)
                        favorites.add(pkg)
                        if (folders[id].isNullOrEmpty()) {
                            folders.remove(id); folderNames.remove(id); favorites.remove("folder:$id")
                        }
                        saveFolders(); render()
                    }.setNegativeButton("Batal", null).show()
            }.setNegativeButton("Tutup", null).show()
    }
    private fun iconCell(app: App, width: Int, height: Int): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), dp(9), dp(3), dp(5))
            isClickable = true; isFocusable = true
            contentDescription = "Buka ${app.name}"
        }
        cell.addView(ImageView(this).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        val labelRow = FrameLayout(this)
        labelRow.addView(text(app.name, 11f).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, FrameLayout.LayoutParams(-1, -1))
        val options = text("⋮", 17f, aqua).apply {
                contentDescription = "Opsi untuk ${app.name}"
                isClickable = true
                setOnClickListener { appMenu(app) }
            }
        labelRow.addView(options, FrameLayout.LayoutParams(dp(26), dp(39), Gravity.END or Gravity.TOP))
        cell.addView(labelRow, LinearLayout.LayoutParams(-1, dp(39)).apply { topMargin = dp(5) })
        cell.setOnClickListener { try { startActivity(app.launch) } catch (_: Exception) { Toast.makeText(this, "Tidak dapat membuka ${app.name}", Toast.LENGTH_SHORT).show() } }
        cell.setOnLongClickListener {
            if (!panel) {
                val data = ClipData.newPlainText("launcher-app", app.pkg)
                cell.startDragAndDrop(data, View.DragShadowBuilder(cell), app.pkg, 0)
                true
            } else { appMenu(app); true }
        }
        if (!panel) cell.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                DragEvent.ACTION_DRAG_ENTERED -> { cell.alpha = 0.5f; true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { cell.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    cell.alpha = 1f
                    val source = event.localState as? String
                    if (source != null && source != app.pkg) {
                        if (drawer) reorderDrawer(source, app.pkg)
                        else if (favorites.contains(source)) showHomeDropOptions(source, app.pkg)
                    }
                    true
                }
                else -> true
            }
        }
        cell.layoutParams = GridLayout.LayoutParams().apply { this.width = width; this.height = height }
        return cell
    }
    private fun showHomeDropOptions(source: String, target: String) {
        if (source == target || !favorites.contains(source) || !favorites.contains(target)) return
        val sourceName = apps.firstOrNull { it.pkg == source }?.name ?: "Aplikasi"
        val targetName = apps.firstOrNull { it.pkg == target }?.name ?: "Aplikasi"
        AlertDialog.Builder(this).setTitle("Pindahkan " + sourceName)
            .setItems(arrayOf("Pindah sebelum " + targetName, "Pindah setelah " + targetName, "Gabungkan menjadi folder")) { _, choice ->
                if (choice == 2) {
                    moveIntoFolder(source, target)
                } else {
                    val order = favorites.toMutableList()
                    order.remove(source)
                    val index = order.indexOf(target)
                    if (index >= 0) {
                        order.add((index + if (choice == 1) 1 else 0).coerceAtMost(order.size), source)
                        favorites.clear()
                        favorites.addAll(order)
                        saveFavorites()
                        page = 0
                        render()
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }
    private fun reorderDrawer(source: String, target: String) {
        val order = apps.map { it.pkg }.toMutableList()
        if (!order.remove(source)) return
        val index = order.indexOf(target)
        if (index < 0) return
        order.add(index, source)
        drawerOrder.clear(); drawerOrder.addAll(order)
        getPreferences(MODE_PRIVATE).edit().putString("drawer_order", drawerOrder.joinToString("|")).apply()
        apps.sortBy { drawerOrder.indexOf(it.pkg).let { pos -> if (pos < 0) Int.MAX_VALUE else pos } }
        render()
    }
    private fun loadSignaturePhoto() {
        val saved = getPreferences(MODE_PRIVATE).getString("signature_photo", null) ?: return
        try {
            contentResolver.openInputStream(Uri.parse(saved))?.use { stream ->
                signaturePhoto = BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) { signaturePhoto = null }
    }
    private inner class HeartPhoto(context: Context) : View(context) {
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(canvas: Canvas) {
            val bmp = signaturePhoto ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            val heart = Path().apply {
                moveTo(w / 2f, h * 0.92f)
                cubicTo(w * -0.12f, h * 0.48f, w * 0.05f, h * 0.05f, w * 0.28f, h * 0.08f)
                cubicTo(w * 0.43f, h * 0.07f, w * 0.50f, h * 0.23f, w / 2f, h * 0.25f)
                cubicTo(w * 0.60f, h * 0.04f, w * 0.84f, h * 0.03f, w * 0.95f, h * 0.20f)
                cubicTo(w * 1.16f, h * 0.53f, w * 0.72f, h * 0.83f, w / 2f, h * 0.92f)
                close()
            }
            canvas.save()
            canvas.clipPath(heart)
            val scale = maxOf(w / bmp.width, h / bmp.height)
            val drawWidth = bmp.width * scale
            val drawHeight = bmp.height * scale
            val left = (w - drawWidth) / 2f
            val top = (h - drawHeight) / 2f
            brush.color = Color.WHITE
            canvas.drawBitmap(bmp, null, RectF(left, top, left + drawWidth, top + drawHeight), brush)
            canvas.restore()
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = dp(2).toFloat()
            brush.color = aqua
            canvas.drawPath(heart, brush)
            brush.style = Paint.Style.FILL
        }
    }
    private fun chooseSignaturePhoto() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 612)
        } catch (_: Exception) { Toast.makeText(this, "Pemilih foto tidak tersedia", Toast.LENGTH_SHORT).show() }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && (requestCode == 613 || requestCode == 614)) {
            val uri = data?.data ?: return
            try {
                if (requestCode == 613) {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(layoutJson().toString(2)) }
                    Toast.makeText(this, "Backup tersimpan", Toast.LENGTH_SHORT).show()
                } else {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                    val json = JSONObject(raw)
                    if (json.optString("format") != "iing-std-layout-v1") throw IllegalArgumentException("Format tidak cocok")
                    AlertDialog.Builder(this).setTitle("Pulihkan tata letak?")
                        .setMessage("Susunan ikon, folder, dan pengaturan akan diganti. Foto asli tidak dihapus.")
                        .setPositiveButton("Pulihkan") { _, _ ->
                            try { restoreLayout(json) }
                            catch (_: Exception) { Toast.makeText(this, "Backup tidak valid", Toast.LENGTH_SHORT).show() }
                        }.setNegativeButton("Batal", null).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Gagal membaca atau menulis backup", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode == 612 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getPreferences(MODE_PRIVATE).edit().putString("signature_photo", uri.toString()).apply()
                loadSignaturePhoto()
                render()
            } catch (_: Exception) { Toast.makeText(this, "Foto tidak dapat dibaca", Toast.LENGTH_SHORT).show() }
        }
    }
    private fun addSignatureWatermark() {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (signaturePhoto != null) {
            group.addView(HeartPhoto(this).apply { isClickable = false; alpha = heartOpacity / 100f },
                LinearLayout.LayoutParams(dp(heartSize), dp(heartSize)))
        }
        if (showSignature) group.addView(text("IING SUTADI", 35f, aqua).apply {
            setTypeface(Typeface.create("cursive", Typeface.ITALIC))
            alpha = 0.38f
            isClickable = false
            isFocusable = false
        }, LinearLayout.LayoutParams(-1, dp(60)))
        group.translationX = dp(heartOffsetX).toFloat()
        group.translationY = dp(heartOffsetY).toFloat()
        content.addView(group, FrameLayout.LayoutParams(-1, dp(heartSize + if (showSignature) 70 else 0), Gravity.CENTER))
    }
    private fun saveFavorites() {
        favoritesInitialized = true
        getPreferences(MODE_PRIVATE).edit().putString("favorites_order", favorites.joinToString("|")).apply()
    }
    private fun showAddToHomePicker() {
        val available = apps.filter { !favorites.contains(it.pkg) && folders.values.none { members -> it.pkg in members } }
        if (available.isEmpty()) {
            Toast.makeText(this, "Semua aplikasi sudah ada di beranda atau folder", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = BooleanArray(available.size)
        AlertDialog.Builder(this)
            .setTitle("Pilih aplikasi untuk beranda")
            .setMultiChoiceItems(available.map { it.name }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setPositiveButton("Tambahkan") { _, _ ->
                available.forEachIndexed { index, app -> if (selected[index]) favorites.add(app.pkg) }
                saveFavorites()
                Toast.makeText(this, "Aplikasi ditambahkan ke beranda", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    private fun appMenu(app: App) {
        AlertDialog.Builder(this).setTitle(app.name)
            .setItems(arrayOf("Buka", if (favorites.contains(app.pkg)) "Hapus dari beranda" else "Tambahkan ke beranda", "Pindahkan ikon ke awal", "Info aplikasi", "Uninstall", "Buat / masukkan ke folder", if (dockApps.contains(app.pkg)) "Hapus dari dock" else "Tambahkan ke dock")) { _, option ->
                try {
                    when (option) {
                        0 -> startActivity(app.launch)
                        1 -> { if (!favorites.add(app.pkg)) favorites.remove(app.pkg); saveFavorites(); render() }
                        2 -> { favorites.remove(app.pkg); val old = favorites.toList(); favorites.clear(); favorites.add(app.pkg); favorites.addAll(old); saveFavorites(); page = 0; render() }
                        3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                        4 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                        5 -> chooseFolderFor(app)
                        6 -> {
                            if (dockApps.contains(app.pkg)) { dockApps.remove(app.pkg); saveDock(); buildDock(dockBar) }
                            else addToDock(app.pkg)
                        }
                    }
                } catch (_: Exception) { Toast.makeText(this, "Tindakan tidak tersedia", Toast.LENGTH_SHORT).show() }
            }.show()
    }
    private fun chooseFolderFor(app: App) {
        val options = mutableListOf("Buat folder baru")
        options.addAll(folders.keys.map { "Masukkan ke ${folderNames[it] ?: "Folder"}" })
        AlertDialog.Builder(this).setTitle("Kelompokkan ${app.name}")
            .setItems(options.toTypedArray()) { _, index ->
                if (index == 0) {
                    val candidates = apps.filter { it.pkg != app.pkg && it.pkg !in folders.values.flatten() }
                    if (candidates.isEmpty()) {
                        Toast.makeText(this, "Tidak ada aplikasi lain untuk digabung", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this).setTitle("Gabungkan dengan aplikasi")
                            .setItems(candidates.map { it.name }.toTypedArray()) { _, selected ->
                                val other = candidates[selected]
                                val id = UUID.randomUUID().toString()
                                folders[id] = mutableListOf(app.pkg, other.pkg)
                                folderNames[id] = "Folder Baru"
                                favorites.remove(app.pkg); favorites.remove(other.pkg)
                                favorites.add("folder:" + id)
                                saveFolders(); render()
                                renameFolder(id)
                            }.setNegativeButton("Batal", null).show()
                    }
                } else {
                    val id = folders.keys.elementAt(index - 1)
                    folders.forEach { (otherId, members) ->
                        if (otherId != id) members.remove(app.pkg)
                    }
                    val emptyIds = folders.filterValues { it.isEmpty() }.keys.toList()
                    emptyIds.forEach { emptyId ->
                        folders.remove(emptyId)
                        folderNames.remove(emptyId)
                        favorites.remove("folder:" + emptyId)
                    }
                    if (folders[id]?.contains(app.pkg) != true) folders[id]?.add(app.pkg)
                    favorites.remove(app.pkg)
                    saveFolders(); render()
                }
            }.setNegativeButton("Batal", null).show()
    }
    private fun saveDock() {
        getPreferences(MODE_PRIVATE).edit().putString("dock_apps", dockApps.joinToString("|")).apply()
    }
    private fun addToDock(pkg: String) {
        if (pkg in dockApps) return
        if (dockApps.size >= dockCapacity) {
            AlertDialog.Builder(this).setTitle("Ganti aplikasi di dock")
                .setItems(dockApps.map { id -> apps.firstOrNull { it.pkg == id }?.name ?: id }.toTypedArray()) { _, index ->
                    dockApps[index] = pkg
                    saveDock(); buildDock(dockBar)
                }.setNegativeButton("Batal", null).show()
        } else {
            dockApps.add(pkg); saveDock(); buildDock(dockBar)
        }
    }
    private fun chooseDockApp(oldPkg: String?) {
        val available = apps.filter { it.pkg !in dockApps }
        AlertDialog.Builder(this).setTitle("Pilih aplikasi dock")
            .setItems(available.map { it.name }.toTypedArray()) { _, i ->
                if (oldPkg == null) addToDock(available[i].pkg)
                else {
                    val index = dockApps.indexOf(oldPkg)
                    if (index >= 0) dockApps[index] = available[i].pkg
                    saveDock(); buildDock(dockBar)
                }
            }.setNegativeButton("Batal", null).show()
    }
    private fun navPill(label: String, active: Boolean): TextView = text(label, 12f, if (active) aqua else 0xffe9cf8b.toInt()).apply {
        setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(if (active) 0x2832E4C9 else 0x122D2619)
            cornerRadius = dp(25).toFloat()
            setStroke(dp(1), if (active) 0x9932E4C9.toInt() else 0x66D4AF37)
        }
        val pad = dp(4)
        setPadding(pad, pad, pad, pad)
    }
    private fun buildDock(dock: LinearLayout) {
        dock.removeAllViews()
        dock.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                DragEvent.ACTION_DROP -> {
                    val pkg = event.localState as? String
                    if (pkg != null && apps.any { it.pkg == pkg }) addToDock(pkg)
                    true
                }
                else -> true
            }
        }
        dockApps.take(dockCapacity).forEach { pkg ->
            val app = apps.firstOrNull { it.pkg == pkg }
            val image = ImageView(this)
            if (app != null) {
                image.setImageDrawable(app.icon)
                image.contentDescription = "Dock: " + app.name
                image.setOnClickListener { try { startActivity(app.launch) } catch (_: Exception) {} }
                image.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle("Dock: " + app.name)
                        .setItems(arrayOf("Buka", "Ganti aplikasi", "Hapus dari dock", "Info aplikasi")) { _, i ->
                            when (i) {
                                0 -> try { startActivity(app.launch) } catch (_: Exception) {}
                                1 -> chooseDockApp(pkg)
                                2 -> { dockApps.remove(pkg); saveDock(); buildDock(dockBar) }
                                3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)))
                            }
                        }.show()
                    true
                }
            }
            image.background = GradientDrawable().apply {
                setColor(0x16D4AF37)
                cornerRadius = dp(dockIconSize / 2).toFloat()
                setStroke(dp(1), 0xA0D4AF37.toInt())
            }
            image.setPadding(dp(8), dp(8), dp(8), dp(8))
            val slot = FrameLayout(this)
            slot.addView(image, FrameLayout.LayoutParams(dp(dockIconSize), dp(dockIconSize), Gravity.CENTER))
            dock.addView(slot, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        repeat((dockCapacity - dockApps.size).coerceAtLeast(0)) {
            val plus = text("+", 27f, aqua).apply {
                contentDescription = "Tambahkan aplikasi ke dock"
                setOnClickListener { chooseDockApp(null) }
            }
            dock.addView(plus, LinearLayout.LayoutParams(0, dp(64), 1f))
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
        addSignatureWatermark()
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
        val homeItems = favorites.toList()
        val maxPage = ((homeItems.size.coerceAtLeast(1) - 1) / perPage)
        page = page.coerceIn(0, maxPage)
        val width = (resources.displayMetrics.widthPixels - dp(32)) / columns
        homeItems.drop(page * perPage).take(perPage).forEach { key ->
            if (key.startsWith("folder:")) grid.addView(folderCell(key.removePrefix("folder:"), width, dp(106)))
            else apps.firstOrNull { it.pkg == key }?.let { grid.addView(iconCell(it, width, dp(106))) }
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
        addSignatureWatermark()
        val area = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(area, FrameLayout.LayoutParams(-1, -1))
        val search = EditText(this).apply {
            hint = "Cari aplikasi"; setSingleLine(true); textSize = 16f
            setTextColor(Color.WHITE); setHintTextColor(muted)
            setText(drawerQuery)
            setSelection(text.length)
        }
        area.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        val addToHome = text("＋ TAMBAH APLIKASI KE BERANDA", 13f, aqua).apply {
            background = shape(0x1712A99C, 28, true)
            setOnClickListener { showAddToHomePicker() }
        }
        val addRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        addRow.addView(addToHome, LinearLayout.LayoutParams(dp(244), dp(40)))
        val endDot = text("●", 9f, aqua).apply { alpha = 0.55f }
        addRow.addView(endDot, LinearLayout.LayoutParams(dp(19), dp(40)))
        area.addView(addRow, LinearLayout.LayoutParams(-1, dp(46)))
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
            val filtered = apps.filter { it.pkg !in hiddenApps && it.name.contains(search.text.toString(), true) }
            val perPage = columns * 5
            val max = ((filtered.size - 1).coerceAtLeast(0)) / perPage
            page = page.coerceIn(0, max)
            count.text = "${page + 1} / ${max + 1}  •  ${columns} KOLOM"
            val width = (resources.displayMetrics.widthPixels - dp(32)) / columns
            filtered.drop(page * perPage).take(perPage).forEach { grid.addView(iconCell(it, width, dp(114))) }
        }
        prev.setOnClickListener { page--; redraw() }
        next.setOnClickListener { page++; redraw() }
        layout.setOnClickListener { columns = if (columns == 4) 5 else if (columns == 5) 6 else 4; getPreferences(MODE_PRIVATE).edit().putInt("columns", columns).apply(); grid.columnCount = columns; page = 0; redraw() }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { drawerQuery = s?.toString() ?: ""; page = 0; redraw() }
            override fun afterTextChanged(s: Editable?) {}
        })
        /* Horizontal paging is handled at Activity dispatch level, including over app icons.
           Do not attach a second competing touch listener to the scroll container. */
        /* scroll.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) { startX = e.x; return false }
                if (e.action == MotionEvent.ACTION_UP && kotlin.math.abs(e.x - startX) > dp(60)) {
                    page += if (e.x < startX) 1 else -1
                    redraw(); return true
                }
                return false
            }
        })
        */
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
        val dockSetting = text("DOCK   $dockCapacity APLIKASI  •  UBAH", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener {
                dockCapacity = if (dockCapacity == 6) 4 else dockCapacity + 1
                getPreferences(MODE_PRIVATE).edit().putInt("dock_capacity", dockCapacity).apply()
                buildDock(dockBar); render()
            }
        }
        area.addView(dockSetting, LinearLayout.LayoutParams(-1, dp(60)))
        val dockSizeSetting = text("UKURAN IKON DOCK  •  ${dockIconSize}dp", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener {
                dockIconSize = if (dockIconSize >= 62) 42 else dockIconSize + 4
                getPreferences(MODE_PRIVATE).edit().putInt("dock_icon_size", dockIconSize).apply()
                buildDock(dockBar)
                render()
            }
        }
        area.addView(dockSizeSetting, LinearLayout.LayoutParams(-1, dp(58)))
        val signatureSetting = text("TANDA TANGAN  •  " + if (showSignature) "TAMPIL" else "SEMBUNYI", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener {
                showSignature = !showSignature
                getPreferences(MODE_PRIVATE).edit().putBoolean("show_signature", showSignature).apply()
                render()
            }
        }
        area.addView(signatureSetting, LinearLayout.LayoutParams(-1, dp(58)))
        val heartPosition = text("POSISI FOTO HATI  •  ATUR", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener {
                val choices = arrayOf("Naik 20dp", "Turun 20dp", "Geser kiri 20dp", "Geser kanan 20dp", "Kembali ke tengah")
                AlertDialog.Builder(this@MainActivity).setTitle("Posisi foto hati").setItems(choices) { _, choice ->
                    when (choice) {
                        0 -> heartOffsetY = (heartOffsetY - 20).coerceIn(-180, 180)
                        1 -> heartOffsetY = (heartOffsetY + 20).coerceIn(-180, 180)
                        2 -> heartOffsetX = (heartOffsetX - 20).coerceIn(-120, 120)
                        3 -> heartOffsetX = (heartOffsetX + 20).coerceIn(-120, 120)
                        else -> { heartOffsetX = 0; heartOffsetY = 0 }
                    }
                    getPreferences(MODE_PRIVATE).edit().putInt("heart_offset_x", heartOffsetX).putInt("heart_offset_y", heartOffsetY).apply()
                    render()
                }.setNegativeButton("Batal", null).show()
            }
        }
        area.addView(heartPosition, LinearLayout.LayoutParams(-1, dp(58)))
        val wallpaperSetting = text("WALLPAPER HP  •  " + if (showWallpaper) "AKTIF" else "NONAKTIF", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener {
                showWallpaper = !showWallpaper
                getPreferences(MODE_PRIVATE).edit().putBoolean("wallpaper", showWallpaper).apply()
                applyWallpaper(); render()
            }
        }
        area.addView(wallpaperSetting, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(10) })
        val photoSetting = text("FOTO HATI IING SUTADI  •  PILIH FOTO", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener { chooseSignaturePhoto() }
        }
        area.addView(photoSetting, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(10) })
        fun option(label: String, callback: () -> Unit) {
            area.addView(text(label, 14f, aqua).apply {
                background = shape(0xff124441.toInt(), 18, true)
                setOnClickListener { callback() }
            }, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(8) })
        }
        option("UKURAN IKON  •  ${iconSize}dp") {
            val sizes = intArrayOf(36, 42, 48, 54, 60, 64)
            AlertDialog.Builder(this).setTitle("Ukuran ikon")
                .setSingleChoiceItems(sizes.map { "${it}dp" }.toTypedArray(), sizes.indexOf(iconSize)) { dialog, i ->
                    iconSize = sizes[i]
                    getPreferences(MODE_PRIVATE).edit().putInt("icon_size", iconSize).apply()
                    dialog.dismiss(); render()
                }.setNegativeButton("Batal", null).show()
        }
        option("SEMBUNYIKAN / TAMPILKAN APLIKASI") { manageHiddenApps() }
        option("UKURAN FOTO HATI  •  ${heartSize}dp") {
            val sizes = intArrayOf(72, 88, 112, 136, 168)
            AlertDialog.Builder(this).setTitle("Ukuran foto hati")
                .setSingleChoiceItems(sizes.map { "${it}dp" }.toTypedArray(), sizes.indexOf(heartSize)) { dialog, i ->
                    heartSize = sizes[i]
                    getPreferences(MODE_PRIVATE).edit().putInt("heart_size", heartSize).apply()
                    dialog.dismiss(); render()
                }.setNegativeButton("Batal", null).show()
        }
        option("OPASITAS FOTO HATI  •  ${heartOpacity}%") {
            val levels = intArrayOf(20, 40, 60, 80, 100)
            AlertDialog.Builder(this).setTitle("Transparansi foto hati")
                .setSingleChoiceItems(levels.map { "${it}%" }.toTypedArray(), levels.indexOf(heartOpacity)) { dialog, i ->
                    heartOpacity = levels[i]
                    getPreferences(MODE_PRIVATE).edit().putInt("heart_opacity", heartOpacity).apply()
                    dialog.dismiss(); render()
                }.setNegativeButton("Batal", null).show()
        }
        option("GESTUR DUA JARI TURUN  •  " + if (twoFingerPanel) "AKTIF" else "NONAKTIF") {
            twoFingerPanel = !twoFingerPanel
            getPreferences(MODE_PRIVATE).edit().putBoolean("two_finger_panel", twoFingerPanel).putInt("dock_capacity", dockCapacity).apply()
            render()
        }
        option("BACKUP TATA LETAK  •  EKSPOR") { exportLayout() }
        option("PULIHKAN TATA LETAK  •  IMPOR") { importLayout() }
        val defaultSetting = text("JADIKAN LAUNCHER UTAMA  ⌂", 15f, aqua).apply {
            background = shape(0xff124441.toInt(), 18, true)
            setOnClickListener { requestDefaultHome() }
        }
        area.addView(defaultSetting, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(10) })
        area.addView(text("Panel ini membuka pengaturan Android; tidak mengganti Quick Settings sistem.", 12f, muted),
            LinearLayout.LayoutParams(-1, dp(70)))
    }
    private fun manageHiddenApps() {
        val choices = apps.toList()
        val checked = BooleanArray(choices.size) { choices[it].pkg in hiddenApps }
        AlertDialog.Builder(this).setTitle("Sembunyikan dari daftar aplikasi")
            .setMultiChoiceItems(choices.map { it.name }.toTypedArray(), checked) { _, i, value -> checked[i] = value }
            .setPositiveButton("Simpan") { _, _ ->
                hiddenApps.clear()
                choices.forEachIndexed { i, app -> if (checked[i]) hiddenApps.add(app.pkg) }
                getPreferences(MODE_PRIVATE).edit().putString("hidden_apps", hiddenApps.joinToString("|")).apply()
                page = 0; render()
            }.setNegativeButton("Batal", null).show()
    }
    private fun layoutJson(): JSONObject {
        val prefs = getPreferences(MODE_PRIVATE)
        return JSONObject().apply {
            put("format", "iing-std-layout-v1")
            listOf("favorites_order", "drawer_order", "folder_data", "hidden_apps", "dock_apps").forEach {
                put(it, prefs.getString(it, if (it == "folder_data") "{}" else "") ?: "")
            }
            put("columns", columns)
            put("icon_size", iconSize)
            put("heart_size", heartSize)
            put("heart_opacity", heartOpacity)
            put("heart_offset_x", heartOffsetX)
            put("heart_offset_y", heartOffsetY)
            put("show_signature", showSignature)
            put("wallpaper", showWallpaper)
            put("two_finger_panel", twoFingerPanel)
            put("dock_capacity", dockCapacity)
            put("dock_icon_size", dockIconSize)
        }
    }
    private fun exportLayout() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "IING-STD-Launcher-Backup.json")
        }, 613)
    }
    private fun importLayout() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, 614)
    }
    private fun restoreLayout(data: JSONObject) {
        if (data.optString("format") != "iing-std-layout-v1") throw IllegalArgumentException("Format tidak cocok")
        val prefs = getPreferences(MODE_PRIVATE).edit()
        listOf("favorites_order", "drawer_order", "folder_data", "hidden_apps", "dock_apps").forEach {
            prefs.putString(it, data.optString(it, if (it == "folder_data") "{}" else ""))
        }
        columns = data.optInt("columns", 4).coerceIn(4, 6)
        iconSize = data.optInt("icon_size", 48).coerceIn(36, 64)
        heartSize = data.optInt("heart_size", 112).coerceIn(72, 168)
        heartOpacity = data.optInt("heart_opacity", 100).coerceIn(20, 100)
        heartOffsetX = data.optInt("heart_offset_x", 0).coerceIn(-120, 120)
        heartOffsetY = data.optInt("heart_offset_y", 0).coerceIn(-180, 180)
        showSignature = data.optBoolean("show_signature", true)
        showWallpaper = data.optBoolean("wallpaper", true)
        twoFingerPanel = data.optBoolean("two_finger_panel", true)
        dockCapacity = data.optInt("dock_capacity", 4).coerceIn(4, 6)
        dockIconSize = data.optInt("dock_icon_size", 54).coerceIn(42, 62)
        prefs.putInt("columns", columns).putInt("icon_size", iconSize)
            .putInt("heart_size", heartSize).putInt("heart_opacity", heartOpacity)
            .putInt("heart_offset_x", heartOffsetX).putInt("heart_offset_y", heartOffsetY).putBoolean("show_signature", showSignature)
            .putBoolean("wallpaper", showWallpaper).putBoolean("two_finger_panel", twoFingerPanel)
            .putInt("dock_capacity", dockCapacity).putInt("dock_icon_size", dockIconSize).apply()
        favorites.clear()
        data.optString("favorites_order").split("|").filter { it.isNotBlank() }.forEach { favorites.add(it) }
        favoritesInitialized = true
        drawerOrder.clear()
        data.optString("drawer_order").split("|").filter { it.isNotBlank() }.forEach { drawerOrder.add(it) }
        hiddenApps.clear()
        data.optString("hidden_apps").split("|").filter { it.isNotBlank() }.forEach { hiddenApps.add(it) }
        dockApps.clear()
        data.optString("dock_apps").split("|").filter { it.isNotBlank() }.take(dockCapacity).forEach { dockApps.add(it) }
        folders.clear(); folderNames.clear(); loadFolders()
        loadApps(); applyWallpaper(); page = 0; render()
    }
    private fun cellularLabel(): String = radioType
    private fun updateStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork)
        val connected = cap != null
        val internet = when {
            cap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            cap?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Data seluler"
            cap == null -> "Offline"
            else -> "Internet"
        }
        val mobile = if (cellularServiceAvailable || radioType != "Seluler") radioType else "Seluler"
        val statusLabel = "$mobile • $internet"
        if (network.text.toString() != statusLabel) network.text = statusLabel
        signalBars.alpha = if (cellularBarsAvailable) 1f else 0.28f
        signalBars.contentDescription = if (cellularBarsAvailable) "Kekuatan sinyal seluler $signalLevel dari 4" else "Kekuatan sinyal seluler tidak tersedia"
        signalBars.invalidate()
        val now = SystemClock.elapsedRealtime()
        val bytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()
        if (lastAt > 0 && bytes >= lastBytes && bytes >= 0) {
            val rate = (bytes - lastBytes) * 1000 / (now - lastAt).coerceAtLeast(1)
            val up = if (lastTxBytes >= 0 && txBytes >= lastTxBytes) (txBytes - lastTxBytes) * 1000 / (now - lastAt).coerceAtLeast(1) else 0L
            speed.text = "↓ ${rate / 1024}K  ↑ ${up / 1024}K"
        }
        lastBytes = bytes; lastTxBytes = txBytes; lastAt = now
    }
    override fun onStart() {
        super.onStart()
        val latestBattery = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        refreshBattery(latestBattery)
        startTelephony()
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("phone_permission_asked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("phone_permission_asked", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.READ_PHONE_STATE), 717)
        }
        if (::content.isInitialized) { loadApps(); render() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 717 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startTelephony()
    }
    override fun onStop() { stopTelephony(); unregisterReceiver(receiver); super.onStop() }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panel || drawer) { panel = false; drawer = false; render() }
        else super.onBackPressed()
    }
}
