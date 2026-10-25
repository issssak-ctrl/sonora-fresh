package com.sonora.fresh

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.res.Configuration
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.os.Build
import android.widget.ImageView
import android.graphics.Color
import android.graphics.Typeface
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private lateinit var tracks: LinearLayout
    private lateinit var now: TextView
    private lateinit var play: Button
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val titles = mutableListOf<String>()
    private lateinit var gifView: ImageView
    private lateinit var gifContainer: LinearLayout
    private lateinit var playerPane: LinearLayout
    private lateinit var libraryPane: LinearLayout
    private lateinit var mainArea: LinearLayout
    private lateinit var seekBar: SeekBar
    private var gifUri: Uri? = null
    private val gifPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            gifUri = uri
            getPreferences(MODE_PRIVATE).edit().putString("gif_uri", uri.toString()).apply()
            showGif()
        }
    }
    private fun showGif() {
        val uri = gifUri ?: return
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(contentResolver, uri))
                gifView.setImageDrawable(drawable)
                (drawable as? Animatable)?.start()
            } else {
                gifView.setImageURI(uri)
            }
        } catch (e: Exception) {
            now.text = "Не удалось открыть GIF: ${e.message}"
        }
    }
    private fun applyOrientation() {
        val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        mainArea.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        mainArea.removeAllViews()
        (gifContainer.parent as? ViewGroup)?.removeView(gifContainer)
        (playerPane.parent as? ViewGroup)?.removeView(playerPane)
        if (wide) {
            mainArea.addView(gifContainer, LinearLayout.LayoutParams(0, -1, 1f))
            mainArea.addView(playerPane, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            mainArea.addView(gifContainer, LinearLayout.LayoutParams(-1, 0, 1f))
            mainArea.addView(playerPane, LinearLayout.LayoutParams(-1, -2))
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation()
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getPreferences(MODE_PRIVATE).edit().putString("folder", uri.toString()).apply()
            scanFolder(uri)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5,9,15)
        window.navigationBarColor = Color.rgb(5,9,15)
        val bg = Color.rgb(5, 9, 15)
        val accent = Color.rgb(35, 201, 255)
        fun label(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE); setPadding(8, 8, 8, 8)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 8, 14, 8)
            setBackgroundColor(bg)
        }
        root.addView(label("SONORA", 25f).apply { setTextColor(accent); setTypeface(null, Typeface.BOLD) })
        mainArea = LinearLayout(this)
        root.addView(mainArea, LinearLayout.LayoutParams(-1, 0, 1f))
        gifContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 6, 6, 6)
        }
        gifView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(10, 20, 32))
            contentDescription = "Анимированная обложка"
        }
        gifContainer.addView(gifView, LinearLayout.LayoutParams(-1, 0, 1f))
        gifContainer.addView(Button(this).apply {
            text = "ВЫБРАТЬ GIF ВМЕСТО ОБЛОЖКИ"
            setOnClickListener { gifPicker.launch(arrayOf("image/gif")) }
        })
        playerPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 4, 12, 4)
        }
        now = label("Выбери трек", 18f).apply { setTextColor(accent); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        playerPane.addView(now)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun control(value: String, action: () -> Unit) = Button(this).apply {
            text = value; setOnClickListener { action() }
            row.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        control("◀◀") { controller?.seekToPreviousMediaItem() }
        play = Button(this).apply {
            text = "▶"; setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        }
        row.addView(play, LinearLayout.LayoutParams(0, -2, 1f))
        control("▶▶") { controller?.seekToNextMediaItem() }
        playerPane.addView(row)
        seekBar = SeekBar(this)
        playerPane.addView(seekBar)
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) controller?.let { if (it.duration > 0) it.seekTo(it.duration * progress / 1000) }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        applyOrientation()
        libraryPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryPane.addView(Button(this).apply {
            text = "ВЫБРАТЬ ПАПКУ С МУЗЫКОЙ"
            setOnClickListener { folderPicker.launch(null) }
        })
        tracks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryPane.addView(ScrollView(this).apply { addView(tracks) }, LinearLayout.LayoutParams(-1, 0, 1f))
        libraryPane.visibility = View.GONE
        root.addView(libraryPane, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val playerTab = Button(this).apply { text = "ПЛЕЕР"; setTextColor(accent) }
        val libraryTab = Button(this).apply { text = "БИБЛИОТЕКА" }
        fun showTab(library: Boolean) {
            mainArea.visibility = if (library) View.GONE else View.VISIBLE
            libraryPane.visibility = if (library) View.VISIBLE else View.GONE
            playerTab.setTextColor(if (library) Color.WHITE else accent)
            libraryTab.setTextColor(if (library) accent else Color.WHITE)
        }
        playerTab.setOnClickListener { showTab(false) }
        libraryTab.setOnClickListener { showTab(true) }
        nav.addView(playerTab, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(libraryTab, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(14, bars.top + 8, 14, bars.bottom + 8)
            insets
        }
        setContentView(root)
        getPreferences(MODE_PRIVATE).getString("gif_uri", null)?.let {
            gifUri = Uri.parse(it); showGif()
        }
        val token=SessionToken(this, android.content.ComponentName(this,PlaybackService::class.java))
        controllerFuture=MediaController.Builder(this,token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller=controllerFuture?.get()
                controller?.addListener(object:Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason:Int) { runOnUiThread { now.text=mediaItem?.mediaMetadata?.title ?: "SONORA" } }
                    override fun onIsPlayingChanged(isPlaying:Boolean) { runOnUiThread { play.text=if(isPlaying) "Ⅱ" else "▶" } }
                })
                now.text=controller?.currentMediaItem?.mediaMetadata?.title ?: "Выбери трек"
            } catch(e:Exception) { now.text="Ошибка аудиодвижка: ${e.message}" }
        }, ContextCompat.getMainExecutor(this))
        getPreferences(MODE_PRIVATE).getString("folder",null)?.let { scanFolder(Uri.parse(it)) }
        val handler=android.os.Handler(mainLooper)
        val updater=object:Runnable { override fun run() { controller?.let { if(it.duration>0) seekBar.progress=(it.currentPosition*1000/it.duration).toInt().coerceIn(0,1000) }; handler.postDelayed(this,500) } }
        handler.post(updater)
        window.decorView.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:android.view.View) {}
            override fun onViewDetachedFromWindow(v:android.view.View) { handler.removeCallbacks(updater) }
        })
    }
    private fun scanFolder(tree:Uri) {
        titles.clear(); tracks.removeAllViews()
        val items=mutableListOf<MediaItem>()
        val rootId=DocumentsContract.getTreeDocumentId(tree)
        fun visit(docId:String, depth:Int) {
            if(depth>12 || items.size>=3000) return
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,docId)
            contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { cursor ->
                val idIndex=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val folders=mutableListOf<String>()
                while(cursor.moveToNext()) {
                    val id=cursor.getString(idIndex); val name=cursor.getString(nameIndex); val mime=cursor.getString(mimeIndex)
                    if(mime==DocumentsContract.Document.MIME_TYPE_DIR) folders.add(id)
                    else if(mime.startsWith("audio/") || name.substringAfterLast('.',"").lowercase() in setOf("mp3","flac","wav","m4a","ogg","opus","aac")) {
                        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
                        titles.add(name)
                        items.add(MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(name.substringBeforeLast('.')).build()).build())
                    }
                }
                folders.forEach { visit(it,depth+1) }
            }
        }
        try { visit(rootId,0) } catch(e:Exception) { now.text="Не удалось прочитать папку: ${e.message}" }
        titles.forEachIndexed { index,title ->
            tracks.addView(TextView(this).apply {
                text="♫   $title"; textSize=16f; setTextColor(-1); setPadding(12,20,12,20)
                setOnClickListener {
                    controller?.apply { setMediaItems(items,index,0); prepare(); play() }
                    now.text=title
                }
            })
        }
        if(items.isNotEmpty() && controller?.currentMediaItem == null) now.text="Найдено треков: ${items.size}"
        else now.text="Музыка не найдена в выбранной папке"
    }
    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
}
