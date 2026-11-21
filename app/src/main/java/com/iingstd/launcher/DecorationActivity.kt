package com.iingstd.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class DecorationActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        layout.addView(TextView(this).apply {
            text = "IING STD • Dekorasi Beranda"
            textSize = 23f
        })
        layout.addView(TextView(this).apply {
            text = "Launcher bawaan tetap digunakan. Foto hati dan tanda tangan muncul sebagai lapisan transparan yang tidak menangkap sentuhan. Pada versi percobaan, dekorasi juga bisa terlihat di aplikasi lain. Matikan kapan saja di sini atau melalui notifikasi."
            textSize = 15f
            setPadding(0, dp(14), 0, dp(18))
        })
        layout.addView(Button(this).apply {
            text = "Pilih foto hati"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, 612)
            }
        })
        layout.addView(Button(this).apply {
            text = "Tampilkan dekorasi"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@DecorationActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    val intent = Intent(this@DecorationActivity, DecorationService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                }
            }
        })
        layout.addView(Button(this).apply {
            text = "Sembunyikan dekorasi"
            setOnClickListener { stopService(Intent(this@DecorationActivity, DecorationService::class.java)) }
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
                if (Settings.canDrawOverlays(this)) {
                    stopService(Intent(this, DecorationService::class.java))
                    val service = Intent(this, DecorationService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                }
            } catch (_: Exception) { }
        }
    }
}
