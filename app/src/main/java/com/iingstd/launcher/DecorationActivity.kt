package com.iingstd.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.IOException

class DecorationActivity : Activity() {
    private var photo: Bitmap? = null
    private lateinit var preview: ImageView
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stop the older global overlay if an earlier version left it running.
        stopService(Intent(this, DecorationService::class.java))
        val uri = getSharedPreferences("MainActivity", MODE_PRIVATE).getString("signature_photo", null)
        if (uri != null) photo = try { contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(0xff101b20.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "IING STD • Wallpaper Hati"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(52)))
        layout.addView(TextView(this).apply {
            text = "Hati merah jambu dan tanda tangan menyatu dengan wallpaper beranda, di belakang ikon. Foto yang pernah dipilih tetap tersimpan. Wallpaper lama diganti hanya setelah menekan Pasang."
            setTextColor(0xffffc5e1.toInt())
            textSize = 14f
        })
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
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
            text = "Pasang sebagai wallpaper beranda"
            setOnClickListener {
                try {
                    val manager = WallpaperManager.getInstance(this@DecorationActivity)
                    val metrics = resources.displayMetrics
                    val bitmap = createWallpaper(metrics.widthPixels.coerceAtLeast(720), metrics.heightPixels.coerceAtLeast(1280))
                    manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    bitmap.recycle()
                    Toast.makeText(this@DecorationActivity, "Wallpaper beranda terpasang", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@DecorationActivity, "Gagal memasang wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(layout)
        refreshPreview()
    }
    private fun refreshPreview() {
        val metrics = resources.displayMetrics
        preview.setImageBitmap(createWallpaper(metrics.widthPixels.coerceAtLeast(540) / 2, metrics.heightPixels.coerceAtLeast(960) / 2))
    }
    private fun createWallpaper(w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xff190d1c.toInt(), 0xff45203c.toInt(), 0xff170c20.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val size = w * .39f
        val x = (w-size)/2f
        val y = h*.43f-size*.5f
        run {
            val bmp = photo
            val heart = Path().apply {
                moveTo(x+size*.5f, y+size*.93f)
                cubicTo(x-size*.12f, y+size*.47f, x+size*.05f, y+size*.05f, x+size*.28f, y+size*.08f)
                cubicTo(x+size*.43f, y+size*.07f, x+size*.5f, y+size*.23f, x+size*.5f, y+size*.25f)
                cubicTo(x+size*.6f, y+size*.04f, x+size*.84f, y+size*.03f, x+size*.95f, y+size*.2f)
                cubicTo(x+size*1.16f, y+size*.53f, x+size*.72f, y+size*.83f, x+size*.5f, y+size*.93f)
                close()
            }
            if (bmp != null) {
                canvas.save()
                canvas.clipPath(heart)
                val scale = maxOf(size / bmp.width, size / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                canvas.drawBitmap(bmp, null,
                    RectF(x + (size-dw)/2, y + (size-dh)/2, x + (size+dw)/2, y + (size+dh)/2), p)
                canvas.restore()
            } else {
                p.color = 0xfff062a6.toInt()
                canvas.drawPath(heart, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = w*.012f; p.color = 0xffff80c0.toInt()
            canvas.drawPath(heart,p); p.style = Paint.Style.FILL
        }
        p.color = 0xffffc5e1.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create("cursive", Typeface.ITALIC)
        p.textSize = w*.087f
        canvas.drawText("IING SUTADI", w/2f, y+size+ h*.055f, p)
        return out
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 612 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putString("signature_photo", uri.toString()).apply()
                photo = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                refreshPreview()
            } catch (e: Exception) {
                Toast.makeText(this, "Foto tidak dapat dibaca", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
