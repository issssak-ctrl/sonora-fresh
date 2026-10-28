package com.sonora.fresh

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
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
    private fun panel(radius: Float = 12f, stroke: Int = steel): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, metalColors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius)
            setStroke(dp(1.5f).toInt(), stroke)
        }
    private fun darkPanel(radius: Float = 14f, stroke: Int = steel): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff121a22.toInt(), 0xff070b10.toInt())).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius)
            setStroke(dp(1.5f).toInt(), stroke)
        }
    private fun activePanel(radius: Float = 12f): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius)
        setColor(0xff06141f.toInt())
        setStroke(dp(2f).toInt(), accent)
    }
    private fun circle(ring: Int = steel, ringWidth: Float = 2f): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, metalColors).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(ringWidth).toInt(), ring)
        }
    private fun chrome(round: Boolean = false): Drawable = if (round) circle() else panel()
    private inner class SpectrumView : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shown = FloatArray(AudioLevels.BANDS)
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
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
                p.color = if (i % 4 == 0) 0xff78edff.toInt() else accent
                c.drawRoundRect(i * spacing + dp(1), height - h, (i + 1) * spacing - dp(1), height.toFloat(), dp(1f), dp(1f), p)
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
        window.statusBarColor = background; window.navigationBarColor = background
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(this@MainActivity.background) }
        header = text("SONORA", 23f, 0xffe6edf3.toInt()).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f; setPadding(dp(18), 0, 0, 0); background = panel(10f) }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(52)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        player = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(player, LinearLayout.LayoutParams(-1, -1))
        gifArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }
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
        details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = darkPanel() }
        trackName = text("Выбери трек в библиотеке", 19f, accent).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(dp(7f), 0f, 0f, 0x8800d1ff.toInt()) }
        trackArtist = text("SONORA · Now Playing", 13f, muted)
        details.addView(trackName, LinearLayout.LayoutParams(-1, -2))
        details.addView(trackArtist, LinearLayout.LayoutParams(-1, dp(28)))
        spectrum = SpectrumView()
        details.addView(spectrum, LinearLayout.LayoutParams(-1, dp(55)).apply { topMargin = dp(7); bottomMargin = dp(8) })
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
        details.addView(controls, LinearLayout.LayoutParams(-1, dp(86)))
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
        seek = SeekBar(this).apply { max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(accent); thumbTintList = android.content.res.ColorStateList.valueOf(accent) }
        details.addView(seek, LinearLayout.LayoutParams(-1, dp(36)))
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
        details.addView(times)
        volumeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        volumeRow.addView(text("−", 22f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(28), dp(38)))
        volume = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0) } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { volumeDragging = true }
            override fun onStopTrackingTouch(bar: SeekBar?) { volumeDragging = false }
        })
        volumeRow.addView(volume, LinearLayout.LayoutParams(0, dp(38), 1f))
        volumeRow.addView(text("+", 22f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(28), dp(38)))
        details.addView(volumeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        configurePlayerLayout()
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        content.addView(library, LinearLayout.LayoutParams(-1, -1))
        library.addView(button("Сканировать музыку / USB", 15f) { requestMusicScan() }, LinearLayout.LayoutParams(-1, dp(52)))
        val pickFolder = button("Выбрать папку (если поддерживается)", 14f) {
            // No resolveActivity() pre-check: on Android 11+ it wrongly returns null without a <queries> entry.
            try {
                folderPicker.launch(null)
            } catch (e: android.content.ActivityNotFoundException) {
                toast("В системе нет выбора папок. Используйте «Сканировать музыку / USB»")
            } catch (e: Exception) {
                toast("Проводник недоступен: ${e.localizedMessage ?: "ошибка системы"}")
            }
        }
        library.addView(pickFolder, LinearLayout.LayoutParams(-1, dp(48)))
        search = EditText(this).apply {
            hint = "Поиск по трекам"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 15f
            isSingleLine = true; setPadding(dp(12), 0, dp(12), 0)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterSongs(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        library.addView(search, LinearLayout.LayoutParams(-1, dp(50)))
        trackList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        library.addView(ScrollView(this).apply { isFillViewport = true; addView(trackList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        settings = ScrollView(this).apply { visibility = View.GONE }
        val settingsItems = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(12)) }
        settings.addView(settingsItems)
        settingsItems.addView(text("Анимация обложки", 20f, accent), LinearLayout.LayoutParams(-1, dp(50)))
        for (i in 1..4) {
            settingsItems.addView(button("Встроенное видео MP4 №$i", 16f) { selectAnimation(i) }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(7) })
        }
        settingsItems.addView(button("Мой GIF", 16f) { gifPicker.launch(arrayOf("image/gif")) }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(7) })
        settingsItems.addView(button("Без анимации", 16f) { selectAnimation(-1) }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(7) })
        settingsItems.addView(text("Автомобильный режим", 20f, accent), LinearLayout.LayoutParams(-1, dp(54)))
        settingsItems.addView(button("Переключить автомобильный режим", 16f) {
            carMode = !carMode
            getPreferences(MODE_PRIVATE).edit().putBoolean("car_mode", carMode).apply()
            toast(if (carMode) "Автомобильный режим включён" else "Автомобильный режим выключен")
            configurePlayerLayout()
        }, LinearLayout.LayoutParams(-1, dp(60)))
        content.addView(settings, LinearLayout.LayoutParams(-1, -1))
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
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶"; spectrum.invalidate() }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateModes() }
                    override fun onRepeatModeChanged(repeatMode: Int) { updateModes() }
                })
                refreshTrack(); updateModes()
            } catch (e: Exception) { trackName.text = "Ошибка Media3: ${e.message}" }
        }, ContextCompat.getMainExecutor(this))
        handler.post { startupScan() }
        handler.post(ticker)
    }
    private fun markTabs(active: TextView) {
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
        header.layoutParams = (header.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 40 else 52) }
        nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 52 else 61) }
        for (t in listOf(playerTab, libraryTab, settingsTab)) {
            t.layoutParams = (t.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 42 else 52) }
        }
    }
    private fun showLibrary(show: Boolean) {
        settings.visibility = View.GONE
        library.visibility = if (show) View.VISIBLE else View.GONE
        player.visibility = if (show) View.GONE else View.VISIBLE
        if (show) video.pause() else if (animationMode > 0) video.start()
        spectrum.invalidate()
        playerTab.setTextColor(if (show) Color.WHITE else accent)
        libraryTab.setTextColor(if (show) accent else Color.WHITE)
        settingsTab.setTextColor(Color.WHITE)
        markTabs(if (show) libraryTab else playerTab)
    }
    private fun showSettings() {
        player.visibility = View.GONE
        library.visibility = View.GONE
        settings.visibility = View.VISIBLE
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
        trackArtist.text = metadata?.artist ?: "SONORA · Now Playing"
        playButton.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }
    private fun configurePlayerLayout() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        player.removeAllViews()
        (gifArea.parent as? ViewGroup)?.removeView(gifArea)
        (details.parent as? ViewGroup)?.removeView(details)
        player.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        player.gravity = if (landscape) Gravity.TOP else Gravity.CENTER_VERTICAL
        if (landscape) {
            player.addView(gifArea, LinearLayout.LayoutParams(0, -2, 1.2f).apply { marginEnd = dp(6) })
            player.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            player.addView(gifArea, LinearLayout.LayoutParams(-1, -2))
            player.addView(details, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        gif.scaleType = ImageView.ScaleType.FIT_CENTER
        gif.adjustViewBounds = true
        gif.maxHeight = if (landscape) (resources.displayMetrics.heightPixels * 0.70f).toInt() else (resources.displayMetrics.heightPixels * 0.52f).toInt()
        // wrap_content and adjustViewBounds preserve aspect ratio; maxHeight prevents oversized GIF.
        gif.layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
        // Compact metrics in landscape so the whole panel fits; roomy in portrait to fill the screen.
        smallSize = if (landscape) 40 else 46
        bigSize = if (landscape) 54 else 68
        (spectrum.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(if (landscape) 38 else 92); topMargin = dp(if (landscape) 4 else 10); bottomMargin = dp(if (landscape) 4 else 10)
        }
        (controlsRow.layoutParams as LinearLayout.LayoutParams).height = dp(if (landscape) 58 else 86)
        (seek.layoutParams as LinearLayout.LayoutParams).height = dp(if (landscape) 28 else 38)
        for (i in 0 until controlsRow.childCount) {
            val lp = controlsRow.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            val size = dp(if (i == 2) bigSize else smallSize)
            lp.width = size; lp.height = size
        }
        details.setPadding(dp(12), dp(if (landscape) 6 else 10), dp(12), dp(if (landscape) 6 else 10))
        trackName.maxLines = if (landscape) 1 else 2
        volumeRow.visibility = if (landscape) View.GONE else View.VISIBLE
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
    private fun filterSongs(query: String) {
        for (i in 0 until trackList.childCount) {
            trackList.getChildAt(i).visibility = if (songTitles.getOrNull(i)?.contains(query, ignoreCase = true) == true) View.VISIBLE else View.GONE
        }
    }
    private fun scanDeviceMusic(quiet: Boolean = false) {
        val items = mutableListOf<MediaItem>()
        val names = mutableListOf<String>()
        try {
            val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.IS_MUSIC)
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.query(collection, columns, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                while (cursor.moveToNext() && items.size < 10000) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                    val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    items.add(MediaItem.Builder().setUri(uri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build())
                    names.add(title)
                }
            }
            showSongs(items, names)
            if (!quiet || items.isEmpty()) toast(if (items.isEmpty()) "Музыка не найдена. Возможно, BYD скрывает USB от сторонних приложений."
                  else "Найдено треков: ${items.size}")
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
    private fun showSongs(items: List<MediaItem>, names: List<String>) {
        songTitles.clear(); songTitles.addAll(names)
        trackList.removeAllViews()
        names.forEachIndexed { index, title ->
            trackList.addView(text("♫   $title", 15f).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    controller?.apply { setMediaItems(items, index, 0); prepare(); play() }
                    trackName.text = items[index].mediaMetadata.title ?: title
                    showLibrary(false)
                }
            }, LinearLayout.LayoutParams(-1, dp(52)))
        }
        filterSongs(search.text.toString())
    }
    private fun scanFolder(tree: Uri, quiet: Boolean = false) {
        val items = mutableListOf<MediaItem>()
        songTitles.clear(); trackList.removeAllViews()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        fun visit(docId: String, depth: Int) {
            if (depth > 12 || items.size >= 3000) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val folders = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol); val name = cursor.getString(nameCol); val mime = cursor.getString(mimeCol)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) folders.add(id)
                    else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac")) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        songTitles.add(name)
                        items.add(MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(name.substringBeforeLast('.')).build()).build())
                    }
                }
                folders.forEach { visit(it, depth + 1) }
            }
        }
        try { visit(rootId, 0) } catch (e: Exception) { toast("Ошибка чтения папки: ${e.localizedMessage}") }
        showSongs(items, songTitles.toList())
        if (!quiet || items.isEmpty()) toast(if (items.isEmpty()) "В выбранной папке нет аудиофайлов" else "Найдено треков: ${items.size}")
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        video.stopPlayback()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
