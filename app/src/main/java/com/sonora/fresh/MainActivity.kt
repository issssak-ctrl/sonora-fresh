package com.sonora.fresh

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
    private val accent = Color.rgb(19, 201, 248)
    private val muted = Color.rgb(154, 173, 189)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var gifUri: Uri? = null
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var library: LinearLayout
    private lateinit var player: LinearLayout
    private lateinit var gif: ImageView
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
            getPreferences(MODE_PRIVATE).edit().putString("gif_uri", uri.toString()).apply()
            displayGif()
        }
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
    private fun pill(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(135, 19, 40, 56)); cornerRadius = dp(80).toFloat()
        setStroke(dp(1), Color.rgb(36, 82, 106))
    }
    private fun button(value: String, size: Float = 18f, action: () -> Unit): TextView = text(value, size).apply {
        gravity = Gravity.CENTER; background = pill(); setOnClickListener { action() }
    }
    private fun formatTime(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background; window.navigationBarColor = background
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(background) }
        val header = text("SONORA", 22f, accent).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, 0) }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        player = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(player, LinearLayout.LayoutParams(-1, -1))
        gifArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        gif = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "GIF вместо обложки"
        }
        gifArea.addView(gif, LinearLayout.LayoutParams(-1, -2))
        gifChoose = button("Выбрать GIF", 13f) { gifPicker.launch(arrayOf("image/gif")) }
        gifArea.addView(gifChoose, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        trackName = text("Выбери трек в библиотеке", 19f, accent).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD }
        trackArtist = text("SONORA · Now Playing", 13f, muted)
        details.addView(trackName, LinearLayout.LayoutParams(-1, -2))
        details.addView(trackArtist, LinearLayout.LayoutParams(-1, dp(28)))
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
            controls.addView(view, LinearLayout.LayoutParams(0, dp(if (index == 2) 62 else 48), if (index == 2) 1.3f else 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        details.addView(controls, LinearLayout.LayoutParams(-1, dp(82)))
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
        val pickFolder = button("Выбрать папку с музыкой", 15f) { folderPicker.launch(null) }
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
        library.addView(ScrollView(this).apply { fillViewport = true; addView(trackList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        playerTab = button("Плеер", 15f) { showLibrary(false) }
        libraryTab = button("Библиотека", 15f) { showLibrary(true) }
        nav.addView(playerTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        nav.addView(libraryTab, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(5) })
        root.addView(nav)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(12), bars.top + dp(3), dp(12), bars.bottom + dp(5))
            insets
        }
        setContentView(root)
        showLibrary(false)
        getPreferences(MODE_PRIVATE).getString("gif_uri", null)?.let { gifUri = Uri.parse(it); displayGif() }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { refreshTrack() }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶" }
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
        library.visibility = if (show) View.VISIBLE else View.GONE
        player.visibility = if (show) View.GONE else View.VISIBLE
        playerTab.setTextColor(if (show) Color.WHITE else accent)
        libraryTab.setTextColor(if (show) accent else Color.WHITE)
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
        if (landscape) {
            player.addView(gifArea, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(14) })
            player.addView(details, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            player.addView(gifArea, LinearLayout.LayoutParams(-1, 0, 1f))
            player.addView(details, LinearLayout.LayoutParams(-1, -2))
        }
        gif.scaleType = ImageView.ScaleType.FIT_CENTER
        gif.adjustViewBounds = true
        gif.maxHeight = if (landscape) (resources.displayMetrics.heightPixels * 0.70f).toInt() else (resources.displayMetrics.heightPixels * 0.52f).toInt()
        // wrap_content and adjustViewBounds preserve aspect ratio; maxHeight prevents oversized GIF.
        gif.layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); configurePlayerLayout() }
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
        try { visit(rootId, 0) } catch (e: Exception) { trackArtist.text = "Ошибка папки: ${e.message}" }
        songTitles.forEachIndexed { index, title ->
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
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
