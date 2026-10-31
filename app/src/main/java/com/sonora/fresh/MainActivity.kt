package com.sonora.fresh

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.SweepGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.widget.Toast
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.media.MediaPlayer
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val background = Color.rgb(2, 6, 12)
    private val accent = Color.rgb(0, 209, 255)
    private val muted = Color.rgb(154, 173, 189)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var gifUri: Uri? = null
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var library: LinearLayout
    private lateinit var player: LinearLayout
    private lateinit var gif: ImageView
    private lateinit var video: VideoView
    private lateinit var mediaBox: FrameLayout
    private lateinit var settings: ScrollView
    private lateinit var settingsTab: TextView
    private var animationMode = 1
    private var carMode = false
    private lateinit var gifArea: LinearLayout
    private lateinit var gifChoose: TextView
    private lateinit var details: LinearLayout
    private lateinit var displayPanel: LinearLayout
    private lateinit var glass: FrameLayout
    private lateinit var controlPanel: LinearLayout
    private lateinit var trackName: TextView
    private lateinit var trackArtist: TextView
    private lateinit var playButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var repeatButton: TextView
    private lateinit var elapsed: TextView
    private lateinit var total: TextView
    private lateinit var seek: SeekBar
    private lateinit var trackList: LinearLayout
    private lateinit var spectrum: View
    private lateinit var search: EditText
    private lateinit var header: TextView
    private lateinit var nav: LinearLayout
    private lateinit var controlsRow: LinearLayout
    private lateinit var volumeRow: LinearLayout
    private lateinit var volume: SeekBar
    private lateinit var audio: AudioManager
    private var volumeDragging = false
    private var smallSize = 46
    private var bigSize = 68
    private val steel = 0xff74808d.toInt()
    private class Song(val id: String, val title: String, val uri: Uri, val durationMs: Long, val format: String, val folder: String, val added: Long, val artist: String, val name: String)
    private class Row(val song: Song?, val folder: String?, val count: Int)
    private class RowHolder(val root: LinearLayout, val icon: TextView, val title: TextView, val sub: TextView)
    private val allSongs = mutableListOf<Song>()
    private val spacers = mutableListOf<View>()
    private var restored = false
    private val animButtons = mutableMapOf<Int, TextView>()
    private lateinit var carToggle: TextView
    private val favorites = mutableSetOf<String>()
    private val chipModes = listOf(0, 3, 1, 2)
    private val iconSearch = 0
    private val iconDots = 1
    private val iconHeart = 2
    private val iconSpeakerLow = 3
    private val iconSpeakerHigh = 4
    private val iconEq = 5
    private lateinit var eqScreen: ScrollView
    private lateinit var eqView: EqView
    private lateinit var eqOnButton: TextView
    private lateinit var eqBassLabel: TextView
    private lateinit var eqBassSeek: SeekBar
    private lateinit var eqPresetButtons: List<TextView>
    private val eqPresetNames = listOf("Плоский", "Бас", "Вокал", "Рок", "Поп", "Высокие")
    private val eqPresets = listOf(
        floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        floatArrayOf(6f, 5f, 4f, 2f, 1f, 0f, 0f, 0f, 0f, 0f),
        floatArrayOf(-2f, -1f, 0f, 1f, 3f, 4f, 4f, 3f, 1f, 0f),
        floatArrayOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 4f, 4f),
        floatArrayOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, -2f),
        floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)
    )
    private lateinit var headerBar: LinearLayout
    private lateinit var heart: IconView
    private val rows = mutableListOf<Row>()
    private var modeSongs: List<Song> = emptyList()
    private var filterMode = 0
    private var selectedFolder: String? = null
    private lateinit var libraryAdapter: LibraryAdapter
    private lateinit var actionsRow: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var chips: List<TextView>
    private lateinit var miniBar: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniSub: TextView
    private lateinit var miniPlay: TextView
    private lateinit var playerTab: TextView
    private lateinit var libraryTab: TextView
    private val songTitles = mutableListOf<String>()
    private val handler = Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            controller?.let { p ->
                val duration = p.duration.takeIf { it > 0 } ?: 0L
                if (duration > 0) seek.progress = (p.currentPosition.coerceAtLeast(0) * 1000L / duration).toInt().coerceIn(0, 1000)
                elapsed.text = formatTime(p.currentPosition)
                total.text = formatTime(duration)
            }
            if (!volumeDragging && ::volume.isInitialized) volume.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            handler.postDelayed(this, 500)
        }
    }
    private val gifPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            gifUri = uri
            animationMode = 0
            getPreferences(MODE_PRIVATE).edit().putString("gif_uri", uri.toString()).putInt("animation_mode", 0).apply()
            displayAnimation()
        }
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanDeviceMusic() else toast("Разрешите доступ к музыке в настройках Android")
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun requestMusicScan() {
        getPreferences(MODE_PRIVATE).edit().putString("source", "device").apply()
        val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
                         else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            scanDeviceMusic()
        } else audioPermission.launch(permission)
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            getPreferences(MODE_PRIVATE).edit().putString("folder", uri.toString()).putString("source", "folder").apply()
            scanFolder(uri)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    // Brushed-metal look built from gradients (no bitmap assets).
    private val metalColors = intArrayOf(0xff3b444e.toInt(), 0xff171d24.toInt(), 0xff252d36.toInt(), 0xff0e1318.toInt())
    private fun panel(radius: Float = 12f, stroke: Int = steel): Drawable = MetalDrawable(radius, false, stroke)
    private fun darkPanel(radius: Float = 14f, stroke: Int = steel): Drawable = MetalDrawable(radius, true, stroke)
    private fun activePanel(radius: Float = 12f): Drawable = ActiveDrawable(radius)
    private fun circle(ring: Int = steel, ringWidth: Float = 2f): Drawable =
        if (ring == accent) ChromeDrawable(true, maxOf(ringWidth, 2.5f) + 1f, false) else ChromeDrawable(false, 3.4f, false)

    // ---------- brushed metal / chrome, drawn in code (no image assets) ----------
    private val brushed: Bitmap by lazy {
        val w = 256
        val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val rnd = java.util.Random(7)
        val paint = Paint()
        for (y in 0 until h) { // fine horizontal brush lines
            paint.color = if (rnd.nextBoolean()) Color.argb(rnd.nextInt(30), 255, 255, 255) else Color.argb(rnd.nextInt(30), 0, 0, 0)
            cv.drawRect(0f, y.toFloat(), w.toFloat(), y + 1f, paint)
        }
        repeat(320) { // short scratches
            val y = rnd.nextInt(h).toFloat()
            val x = rnd.nextInt(w).toFloat()
            val len = 8f + rnd.nextInt(70)
            paint.color = if (rnd.nextBoolean()) Color.argb(8 + rnd.nextInt(26), 255, 255, 255) else Color.argb(8 + rnd.nextInt(26), 0, 0, 0)
            cv.drawRect(x, y, minOf(x + len, w.toFloat()), y + 1f, paint)
        }
        bmp
    }

    // Slider track: dark groove + cyan progress with a soft glow (like the mockup).
    private fun sliderTrack(): Drawable {
        val bg = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(2f); setColor(0xFF27313A.toInt()) }
        val core = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF0090C8.toInt(), 0xFF2BE0FF.toInt()))
            .apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(2f) }
        val halo = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(5f); setColor(0x3300D1FF) }
        val progress = android.graphics.drawable.LayerDrawable(arrayOf(halo, core)).apply {
            setLayerHeight(0, dp(10)); setLayerGravity(0, Gravity.CENTER_VERTICAL)
            setLayerHeight(1, dp(4)); setLayerGravity(1, Gravity.CENTER_VERTICAL)
        }
        val clip = android.graphics.drawable.ClipDrawable(progress, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
        return android.graphics.drawable.LayerDrawable(arrayOf(bg, clip)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            setLayerHeight(0, dp(4)); setLayerGravity(0, Gravity.CENTER_VERTICAL)
            setLayerHeight(1, dp(10)); setLayerGravity(1, Gravity.CENTER_VERTICAL)
        }
    }
    // Panel: steel gradient + brushed texture + top bevel + chrome edge.
    private inner class MetalDrawable(private val radiusDp: Float, private val dark: Boolean, private val strokeColor: Int) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tex = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val inner = RectF()
        private var radius = 0f
        override fun onBoundsChange(b: android.graphics.Rect) {
            val sw = dp(1.5f)
            rect.set(b.left + sw / 2f, b.top + sw / 2f, b.right - sw / 2f, b.bottom - sw / 2f)
            radius = minOf(dp(radiusDp), rect.height() / 2f, rect.width() / 2f)
            val g = dp(1.5f)
            inner.set(rect.left + g, rect.top + g, rect.right - g, rect.bottom - g)
            val top = b.top.toFloat()
            val bot = b.bottom.toFloat()
            fill.shader = LinearGradient(0f, top, 0f, bot,
                if (dark) intArrayOf(0xFF1A242E.toInt(), 0xFF0D141B.toInt(), 0xFF070B10.toInt())
                else intArrayOf(0xFF66717D.toInt(), 0xFF2C353E.toInt(), 0xFF424D58.toInt(), 0xFF1A2128.toInt()),
                null, Shader.TileMode.CLAMP)
            tex.shader = BitmapShader(brushed, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            tex.alpha = if (dark) 100 else 170
            bevel.strokeWidth = dp(1f)
            bevel.shader = LinearGradient(0f, top, 0f, bot, 0x7AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            edge.strokeWidth = sw
            if (strokeColor == steel) {
                edge.shader = LinearGradient(b.left.toFloat(), top, b.right.toFloat(), bot,
                    intArrayOf(0xFFE0E7EE.toInt(), 0xFF6A7682.toInt(), 0xFFC2CCD6.toInt(), 0xFF535E69.toInt()), null, Shader.TileMode.CLAMP)
            } else {
                edge.shader = null
                edge.color = strokeColor
            }
        }
        override fun draw(c: Canvas) {
            if (rect.width() <= 0f) return
            c.drawRoundRect(rect, radius, radius, fill)
            c.drawRoundRect(rect, radius, radius, tex)
            val ir = maxOf(0f, radius - dp(1.5f))
            c.drawRoundRect(inner, ir, ir, bevel)
            c.drawRoundRect(rect, radius, radius, edge)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // Selected item: dark face, soft cyan inner glow and a crisp cyan edge.
    private inner class ActiveDrawable(private val radiusDp: Float) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private var radius = 0f
        override fun onBoundsChange(b: android.graphics.Rect) {
            val sw = dp(2f)
            rect.set(b.left + sw / 2f, b.top + sw / 2f, b.right - sw / 2f, b.bottom - sw / 2f)
            radius = minOf(dp(radiusDp), rect.height() / 2f, rect.width() / 2f)
            fill.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), 0xFF0B1E2C.toInt(), 0xFF040C13.toInt(), Shader.TileMode.CLAMP)
            edge.strokeWidth = sw
            edge.color = accent
        }
        override fun draw(c: Canvas) {
            if (rect.width() <= 0f) return
            c.drawRoundRect(rect, radius, radius, fill)
            val steps = listOf(dp(9f) to 0x14, dp(6f) to 0x22, dp(3.5f) to 0x3C)
            for ((w, a) in steps) {
                glow.strokeWidth = w
                glow.color = Color.argb(a, 0, 209, 255)
                c.drawRoundRect(rect, radius, radius, glow)
            }
            c.drawRoundRect(rect, radius, radius, edge)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // Recessed window the visualizer lives in: dark smoky back, inner shadow at the top, light lip at the bottom.
    private inner class GlassFrameDrawable : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val inner = RectF()
        private var radius = 0f
        override fun onBoundsChange(b: android.graphics.Rect) {
            rect.set(b.left + dp(1f), b.top + dp(1f), b.right - dp(1f), b.bottom - dp(1f))
            inner.set(rect.left + dp(1.5f), rect.top + dp(1.5f), rect.right - dp(1.5f), rect.bottom - dp(1.5f))
            radius = minOf(dp(10f), rect.height() / 2f, rect.width() / 2f)
            val top = b.top.toFloat()
            val bot = b.bottom.toFloat()
            fill.shader = LinearGradient(0f, top, 0f, bot,
                intArrayOf(0xFF02070B.toInt(), 0xFF071420.toInt(), 0xFF04101A.toInt()), null, Shader.TileMode.CLAMP)
            shade.strokeWidth = dp(3f)
            shade.shader = LinearGradient(0f, top, 0f, top + dp(14f), 0xCC000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            lip.strokeWidth = dp(1f)
            lip.shader = LinearGradient(0f, bot - dp(10f), 0f, bot, 0x00FFFFFF, 0x40FFFFFF, Shader.TileMode.CLAMP)
            edge.strokeWidth = dp(1f)
            edge.color = 0xFF2A3640.toInt()
        }
        override fun draw(c: Canvas) {
            if (rect.width() <= 0f) return
            c.drawRoundRect(rect, radius, radius, fill)
            c.drawRoundRect(inner, radius, radius, shade)
            c.drawRoundRect(inner, radius, radius, lip)
            c.drawRoundRect(rect, radius, radius, edge)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // Fine horizontal scanlines for the glass (a 2x6 px tile, repeated).
    private val scanBitmap: Bitmap by lazy {
        val bmp = Bitmap.createBitmap(2, 6, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.argb(46, 0, 0, 0))
        bmp.setPixel(1, 0, Color.argb(46, 0, 0, 0))
        bmp
    }

    // Smoked-glass cover drawn OVER the visualizer: dark tint, scanlines, soft diagonal reflection, vignette.
    private inner class GlassDrawable : Drawable() {
        private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lines = Paint().apply { shader = BitmapShader(scanBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
        private val vig = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val clip = android.graphics.Path()
        private val band = android.graphics.Path()
        private val thin = android.graphics.Path()
        private val rect = RectF()
        private var radius = 0f
        override fun onBoundsChange(b: android.graphics.Rect) {
            rect.set(b.left + dp(1f), b.top + dp(1f), b.right - dp(1f), b.bottom - dp(1f))
            radius = minOf(dp(10f), rect.height() / 2f, rect.width() / 2f)
            clip.reset()
            clip.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
            val w = rect.width()
            val h = rect.height()
            tint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0x5A03080C, 0x2603080C, Shader.TileMode.CLAMP)
            band.reset()
            band.moveTo(rect.left, rect.top); band.lineTo(rect.left + w * 0.70f, rect.top)
            band.lineTo(rect.left + w * 0.30f, rect.bottom); band.lineTo(rect.left, rect.bottom); band.close()
            sheen.shader = LinearGradient(rect.left, rect.top, rect.left + w * 0.5f, rect.bottom, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            thin.reset()
            thin.moveTo(rect.left + w * 0.74f, rect.top); thin.lineTo(rect.left + w * 0.80f, rect.top)
            thin.lineTo(rect.left + w * 0.52f, rect.bottom); thin.lineTo(rect.left + w * 0.46f, rect.bottom); thin.close()
            vig.shader = RadialGradient(rect.centerX(), rect.centerY(), maxOf(w, h) * 0.75f,
                intArrayOf(0x00000000, 0x00000000, 0x70000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            line.strokeWidth = dp(1f)
            line.shader = LinearGradient(rect.left, 0f, rect.right, 0f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        override fun draw(c: Canvas) {
            if (rect.width() <= 0f) return
            c.save()
            c.clipPath(clip)
            c.drawRect(rect, tint)
            c.drawRect(rect, lines)
            c.drawPath(band, sheen)
            c.drawPath(thin, stripe)
            c.drawRect(rect, vig)
            c.restore()
            c.drawLine(rect.left + radius, rect.top + dp(1f), rect.right - radius, rect.top + dp(1f), line)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // Round button / knob: chrome (or glowing cyan) ring around a brushed face with a top highlight.
    private inner class ChromeDrawable(private val accentRing: Boolean, private val ringDp: Float, private val silver: Boolean, private val sizeDp: Int = -1) : Drawable() {
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val face = Paint(Paint.ANTI_ALIAS_FLAG)
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        private val spec = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val arc = RectF()
        private var cx = 0f
        private var cy = 0f
        private var ringR = 0f
        private var faceR = 0f
        override fun getIntrinsicWidth() = if (sizeDp > 0) dp(sizeDp) else -1
        override fun getIntrinsicHeight() = if (sizeDp > 0) dp(sizeDp) else -1
        override fun onBoundsChange(b: android.graphics.Rect) {
            cx = b.exactCenterX(); cy = b.exactCenterY()
            val r = minOf(b.width(), b.height()) / 2f - dp(1f)
            val glowSpan = if (accentRing) dp(5f) else 0f
            val ringW = dp(ringDp)
            ringR = r - glowSpan - ringW / 2f
            faceR = ringR - ringW / 2f
            ring.strokeWidth = ringW
            val colors = if (accentRing)
                intArrayOf(0xFF8CF3FF.toInt(), 0xFF00A9DC.toInt(), 0xFFA8F7FF.toInt(), 0xFF0086B5.toInt(), 0xFF8CF3FF.toInt())
            else
                intArrayOf(0xFFF1F5F9.toInt(), 0xFF65717D.toInt(), 0xFFFFFFFF.toInt(), 0xFF414C57.toInt(), 0xFFE2E9F0.toInt(), 0xFF707C88.toInt(), 0xFFF1F5F9.toInt())
            val sweep = SweepGradient(cx, cy, colors, null)
            val m = Matrix()
            m.postRotate(-35f, cx, cy)
            sweep.setLocalMatrix(m)
            ring.shader = sweep
            face.shader = RadialGradient(cx - faceR * 0.25f, cy - faceR * 0.45f, faceR * 1.5f,
                if (silver) 0xFFF4F7FA.toInt() else 0xFF3D4954.toInt(),
                if (silver) 0xFF7F8B97.toInt() else 0xFF0E1419.toInt(), Shader.TileMode.CLAMP)
            brush.shader = SweepGradient(cx, cy, intArrayOf(
                0x30FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x30FFFFFF), null)
            arc.set(cx - faceR + dp(2f), cy - faceR + dp(2f), cx + faceR - dp(2f), cy + faceR - dp(2f))
            spec.strokeWidth = dp(1f)
            spec.color = if (silver) Color.argb(200, 255, 255, 255) else Color.argb(110, 255, 255, 255)
        }
        override fun draw(c: Canvas) {
            if (faceR <= 0f) return
            if (accentRing) {
                val steps = listOf(0x12, 0x22, 0x3A)
                for ((i, a) in steps.withIndex()) {
                    glow.strokeWidth = dp(ringDp) + (3 - i) * dp(2.2f)
                    glow.color = Color.argb(a, 0, 209, 255)
                    c.drawCircle(cx, cy, ringR, glow)
                }
            }
            c.drawCircle(cx, cy, ringR, ring)
            c.drawCircle(cx, cy, faceR, face)
            if (!silver) c.drawCircle(cx, cy, faceR, brush)
            c.drawArc(arc, 215f, 110f, false, spec)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    private fun chrome(round: Boolean = false): Drawable = if (round) circle() else panel()
    private inner class SpectrumView : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shown = FloatArray(AudioLevels.BANDS)
        private var barShader: Shader? = null
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            barShader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF9BF5FF.toInt(), 0xFF00C8FF.toInt(), 0xFF0088BC.toInt()), null, Shader.TileMode.CLAMP)
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
                if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) View.MeasureSpec.getSize(heightMeasureSpec) else suggestedMinimumHeight)
        }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            p.shader = barShader
            val bars = AudioLevels.BANDS
            val spacing = width.toFloat() / bars
            val playing = controller?.isPlaying == true
            val levels = AudioLevels.bands
            var alive = false
            for (i in 0 until bars) {
                val target = if (playing) levels[i] else 0f
                shown[i] = if (target >= shown[i]) target else shown[i] * 0.88f
                if (shown[i] > 0.02f) alive = true
                val h = height * (0.04f + 0.96f * shown[i])
                val bw = spacing * 0.5f
                val bx = i * spacing + (spacing - bw) / 2f
                p.alpha = 38
                c.drawRoundRect(bx - bw * 0.7f, height - h, bx + bw * 1.7f, height.toFloat(), bw, bw, p)
                p.alpha = if (i % 4 == 0) 255 else 220
                c.drawRoundRect(bx, height - h, bx + bw, height.toFloat(), bw / 2f, bw / 2f, p)
            }
            if (playing || alive) postInvalidateOnAnimation()
        }
    }
    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f)
    private fun button(value: String, size: Float = 18f, action: () -> Unit): TextView = text(value, size).apply {
        gravity = Gravity.CENTER; background = panel(); setOnClickListener { action() }
        elevation = dp(2).toFloat()
    }
    private fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        SoftEq.load(this)
        favorites.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet())
        window.statusBarColor = background; window.navigationBarColor = background
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(this@MainActivity.background) }
        header = text("SONORA", 23f, 0xffe6edf3.toInt()).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f; setPadding(dp(18), 0, 0, 0) }
        header.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val mid = (bottom - top) / 2f
            val half = header.textSize * 0.55f
            header.paint.shader = LinearGradient(0f, mid - half, 0f, mid + half,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFC7D1DB.toInt(), 0xFF8794A1.toInt()), null, Shader.TileMode.CLAMP)
            header.invalidate()
        }
        headerBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = panel(10f); setPadding(0, 0, dp(6), 0) }
        headerBar.addView(header, LinearLayout.LayoutParams(0, -1, 1f))
        headerBar.addView(IconView(iconSearch).apply { setOnClickListener { openSearch() } }, LinearLayout.LayoutParams(dp(44), -1))
        headerBar.addView(IconView(iconEq).apply { setOnClickListener { showEqualizer() } }, LinearLayout.LayoutParams(dp(44), -1))
        headerBar.addView(IconView(iconDots).apply { setOnClickListener { showSettings() } }, LinearLayout.LayoutParams(dp(40), -1))
        root.addView(headerBar, LinearLayout.LayoutParams(-1, dp(52)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        player = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(player, LinearLayout.LayoutParams(-1, -1))
        gifArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP; background = panel(8f); setPadding(dp(3), dp(3), dp(3), dp(3)) }
        // Landscape: make the player panel exactly as tall as the video frame.
        gifArea.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (isLandscape() && ::details.isInitialized) {
                val h = bottom - top
                val lp = details.layoutParams
                val want = if (h > dp(150)) h else ViewGroup.LayoutParams.WRAP_CONTENT
                if (lp != null && lp.height != want) { lp.height = want; details.layoutParams = lp }
            }
        }
        gif = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "GIF вместо обложки"
        }
        mediaBox = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        mediaBox.addView(gif, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        video = VideoView(this).apply { visibility = View.GONE; setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE) }
        mediaBox.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        gifArea.addView(mediaBox, LinearLayout.LayoutParams(-1, -2))
        gifChoose = button("Выбрать свой GIF", 13f) { gifPicker.launch(arrayOf("image/gif")) }
        gifArea.addView(gifChoose, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Two separate panels: a "display" (track name + visualizer) and a "control deck" (seek, buttons, volume).
        displayPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = darkPanel() }
        controlPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = darkPanel() }
        trackName = text("Выбери трек в библиотеке", 15f, accent).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setShadowLayer(dp(5f), 0f, 0f, 0x6600d1ff.toInt()) }
        trackArtist = text("", 21f, accent).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(dp(7f), 0f, 0f, 0x8800d1ff.toInt()) }
        heart = IconView(iconHeart).apply { setOnClickListener { toggleFavorite() } }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(trackArtist, LinearLayout.LayoutParams(-1, -2))
            addView(trackName, LinearLayout.LayoutParams(-1, -2))
        }
        titleRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(heart, LinearLayout.LayoutParams(dp(40), dp(40)))
        displayPanel.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        spectrum = SpectrumView()
        glass = FrameLayout(this).apply { background = GlassFrameDrawable(); foreground = GlassDrawable() }
        glass.addView(spectrum, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        displayPanel.addView(glass, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(7) })
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        shuffleButton = button("⇄", 20f) {
            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; updateModes() }
        }
        val prev = button("❮❮", 17f) { controller?.seekToPreviousMediaItem() }
        playButton = button("▶", 25f) { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        val next = button("❯❯", 17f) { controller?.seekToNextMediaItem() }
        repeatButton = button("↻", 23f) {
            controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; updateModes() }
        }
        listOf(shuffleButton, prev, playButton, next, repeatButton).forEachIndexed { index, view ->
            view.background = if (index == 2) circle(accent, 3f) else circle()
            // Equal width and height are essential: circles must never turn into ovals.
            val size = if (index == 2) dp(bigSize) else dp(smallSize)
            controls.addView(view, LinearLayout.LayoutParams(size, size).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        controlsRow = controls
        controls.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val row = v as LinearLayout
            val available = row.width - row.paddingLeft - row.paddingRight
            if (available <= 0) return@addOnLayoutChangeListener
            val small = minOf(dp(smallSize), (available - dp(bigSize) - dp(30)) / 4).coerceAtLeast(dp(26))
            val large = minOf(dp(bigSize), (available - dp(30) - 4 * small)).coerceAtLeast(dp(34))
            for (i in 0 until row.childCount) {
                val size = if (i == 2) large else small
                val child = row.getChildAt(i)
                val lp = child.layoutParams as LinearLayout.LayoutParams
                if (lp.width != size || lp.height != size) { lp.width = size; lp.height = size; child.layoutParams = lp }
            }
        }
        seek = SeekBar(this).apply { max = 1000; progressDrawable = sliderTrack(); thumb = ChromeDrawable(false, 3f, true, 24) }
        controlPanel.addView(seek, LinearLayout.LayoutParams(-1, dp(36)))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) controller?.let { if (it.duration > 0) it.seekTo(it.duration * progress / 1000) } }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        elapsed = text("0:00", 12f, muted)
        total = text("0:00", 12f, muted).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        times.addView(elapsed, LinearLayout.LayoutParams(0, dp(22), 1f))
        times.addView(total, LinearLayout.LayoutParams(0, dp(22), 1f))
        controlPanel.addView(times)
        controlPanel.addView(spacer(), LinearLayout.LayoutParams(-1, 0, 1f))
        controlPanel.addView(controls, LinearLayout.LayoutParams(-1, dp(86)))
        controlPanel.addView(spacer(), LinearLayout.LayoutParams(-1, 0, 1f))
        volumeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        volumeRow.addView(IconView(iconSpeakerLow), LinearLayout.LayoutParams(dp(34), dp(38)))
        volume = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            progressDrawable = sliderTrack()
            thumb = ChromeDrawable(false, 3f, true, 24)
        }
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0) } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { volumeDragging = true }
            override fun onStopTrackingTouch(bar: SeekBar?) { volumeDragging = false }
        })
        volumeRow.addView(volume, LinearLayout.LayoutParams(0, dp(38), 1f))
        volumeRow.addView(IconView(iconSpeakerHigh), LinearLayout.LayoutParams(dp(34), dp(38)))
        controlPanel.addView(volumeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        details.addView(displayPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        details.addView(controlPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        configurePlayerLayout()
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        content.addView(library, LinearLayout.LayoutParams(-1, -1))
        // Row 1: compact actions (in landscape the filter chips join this row)
        actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val scanBtn = button("Сканировать", 14f) { requestMusicScan() }
        val folderBtn = button("Папка", 14f) {
            // No resolveActivity() pre-check: on Android 11+ it wrongly returns null without a <queries> entry.
            try {
                folderPicker.launch(null)
            } catch (e: android.content.ActivityNotFoundException) {
                toast("В системе нет выбора папок. Используйте «Сканировать»")
            } catch (e: Exception) {
                toast("Проводник недоступен: ${e.localizedMessage ?: "ошибка системы"}")
            }
        }
        actionsRow.addView(scanBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        actionsRow.addView(folderBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        library.addView(actionsRow, LinearLayout.LayoutParams(-1, -2))
        // Row 2: metal search field
        search = EditText(this).apply {
            hint = "Поиск по трекам…"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 15f
            isSingleLine = true; setPadding(dp(16), 0, dp(16), 0); background = darkPanel(22f)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        library.addView(search, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
        // Row 3: filter chips
        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips = listOf(
            button("Все", 13f) { setFilterMode(0) },
            button("Избранное", 13f) { setFilterMode(3) },
            button("Папки", 13f) { setFilterMode(1) },
            button("Недавние", 13f) { setFilterMode(2) }
        )
        markChips()
        library.addView(chipsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(4) })
        // Track list (recycling ListView, fast even with thousands of songs)
        libraryAdapter = LibraryAdapter()
        val listView = ListView(this).apply {
            divider = null; dividerHeight = 0; overScrollMode = View.OVER_SCROLL_NEVER
            setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            adapter = libraryAdapter
            setOnItemClickListener { _, _, pos, _ -> onRowClick(rows[pos]) }
        }
        library.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        // Mini-player
        miniTitle = text("", 14f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD }
        miniSub = text("SONORA", 12f, muted)
        val miniText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            addView(miniTitle, LinearLayout.LayoutParams(-1, -2))
            addView(miniSub, LinearLayout.LayoutParams(-1, -2))
        }
        miniPlay = button("▶", 18f) { controller?.let { if (it.isPlaying) it.pause() else it.play() } }.apply { background = circle(accent, 2f) }
        val miniNext = button("❯❯", 14f) { controller?.seekToNextMediaItem() }.apply { background = circle() }
        miniBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = panel(14f); setPadding(dp(14), 0, dp(8), 0); visibility = View.GONE
            setOnClickListener { showLibrary(false) }
        }
        miniBar.addView(miniText, LinearLayout.LayoutParams(0, -2, 1f))
        miniBar.addView(miniPlay, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        miniBar.addView(miniNext, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        library.addView(miniBar, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(6) })
        settings = ScrollView(this).apply { visibility = View.GONE; overScrollMode = View.OVER_SCROLL_NEVER }
        val settingsItems = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(6), dp(2), dp(12)) }
        settings.addView(settingsItems)
        fun sectionTitle(t: String) = text(t, 15f, accent).apply {
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f; setPadding(dp(6), 0, 0, 0)
            setShadowLayer(dp(6f), 0f, 0f, 0x8800d1ff.toInt())
        }
        fun animButton(label: String, mode: Int, action: () -> Unit): TextView = button(label, 14f) { action() }.also { animButtons[mode] = it }
        settingsItems.addView(sectionTitle("АНИМАЦИЯ ОБЛОЖКИ"), LinearLayout.LayoutParams(-1, dp(40)))
        val animRows = listOf(
            listOf(animButton("Видео 1", 1) { selectAnimation(1) }, animButton("Видео 2", 2) { selectAnimation(2) }),
            listOf(animButton("Видео 3", 3) { selectAnimation(3) }, animButton("Видео 4", 4) { selectAnimation(4) }),
            listOf(animButton("Мой GIF", 0) { gifPicker.launch(arrayOf("image/gif")) }, animButton("Без анимации", -1) { selectAnimation(-1) })
        )
        for (pair in animRows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, b ->
                row.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (i == 0) marginEnd = dp(4) else marginStart = dp(4) })
            }
            settingsItems.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        settingsItems.addView(sectionTitle("АВТОМОБИЛЬНЫЙ РЕЖИМ"), LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(10) })
        carToggle = button("", 14f) {
            carMode = !carMode
            getPreferences(MODE_PRIVATE).edit().putBoolean("car_mode", carMode).apply()
            updateCarToggle()
            applyCarMode()
        }
        settingsItems.addView(carToggle, LinearLayout.LayoutParams(-1, dp(52)))
        settingsItems.addView(text("Крупные кнопки и вкладки, экран не гаснет", 12f, muted).apply { setPadding(dp(6), dp(4), 0, 0) }, LinearLayout.LayoutParams(-1, -2))
        // ---- Equalizer screen ----
        eqScreen = ScrollView(this).apply { visibility = View.GONE; overScrollMode = View.OVER_SCROLL_NEVER }
        val eqItems = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(6), dp(2), dp(12)) }
        eqScreen.addView(eqItems)
        val eqHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        eqHead.addView(sectionTitle("ЭКВАЛАЙЗЕР"), LinearLayout.LayoutParams(0, dp(40), 1f))
        eqOnButton = button("", 14f) {
            setEq(enabled = !SoftEq.params.enabled)
            SoftEq.save(this)
            updateEqUi()
        }
        eqHead.addView(eqOnButton, LinearLayout.LayoutParams(dp(110), dp(40)))
        eqItems.addView(eqHead, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        eqPresetButtons = eqPresetNames.mapIndexed { i, name -> button(name, 13f) { applyPreset(i) } }
        for (r in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0..2) row.addView(eqPresetButtons[r * 3 + j], LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
            eqItems.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        eqView = EqView()
        eqView.onChange = { setEq(gains = eqView.gains) }
        eqView.onCommit = { SoftEq.save(this); updateEqUi() }
        eqItems.addView(eqView, LinearLayout.LayoutParams(-1, dp(250)).apply { topMargin = dp(4) })
        eqBassLabel = text("", 14f, Color.WHITE).apply { setPadding(dp(6), 0, 0, 0) }
        val bassRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bassRow.addView(eqBassLabel, LinearLayout.LayoutParams(0, -2, 1f))
        bassRow.addView(button("Сбросить", 13f) {
            setEq(gains = FloatArray(SoftEq.BANDS), bass = 0f, enabled = SoftEq.params.enabled)
            SoftEq.save(this)
            updateEqUi()
        }, LinearLayout.LayoutParams(dp(110), dp(36)))
        eqItems.addView(bassRow, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })
        eqBassSeek = SeekBar(this).apply {
            max = 12
            progressDrawable = sliderTrack()
            thumb = ChromeDrawable(false, 3f, true, 24)
        }
        eqBassSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { setEq(bass = progress.toFloat()); eqBassLabel.text = "Бас  +$progress дБ" }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) { SoftEq.save(this@MainActivity); updateEqUi() }
        })
        eqItems.addView(eqBassSeek, LinearLayout.LayoutParams(-1, dp(40)))
        updateEqUi()
        content.addView(settings, LinearLayout.LayoutParams(-1, -1))
        content.addView(eqScreen, LinearLayout.LayoutParams(-1, -1))
        nav = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; background = panel(); setPadding(dp(4), dp(3), dp(4), dp(3)) }
        playerTab = button("Плеер", 15f) { showLibrary(false) }
        libraryTab = button("Библиотека", 15f) { showLibrary(true) }
        settingsTab = button("Настройки", 15f) { showSettings() }
        nav.addView(playerTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        nav.addView(libraryTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(5) })
        nav.addView(settingsTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(5) })
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(61)))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(12), bars.top + dp(3), bars.right + dp(12), bars.bottom + dp(5))
            insets
        }
        setContentView(root)
        applyChrome()
        showLibrary(false)
        val prefs = getPreferences(MODE_PRIVATE)
        carMode = prefs.getBoolean("car_mode", false)
        updateCarToggle()
        applyCarMode()
        animationMode = prefs.getInt("animation_mode", 1)
        prefs.getString("gif_uri", null)?.let { gifUri = Uri.parse(it) }
        displayAnimation()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { refreshTrack() }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶"; spectrum.invalidate(); updateMini() }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateModes() }
                    override fun onRepeatModeChanged(repeatMode: Int) { updateModes() }
                })
                refreshTrack(); updateModes()
                tryRestore()
            } catch (e: Exception) { trackName.text = "Ошибка Media3: ${e.message}" }
        }, ContextCompat.getMainExecutor(this))
        handler.post { startupScan() }
        handler.post(ticker)
    }
    private fun markTabs(active: TextView?) {
        for (t in listOf(playerTab, libraryTab, settingsTab)) {
            val on = t === active
            t.background = if (on) activePanel() else panel()
            t.setTextColor(if (on) accent else Color.WHITE)
            t.setShadowLayer(if (on) dp(6f) else 0f, 0f, 0f, accent)
        }
    }
    // Header / tab bar sizes depend on orientation: landscape must fit in a short screen.
    private fun applyChrome() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        header.textSize = if (landscape) 19f else 23f
        headerBar.layoutParams = (headerBar.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 40 else 52) }
        nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).apply { height = dp((if (landscape) 52 else 61) + (if (carMode) 8 else 0)) }
        for (t in listOf(playerTab, libraryTab, settingsTab)) {
            t.layoutParams = (t.layoutParams as LinearLayout.LayoutParams).apply { height = dp((if (landscape) 42 else 52) + (if (carMode) 8 else 0)) }
        }
        eqView.layoutParams = (eqView.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 170 else 330) }
        layoutLibrary()
        updateMini()
    }
    private fun showLibrary(show: Boolean) {
        settings.visibility = View.GONE
        eqScreen.visibility = View.GONE
        library.visibility = if (show) View.VISIBLE else View.GONE
        player.visibility = if (show) View.GONE else View.VISIBLE
        if (show) video.pause() else if (animationMode > 0) video.start()
        spectrum.invalidate()
        playerTab.setTextColor(if (show) Color.WHITE else accent)
        libraryTab.setTextColor(if (show) accent else Color.WHITE)
        settingsTab.setTextColor(Color.WHITE)
        markTabs(if (show) libraryTab else playerTab)
    }
    private fun markAnimButtons() {
        for ((mode, b) in animButtons) {
            val on = mode == animationMode
            b.background = if (on) activePanel() else panel()
            b.setTextColor(if (on) accent else Color.WHITE)
        }
    }
    // Car mode: bigger controls and tabs, screen stays on.
    private fun applyCarMode() {
        if (carMode) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyChrome()
        configurePlayerLayout()
    }
    private fun updateCarToggle() {
        carToggle.text = "Автомобильный режим: " + if (carMode) "ВКЛ" else "ВЫКЛ"
        carToggle.background = if (carMode) activePanel() else panel()
        carToggle.setTextColor(if (carMode) accent else Color.WHITE)
    }
    // Any user change turns the equalizer on automatically.
    private fun setEq(gains: FloatArray = SoftEq.params.gains, bass: Float = SoftEq.params.bassDb, enabled: Boolean = true) {
        SoftEq.params = SoftEq.Params(enabled, gains.copyOf(), bass)
    }
    private fun applyPreset(i: Int) {
        setEq(gains = eqPresets[i])
        SoftEq.save(this)
        updateEqUi()
    }
    private fun updateEqUi() {
        if (!::eqView.isInitialized) return
        val p = SoftEq.params
        System.arraycopy(p.gains, 0, eqView.gains, 0, SoftEq.BANDS)
        eqView.invalidate()
        eqView.alpha = if (p.enabled) 1f else 0.45f
        eqOnButton.text = if (p.enabled) "ВКЛ" else "ВЫКЛ"
        eqOnButton.background = if (p.enabled) activePanel() else panel()
        eqOnButton.setTextColor(if (p.enabled) accent else Color.WHITE)
        eqBassSeek.progress = p.bassDb.toInt()
        eqBassLabel.text = "Бас  +${p.bassDb.toInt()} дБ"
        eqPresetButtons.forEachIndexed { i, b ->
            val on = p.enabled && eqPresets[i].contentEquals(p.gains)
            b.background = if (on) activePanel() else panel()
            b.setTextColor(if (on) accent else Color.WHITE)
        }
    }
    private fun showEqualizer() {
        player.visibility = View.GONE
        library.visibility = View.GONE
        settings.visibility = View.GONE
        eqScreen.visibility = View.VISIBLE
        video.pause()
        updateEqUi()
        markTabs(null)
    }
    private fun showSettings() {
        player.visibility = View.GONE
        library.visibility = View.GONE
        settings.visibility = View.VISIBLE
        eqScreen.visibility = View.GONE
        video.pause()
        playerTab.setTextColor(Color.WHITE)
        libraryTab.setTextColor(Color.WHITE)
        settingsTab.setTextColor(accent)
        markTabs(settingsTab)
    }
    private fun selectAnimation(mode: Int) {
        animationMode = mode
        getPreferences(MODE_PRIVATE).edit().putInt("animation_mode", mode).apply()
        displayAnimation()
        showLibrary(false)
    }
    private fun displayAnimation() {
        markAnimButtons()
        video.stopPlayback()
        video.visibility = View.GONE
        gif.visibility = View.GONE
        if (animationMode in 1..4) {
            val resId = resources.getIdentifier("animation_$animationMode", "raw", packageName)
            if (resId == 0) { toast("Встроенное видео не найдено"); return }
            video.visibility = View.VISIBLE
            video.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                val w = mp.videoWidth.coerceAtLeast(1)
                val h = mp.videoHeight.coerceAtLeast(1)
                val width = mediaBox.width.takeIf { it > 0 } ?: gifArea.width.takeIf { it > 0 }
                    ?: (resources.displayMetrics.widthPixels / if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 2 else 1)
                val desiredHeight = (width.toFloat() * h / w).toInt()
                val maxHeight = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    (resources.displayMetrics.heightPixels * 0.66f).toInt()
                else (resources.displayMetrics.heightPixels * 0.45f).toInt()
                val height = desiredHeight.coerceAtMost(maxHeight).coerceAtLeast(dp(80))
                val videoWidth = (height.toFloat() * w / h).toInt().coerceAtMost(width)
                mediaBox.layoutParams = (mediaBox.layoutParams as LinearLayout.LayoutParams).apply { this.height = height }
                video.layoutParams = FrameLayout.LayoutParams(videoWidth, height, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                if (player.visibility == View.VISIBLE) video.start()
            }
            video.setVideoURI(Uri.parse("android.resource://$packageName/$resId"))
        } else if (animationMode == 0) {
            gif.visibility = View.VISIBLE
            displayGif()
        } else {
            mediaBox.layoutParams = (mediaBox.layoutParams as LinearLayout.LayoutParams).apply { height = dp(4) }
        }
        gifChoose.visibility = if (animationMode == 0) View.VISIBLE else View.GONE
        gifArea.background = if (animationMode == -1) null else panel(8f)
        gifArea.setPadding(dp(if (animationMode == -1) 0 else 3), dp(if (animationMode == -1) 0 else 3), dp(if (animationMode == -1) 0 else 3), dp(if (animationMode == -1) 0 else 3))
    }
    private fun updateModes() {
        controller?.let {
            shuffleButton.setTextColor(if (it.shuffleModeEnabled) accent else Color.WHITE)
            repeatButton.text = if (it.repeatMode == Player.REPEAT_MODE_ONE) "↻1" else "↻"
            repeatButton.setTextColor(if (it.repeatMode != Player.REPEAT_MODE_OFF) accent else Color.WHITE)
        }
    }
    private fun refreshTrack() {
        val metadata = controller?.currentMediaItem?.mediaMetadata
        trackName.text = metadata?.title ?: "Выбери трек в библиотеке"
        trackArtist.text = metadata?.artist ?: ""
        playButton.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
        updateMini()
        updateHeart()
        if (::libraryAdapter.isInitialized) libraryAdapter.notifyDataSetChanged()
    }
    private fun configurePlayerLayout() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        player.removeAllViews()
        (gifArea.parent as? ViewGroup)?.removeView(gifArea)
        (details.parent as? ViewGroup)?.removeView(details)
        player.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        player.gravity = Gravity.TOP
        if (landscape) {
            player.addView(gifArea, LinearLayout.LayoutParams(0, -2, 1.2f).apply { marginEnd = dp(6) })
            player.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            // Portrait: video on top, the player panel stretches to fill the rest of the screen.
            player.addView(gifArea, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            player.addView(details, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(6) })
        }
        gif.scaleType = ImageView.ScaleType.FIT_CENTER
        gif.adjustViewBounds = true
        gif.maxHeight = if (landscape) (resources.displayMetrics.heightPixels * 0.70f).toInt() else (resources.displayMetrics.heightPixels * 0.52f).toInt()
        // wrap_content and adjustViewBounds preserve aspect ratio; maxHeight prevents oversized GIF.
        gif.layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
        // Compact metrics in landscape so the whole panel fits; roomy in portrait to fill the screen.
        // Car mode = even bigger touch targets. Narrow landscape screens get slightly smaller buttons so the panel still fits.
        val narrow = landscape && resources.configuration.screenWidthDp < 720
        smallSize = if (landscape) (if (narrow) 36 else if (carMode) 52 else 42) else (if (carMode) 60 else 52)
        bigSize = if (landscape) (if (narrow) 48 else if (carMode) 68 else 56) else (if (carMode) 90 else 78)
        val minSpectrum = if (resources.configuration.screenHeightDp < 700) 56 else 92
        glass.minimumHeight = dp(if (landscape) 30 else minSpectrum + 12)
        (glass.layoutParams as LinearLayout.LayoutParams).apply {
            height = 0
            weight = 1f
            topMargin = dp(if (landscape) 3 else 10)
        }
        val gm = dp(if (landscape) 4 else 8)
        (spectrum.layoutParams as FrameLayout.LayoutParams).setMargins(gm, gm, gm, gm)
        for (sp in spacers) { sp.minimumHeight = dp(if (landscape) 4 else 8); sp.visibility = View.VISIBLE }
        (controlPanel.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (landscape) 6 else 8)
        (controlsRow.layoutParams as LinearLayout.LayoutParams).height = dp(bigSize + (if (landscape) 8 else 14))
        (seek.layoutParams as LinearLayout.LayoutParams).height = dp(if (landscape) 28 else 38)
        for (i in 0 until controlsRow.childCount) {
            val lp = controlsRow.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            val size = dp(if (i == 2) bigSize else smallSize)
            lp.width = size; lp.height = size
        }
        displayPanel.setPadding(dp(12), dp(if (landscape) 6 else 10), dp(12), dp(if (landscape) 4 else 8))
        controlPanel.setPadding(dp(12), dp(if (landscape) 4 else 8), dp(12), dp(if (landscape) 6 else 10))
        trackName.maxLines = if (landscape) 1 else 2
        trackArtist.textSize = if (landscape) (if (carMode) 20f else 18f) else (if (carMode) 24f else 21f)
        trackName.textSize = if (landscape) 14f else 15f
        // no room for the on-screen volume slider in landscape or on short portrait screens (hardware keys still work)
        volumeRow.visibility = if (landscape || resources.configuration.screenHeightDp < 700) View.GONE else View.VISIBLE
        controlsRow.requestLayout(); details.requestLayout()
        if (animationMode > 0 && ::video.isInitialized) mediaBox.post { displayAnimation() }
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); applyChrome(); configurePlayerLayout() }
    override fun onPause() { super.onPause(); video.pause() }
    override fun onResume() { super.onResume(); if (::video.isInitialized && animationMode > 0 && player.visibility == View.VISIBLE) video.start() }
    private fun displayGif() {
        val uri = gifUri ?: return
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(contentResolver, uri))
                gif.setImageDrawable(drawable)
                (drawable as? Animatable)?.start()
            } else gif.setImageURI(uri)
        } catch (e: Exception) { trackArtist.text = "Ошибка GIF: ${e.message}" }
    }
    private fun updateHeart() {
        if (!::heart.isInitialized) return
        val id = controller?.currentMediaItem?.mediaId
        heart.filled = id != null && id in favorites
        heart.invalidate()
    }
    private fun toggleFavorite() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        if (!favorites.remove(id)) favorites.add(id)
        getPreferences(MODE_PRIVATE).edit().putStringSet("favorites", HashSet(favorites)).apply()
        updateHeart()
        if (filterMode == 3) applyFilter()
    }
    private fun openSearch() {
        showLibrary(true)
        search.requestFocus()
        search.post { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(search, 0) }
    }
    // 10 vertical band sliders (-12..+12 dB), drawn and touched in code.
    private inner class EqView : View(this@MainActivity) {
        val gains = FloatArray(SoftEq.BANDS)
        var onChange: (() -> Unit)? = null
        var onCommit: (() -> Unit)? = null
        private var active = -1
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labels = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        private val knob = ChromeDrawable(false, 2.6f, true, 24).also { it.setBounds(0, 0, dp(24), dp(24)) }
        private fun yTop() = dp(28).toFloat()
        private fun yBottom() = height - dp(26).toFloat()
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val n = SoftEq.BANDS
            val colW = width.toFloat() / n
            val top = yTop()
            val bottom = yBottom()
            val mid = (top + bottom) / 2f
            p.strokeCap = Paint.Cap.ROUND
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1).toFloat()
            p.color = 0x668795a3
            c.drawLine(0f, mid, width.toFloat(), mid, p)
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(11).toFloat()
            for (i in 0 until n) {
                val cx = colW * (i + 0.5f)
                val g = gains[i]
                val yg = mid - (g / 12f) * (mid - top)
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(4).toFloat()
                p.color = 0xff2a333c.toInt()
                c.drawLine(cx, top, cx, bottom, p)
                p.color = accent
                c.drawLine(cx, mid, cx, yg, p)
                c.save()
                c.translate(cx - dp(12), yg - dp(12))
                knob.draw(c)
                c.restore()
                if (i == active) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = dp(2).toFloat()
                    p.color = accent
                    c.drawCircle(cx, yg, dp(14).toFloat(), p)
                }
                p.style = Paint.Style.FILL
                p.style = Paint.Style.FILL
                p.color = if (g != 0f) accent else muted
                c.drawText((if (g > 0f) "+" else "") + g.toInt(), cx, dp(16).toFloat(), p)
                p.color = muted
                c.drawText(labels[i], cx, height - dp(8).toFloat(), p)
            }
        }
        private fun setFromY(y: Float) {
            if (active < 0) return
            val top = yTop()
            val mid = (top + yBottom()) / 2f
            val g = (((mid - y) / (mid - top)) * 12f).coerceIn(-12f, 12f)
            gains[active] = Math.round(g).toFloat()
            invalidate()
            onChange?.invoke()
        }
        override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    active = (e.x / (width.toFloat() / SoftEq.BANDS)).toInt().coerceIn(0, SoftEq.BANDS - 1)
                    setFromY(e.y)
                }
                android.view.MotionEvent.ACTION_MOVE -> setFromY(e.y)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    active = -1
                    invalidate()
                    onCommit?.invoke()
                }
            }
            return true
        }
    }
    // Small vector icons drawn in code (no image assets).
    private inner class IconView(private val kind: Int) : View(this@MainActivity) {
        var filled = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val s = dp(22).toFloat() / 24f
            val ox = (width - 24f * s) / 2f
            val oy = (height - 24f * s) / 2f
            fun gx(v: Float) = ox + v * s
            fun gy(v: Float) = oy + v * s
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.9f * s
            p.color = 0xffdbe4ec.toInt()
            when (kind) {
                iconSearch -> {
                    c.drawCircle(gx(10f), gy(10f), 6.5f * s, p)
                    c.drawLine(gx(15f), gy(15f), gx(20.5f), gy(20.5f), p)
                }
                iconEq -> {
                    for (lx in listOf(5f, 12f, 19f)) c.drawLine(gx(lx), gy(4f), gx(lx), gy(20f), p)
                    p.style = Paint.Style.FILL
                    c.drawCircle(gx(5f), gy(15f), 2.4f * s, p)
                    c.drawCircle(gx(12f), gy(9f), 2.4f * s, p)
                    c.drawCircle(gx(19f), gy(13f), 2.4f * s, p)
                }
                iconDots -> {
                    p.style = Paint.Style.FILL
                    for (cy in listOf(5f, 12f, 19f)) c.drawCircle(gx(12f), gy(cy), 2.1f * s, p)
                }
                iconHeart -> {
                    val path = android.graphics.Path()
                    path.moveTo(gx(12f), gy(20.5f))
                    path.cubicTo(gx(5f), gy(15f), gx(2.5f), gy(11.5f), gx(2.5f), gy(8f))
                    path.cubicTo(gx(2.5f), gy(5.5f), gx(4.5f), gy(3.5f), gx(7f), gy(3.5f))
                    path.cubicTo(gx(9.2f), gy(3.5f), gx(11f), gy(4.8f), gx(12f), gy(6.5f))
                    path.cubicTo(gx(13f), gy(4.8f), gx(14.8f), gy(3.5f), gx(17f), gy(3.5f))
                    path.cubicTo(gx(19.5f), gy(3.5f), gx(21.5f), gy(5.5f), gx(21.5f), gy(8f))
                    path.cubicTo(gx(21.5f), gy(11.5f), gx(19f), gy(15f), gx(12f), gy(20.5f))
                    path.close()
                    if (filled) { p.color = accent; p.style = Paint.Style.FILL_AND_STROKE }
                    c.drawPath(path, p)
                }
                else -> {
                    val path = android.graphics.Path()
                    path.moveTo(gx(3f), gy(9f)); path.lineTo(gx(7f), gy(9f)); path.lineTo(gx(12f), gy(4.5f))
                    path.lineTo(gx(12f), gy(19.5f)); path.lineTo(gx(7f), gy(15f)); path.lineTo(gx(3f), gy(15f)); path.close()
                    p.color = 0xffb8c4cf.toInt()
                    c.drawPath(path, p)
                    c.drawArc(RectF(gx(8f), gy(8f), gx(16f), gy(16f)), -50f, 100f, false, p)
                    if (kind == iconSpeakerHigh) c.drawArc(RectF(gx(4f), gy(4f), gx(20f), gy(20f)), -50f, 100f, false, p)
                }
            }
        }
    }
    // Flexible gap inside the player panel: shares leftover height in portrait, hidden in landscape.
    private fun spacer(): View = Flex().also { it.minimumHeight = dp(8); spacers.add(it) }
    private inner class Flex : View(this@MainActivity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
                if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) View.MeasureSpec.getSize(heightMeasureSpec) else suggestedMinimumHeight)
        }
    }
    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Artist from the file tag; if missing, try the usual "Artist - Title" file name pattern.
    private fun splitTitle(rawTitle: String, tagArtist: String?): Pair<String, String> {
        val artist = tagArtist?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
        if (artist != null) return artist to rawTitle.removePrefix("$artist - ").trim().ifEmpty { rawTitle }
        val idx = rawTitle.indexOf(" - ")
        if (idx > 0 && idx < rawTitle.length - 3) return rawTitle.substring(0, idx).trim() to rawTitle.substring(idx + 3).trim()
        return "Неизвестный исполнитель" to rawTitle
    }
    private fun toMediaItem(s: Song) = MediaItem.Builder().setMediaId(s.id).setUri(s.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.name).setArtist(s.artist).build()).build()

    private inner class LibraryAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h = (convertView?.tag as? RowHolder) ?: makeRowHolder()
            val row = rows[position]
            val song = row.song
            val playing = song != null && controller?.currentMediaItem?.mediaId == song.id
            if (song != null) {
                h.icon.text = "♫"
                h.title.text = song.title
                val parts = mutableListOf<String>()
                if (song.durationMs > 0) parts.add(formatTime(song.durationMs))
                if (song.format.isNotBlank()) parts.add(song.format)
                h.sub.text = parts.joinToString(" · ")
            } else if (row.folder != null) {
                h.icon.text = "☰"; h.title.text = row.folder; h.sub.text = "${row.count} треков"
            } else {
                h.icon.text = "❮"; h.title.text = "Назад к папкам"; h.sub.text = selectedFolder ?: ""
            }
            h.title.setTextColor(if (playing) accent else Color.WHITE)
            h.icon.setTextColor(if (playing) accent else Color.WHITE)
            h.root.background = if (playing) activePanel(10f) else null
            return h.root
        }
    }
    private fun makeRowHolder(): RowHolder {
        val icon = text("♫", 18f).apply { gravity = Gravity.CENTER; background = panel(10f) }
        val title = text("", 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val sub = text("", 12f, muted).apply { maxLines = 1 }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(sub, LinearLayout.LayoutParams(-1, -2))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
        }
        root.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        val holder = RowHolder(root, icon, title, sub)
        root.tag = holder
        return holder
    }
    private fun onRowClick(row: Row) {
        val song = row.song
        if (song != null) {
            val list = modeSongs
            val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            getSharedPreferences("sonora_state", MODE_PRIVATE).edit().putInt("q_mode", filterMode).putString("q_folder", selectedFolder).apply()
            controller?.apply { setMediaItems(list.map { toMediaItem(it) }, index, 0); prepare(); play() }
            trackName.text = song.name
            trackArtist.text = song.artist
            showLibrary(false)
        } else if (row.folder != null) {
            selectedFolder = row.folder; applyFilter()
        } else {
            selectedFolder = null; applyFilter()
        }
    }
    private fun setFilterMode(mode: Int) {
        filterMode = mode; selectedFolder = null
        markChips(); applyFilter()
    }
    private fun markChips() {
        chips.forEachIndexed { i, c ->
            val on = chipModes[i] == filterMode
            c.background = if (on) activePanel(19f) else panel(19f)
            c.setTextColor(if (on) accent else Color.WHITE)
        }
    }
    // Chips sit in their own row in portrait and move up beside the buttons in landscape (short screen).
    private fun layoutLibrary() {
        val landscape = isLandscape()
        val target = if (landscape) actionsRow else chipsRow
        for (i in 0 until 2) (actionsRow.getChildAt(i).layoutParams as LinearLayout.LayoutParams).height = dp(if (landscape) 38 else 44)
        for (c in chips) {
            (c.parent as? ViewGroup)?.removeView(c)
            target.addView(c, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        chipsRow.visibility = if (landscape) View.GONE else View.VISIBLE
        (search.layoutParams as LinearLayout.LayoutParams).height = dp(if (landscape) 40 else 46)
        search.requestLayout(); actionsRow.requestLayout()
    }
    private fun updateMini() {
        if (!::miniBar.isInitialized) return
        val item = controller?.currentMediaItem
        miniBar.visibility = if (item == null || isLandscape()) View.GONE else View.VISIBLE
        miniTitle.text = item?.mediaMetadata?.title ?: ""
        miniSub.text = item?.mediaMetadata?.artist ?: ""
        miniPlay.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }
    private fun applyFilter() {
        val q = if (::search.isInitialized) search.text.toString().trim() else ""
        fun matches(s: Song) = q.isEmpty() || s.title.contains(q, ignoreCase = true)
        rows.clear()
        when (filterMode) {
            1 -> if (selectedFolder == null) {
                modeSongs = emptyList()
                for ((f, list) in allSongs.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
                    if (q.isEmpty() || f.contains(q, ignoreCase = true)) rows.add(Row(null, f, list.size))
                }
            } else {
                modeSongs = allSongs.filter { it.folder == selectedFolder }
                rows.add(Row(null, null, 0))
                modeSongs.filter { matches(it) }.forEach { rows.add(Row(it, null, 0)) }
            }
            2 -> {
                modeSongs = allSongs.sortedByDescending { it.added }.take(50)
                modeSongs.filter { matches(it) }.forEach { rows.add(Row(it, null, 0)) }
            }
            3 -> {
                modeSongs = allSongs.filter { it.id in favorites }
                modeSongs.filter { matches(it) }.forEach { rows.add(Row(it, null, 0)) }
            }
            else -> {
                modeSongs = allSongs
                allSongs.filter { matches(it) }.forEach { rows.add(Row(it, null, 0)) }
            }
        }
        libraryAdapter.notifyDataSetChanged()
    }
    // After a restart: put back the last queue, track and position (paused, so music never starts by itself).
    private fun tryRestore() {
        val c = controller ?: return
        if (restored || allSongs.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return // the service is still alive and already has a queue
        val state = getSharedPreferences("sonora_state", MODE_PRIVATE)
        val id = state.getString("last_id", null) ?: return
        val folder = state.getString("q_folder", null)
        var list = when (state.getInt("q_mode", 0)) {
            1 -> if (folder != null) allSongs.filter { it.folder == folder } else allSongs
            2 -> allSongs.sortedByDescending { it.added }.take(50)
            3 -> allSongs.filter { it.id in favorites }
            else -> allSongs
        }
        var index = list.indexOfFirst { it.id == id }
        if (index < 0) { list = allSongs; index = list.indexOfFirst { it.id == id } }
        if (index < 0) return
        c.setMediaItems(list.map { toMediaItem(it) }, index, state.getLong("last_pos", 0L))
        c.shuffleModeEnabled = state.getBoolean("last_shuffle", false)
        c.repeatMode = state.getInt("last_repeat", Player.REPEAT_MODE_OFF)
        c.prepare()
        refreshTrack()
        updateModes()
    }
    private fun setLibrary(songs: List<Song>) {
        allSongs.clear(); allSongs.addAll(songs)
        selectedFolder = null
        applyFilter()
        tryRestore()
    }
    private fun scanDeviceMusic(quiet: Boolean = false) {
        val songs = mutableListOf<Song>()
        try {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ARTIST)
            contentResolver.query(collection, columns, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { cursor ->
                val idI = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameI = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleI = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val durI = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val addedI = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataI = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val artistI = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                while (cursor.moveToNext() && songs.size < 10000) {
                    val name = cursor.getString(nameI) ?: continue
                    val title = cursor.getString(titleI)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                    val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idI))
                    val path = if (dataI >= 0) cursor.getString(dataI) else null
                    val folder = path?.let { java.io.File(it).parentFile?.name } ?: "Музыка"
                    val (artist, songName) = splitTitle(title, if (artistI >= 0) cursor.getString(artistI) else null)
                    songs.add(Song(uri.toString(), title, uri, cursor.getLong(durI), name.substringAfterLast('.', "").uppercase(Locale.US), folder, cursor.getLong(addedI), artist, songName))
                }
            }
            setLibrary(songs)
            if (!quiet || songs.isEmpty()) toast(if (songs.isEmpty()) "Музыка не найдена. Возможно, BYD скрывает USB от сторонних приложений."
                  else "Найдено треков: ${songs.size}")
        } catch (e: SecurityException) {
            toast("Нет доступа к аудиофайлам: ${e.localizedMessage}")
        } catch (e: Exception) {
            toast("Ошибка сканирования: ${e.localizedMessage}")
        }
    }
    // Fills the library list on launch (does not touch playback), so no manual scan is needed.
    private fun startupScan() {
        val prefs = getPreferences(MODE_PRIVATE)
        val folder = prefs.getString("folder", null)
        if (prefs.getString("source", "device") == "folder" && folder != null) { scanFolder(Uri.parse(folder), quiet = true); return }
        val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
                         else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) scanDeviceMusic(quiet = true)
        else audioPermission.launch(permission)
    }
    private fun scanFolder(tree: Uri, quiet: Boolean = false) {
        val songs = mutableListOf<Song>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val audioExt = setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac")
        fun visit(docId: String, folderName: String, depth: Int) {
            if (depth > 12 || songs.size >= 3000) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val folders = mutableListOf<Pair<String, String>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol) ?: ""
                    val ext = name.substringAfterLast('.', "")
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) folders.add(id to name)
                    else if (mime.startsWith("audio/") || ext.lowercase() in audioExt) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val base = name.substringBeforeLast('.')
                        val (artist, songName) = splitTitle(base, null)
                        songs.add(Song(uri.toString(), base, uri, 0L, ext.uppercase(Locale.US), folderName, cursor.getLong(modCol), artist, songName))
                    }
                }
                folders.forEach { visit(it.first, it.second, depth + 1) }
            }
        }
        try {
            visit(rootId, rootId.substringAfterLast('/').substringAfterLast(':').ifBlank { "Музыка" }, 0)
        } catch (e: Exception) { toast("Ошибка чтения папки: ${e.localizedMessage}") }
        songs.sortBy { it.title.lowercase() }
        setLibrary(songs)
        if (!quiet || songs.isEmpty()) toast(if (songs.isEmpty()) "В выбранной папке нет аудиофайлов" else "Найдено треков: ${songs.size}")
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        video.stopPlayback()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
