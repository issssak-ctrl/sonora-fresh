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
        val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
                         else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            scanDeviceMusic()
        } else audioPermission.launch(permission)
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            getPreferences(MODE_PRIVATE).edit().putString("folder", uri.toString()).apply()
            scanFolder(uri)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    // Flat cyan outlined panels: predictable on all densities, no bitmap assets.
    private fun panel(radius: Float = 12f, stroke: Int = accent): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius)
        setColor(0xff06111c.toInt())
        setStroke(dp(1), stroke)
    }
    private fun circle(): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xff030a12.toInt())
        setStroke(dp(1.5f).toInt(), accent)
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
        window.statusBarColor = background; window.navigationBarColor = background
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(this@MainActivity.background) }
        val header = text("●  SONORA", 23f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(16), 0, 0, 0); background = panel(0f, 0xff153d50.toInt()) }
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
        video = VideoView(this).apply { visibility = View.GONE }
        mediaBox.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        gifArea.addView(mediaBox, LinearLayout.LayoutParams(-1, -2))
        gifChoose = button("Выбрать свой GIF", 13f) { gifPicker.launch(arrayOf("image/gif")) }
        gifArea.addView(gifChoose, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = panel() }
        trackName = text("Выбери трек в библиотеке", 19f, accent).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD }
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
            view.background = circle()
            // Equal width and height are essential: circles must never turn into ovals.
            val size = if (index == 2) dp(62) else dp(44)
            controls.addView(view, LinearLayout.LayoutParams(size, size).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        details.addView(controls, LinearLayout.LayoutParams(-1, dp(76)))
        controls.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val row = v as LinearLayout
            val available = row.width - row.paddingLeft - row.paddingRight
            if (available <= 0) return@addOnLayoutChangeListener
            val small = minOf(dp(44), (available - dp(62) - dp(30)) / 4).coerceAtLeast(dp(26))
            val large = minOf(dp(62), (available - dp(30) - 4 * small)).coerceAtLeast(dp(34))
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
        configurePlayerLayout()
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        content.addView(library, LinearLayout.LayoutParams(-1, -1))
        library.addView(button("Сканировать музыку / USB", 15f) { requestMusicScan() }, LinearLayout.LayoutParams(-1, dp(52)))
        val pickFolder = button("Выбрать папку (если поддерживается)", 14f) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addCategory(Intent.CATEGORY_DEFAULT)
            if (intent.resolveActivity(packageManager) == null) {
                toast("В системе нет выбора папок. Используйте «Сканировать музыку / USB»")
            } else try {
                folderPicker.launch(null)
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
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; background = panel(); setPadding(dp(4), dp(3), dp(4), dp(3)) }
        playerTab = button("Плеер", 15f) { showLibrary(false) }
        libraryTab = button("Библиотека", 15f) { showLibrary(true) }
        settingsTab = button("Настройки", 15f) { showSettings() }
        nav.addView(playerTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        nav.addView(libraryTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(5) })
        nav.addView(settingsTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(5) })
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(61)))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(12), bars.top + dp(3), dp(12), bars.bottom + dp(5))
            insets
        }
        setContentView(root)
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
        getPreferences(MODE_PRIVATE).getString("folder", null)?.let { scanFolder(Uri.parse(it)) }
        handler.post(ticker)
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
    }
    private fun showSettings() {
        player.visibility = View.GONE
        library.visibility = View.GONE
        settings.visibility = View.VISIBLE
        video.pause()
        playerTab.setTextColor(Color.WHITE)
        libraryTab.setTextColor(Color.WHITE)
        settingsTab.setTextColor(accent)
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
        if (animationMode > 0 && ::video.isInitialized) mediaBox.post { displayAnimation() }
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); configurePlayerLayout() }
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
    private fun scanDeviceMusic() {
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
            toast(if (items.isEmpty()) "Музыка не найдена. Возможно, BYD скрывает USB от сторонних приложений."
                  else "Найдено треков: ${items.size}")
        } catch (e: SecurityException) {
            toast("Нет доступа к аудиофайлам: ${e.localizedMessage}")
        } catch (e: Exception) {
            toast("Ошибка сканирования: ${e.localizedMessage}")
        }
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
                    trackName.text = title.substringBeforeLast('.')
                    showLibrary(false)
                }
            }, LinearLayout.LayoutParams(-1, dp(52)))
        }
        filterSongs(search.text.toString())
    }
    private fun scanFolder(tree: Uri) {
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
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        video.stopPlayback()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
