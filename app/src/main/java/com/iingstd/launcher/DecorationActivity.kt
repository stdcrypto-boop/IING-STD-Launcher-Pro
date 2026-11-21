package com.iingstd.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class DecorationActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xff211126.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "IING & ADELIA  🩷"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xffffb4d8.toInt())
        })
        layout.addView(TextView(this).apply {
            text = "Hiasan kecil 150 dp di tengah layar. Wallpaper asli tetap aman. Hiasan ini adalah lapisan di atas aplikasi, bukan bagian wallpaper."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(-1)
            setPadding(0, dp(22), 0, dp(24))
        })
        layout.addView(Button(this).apply {
            text = "AKTIFKAN"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@DecorationActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this@DecorationActivity, "Izinkan tampil di atas aplikasi, lalu kembali dan tekan AKTIFKAN", Toast.LENGTH_LONG).show()
                } else {
                    val service = Intent(this@DecorationActivity, DecorationService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                    Toast.makeText(this@DecorationActivity, "Hiasan aktif", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        layout.addView(Button(this).apply {
            text = "NONAKTIFKAN"
            setOnClickListener { stopService(Intent(this@DecorationActivity, DecorationService::class.java)) }
        })
        layout.addView(Button(this).apply {
            text = "Pilih foto (opsional)"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, 612)
            }
        })
        setContentView(layout)
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 612 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putString("signature_photo", uri.toString()).apply()
                stopService(Intent(this, DecorationService::class.java))
                if (Settings.canDrawOverlays(this)) {
                    val service = Intent(this, DecorationService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Gagal membaca foto", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
