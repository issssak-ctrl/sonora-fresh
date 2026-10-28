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
    private class Song(val id: String, val title: String, val uri: Uri, val durationMs: Long, val format: String, val folder: String, val added: Long, val artist: String, val name: String)
    private class Row(val song: Song?, val folder: String?, val count: Int)
    private class RowHolder(val root: LinearLayout, val icon: TextView, val title: TextView, val sub: TextView)
    private val allSongs = mutableListOf<Song>()
    private val spacers = mutableListOf<View>()
    private val favorites = mutableSetOf<String>()
    private val chipModes = listOf(0, 3, 1, 2)
    private val iconSearch = 0
    private val iconDots = 1
    private val iconHeart = 2
    private val iconSpeakerLow = 3
    private val iconSpeakerHigh = 4
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
                val bw = spacing * 0.5f
                val bx = i * spacing + (spacing - bw) / 2f
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
        favorites.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet())
        window.statusBarColor = background; window.navigationBarColor = background
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(this@MainActivity.background) }
        header = text("SONORA", 23f, 0xffe6edf3.toInt()).apply { typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f; setPadding(dp(18), 0, 0, 0) }
        headerBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = panel(10f); setPadding(0, 0, dp(6), 0) }
        headerBar.addView(header, LinearLayout.LayoutParams(0, -1, 1f))
        headerBar.addView(IconView(iconSearch).apply { setOnClickListener { openSearch() } }, LinearLayout.LayoutParams(dp(44), -1))
        headerBar.addView(IconView(iconDots).apply { setOnClickListener { showSettings() } }, LinearLayout.LayoutParams(dp(40), -1))
        root.addView(headerBar, LinearLayout.LayoutParams(-1, dp(52)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        player = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(player, LinearLayout.LayoutParams(-1, -1))
        gifArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP; background = panel(8f); setPadding(dp(3), dp(3), dp(3), dp(3)) }
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
        trackArtist = text("", 14f, 0xffb8c4cf.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        heart = IconView(iconHeart).apply { setOnClickListener { toggleFavorite() } }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(trackArtist, LinearLayout.LayoutParams(-1, -2))
            addView(trackName, LinearLayout.LayoutParams(-1, -2))
        }
        titleRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(heart, LinearLayout.LayoutParams(dp(40), dp(40)))
        details.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
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
        details.addView(spacer(), LinearLayout.LayoutParams(-1, 0, 1f))
        details.addView(controls, LinearLayout.LayoutParams(-1, dp(86)))
        details.addView(spacer(), LinearLayout.LayoutParams(-1, 0, 1f))
        volumeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        volumeRow.addView(IconView(iconSpeakerLow), LinearLayout.LayoutParams(dp(34), dp(38)))
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
        volumeRow.addView(IconView(iconSpeakerHigh), LinearLayout.LayoutParams(dp(34), dp(38)))
        details.addView(volumeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
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
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶"; spectrum.invalidate(); updateMini() }
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
        headerBar.layoutParams = (headerBar.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 40 else 52) }
        nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 52 else 61) }
        for (t in listOf(playerTab, libraryTab, settingsTab)) {
            t.layoutParams = (t.layoutParams as LinearLayout.LayoutParams).apply { height = dp(if (landscape) 42 else 52) }
        }
        layoutLibrary()
        updateMini()
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
        smallSize = if (landscape) 40 else 46
        bigSize = if (landscape) 54 else 68
        val minSpectrum = if (resources.configuration.screenHeightDp < 700) 56 else 92
        spectrum.minimumHeight = dp(if (landscape) 38 else minSpectrum)
        (spectrum.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (landscape) dp(38) else 0
            weight = if (landscape) 0f else 3f
            topMargin = dp(if (landscape) 4 else 10); bottomMargin = dp(if (landscape) 4 else 10)
        }
        for (sp in spacers) sp.visibility = if (landscape) View.GONE else View.VISIBLE
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
    private fun spacer(): View = View(this).also { spacers.add(it) }
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
    private fun setLibrary(songs: List<Song>) {
        allSongs.clear(); allSongs.addAll(songs)
        selectedFolder = null
        applyFilter()
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
