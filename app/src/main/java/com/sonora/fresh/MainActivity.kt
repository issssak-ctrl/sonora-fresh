package com.sonora.fresh

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
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
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getPreferences(MODE_PRIVATE).edit().putString("folder", uri.toString()).apply()
            scanFolder(uri)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = android.graphics.Color.rgb(5, 9, 15)
        val accent = android.graphics.Color.rgb(35, 201, 255)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 35, 22, 22); setBackgroundColor(bg) }
        fun label(text: String, size: Float) = TextView(this).apply { this.text=text; textSize=size; setTextColor(-1); setPadding(0,10,0,10) }
        root.addView(label("SONORA", 28f))
        root.addView(label("Музыкальная библиотека • версия 1.0", 14f))
        root.addView(Button(this).apply { text="Выбрать папку с музыкой"; setOnClickListener { folderPicker.launch(null) } })
        now = label("Выбери папку с музыкой", 17f); now.setTextColor(accent); root.addView(now)
        val row = LinearLayout(this).apply { gravity=Gravity.CENTER }
        fun control(text: String, action: () -> Unit) = Button(this).apply { this.text=text; setOnClickListener { action() }; row.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        control("◀◀") { controller?.seekToPreviousMediaItem() }
        play=Button(this).apply { text="▶"; setOnClickListener { controller?.let { if(it.isPlaying) it.pause() else it.play() } }; row.addView(this,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)) }
        control("▶▶") { controller?.seekToNextMediaItem() }
        root.addView(row)
        val seek=SeekBar(this); root.addView(seek)
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress:Int, fromUser:Boolean) { if(fromUser) controller?.let { if(it.duration>0) it.seekTo(it.duration*progress/1000) } }
            override fun onStartTrackingTouch(bar:SeekBar?) {}
            override fun onStopTrackingTouch(bar:SeekBar?) {}
        })
        tracks=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(tracks) },LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
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
        val updater=object:Runnable { override fun run() { controller?.let { if(it.duration>0) seek.progress=(it.currentPosition*1000/it.duration).toInt().coerceIn(0,1000) }; handler.postDelayed(this,500) } }
        handler.post(updater)
        window.decorView.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v:android.view.View) {}
            override fun onViewDetachedFromWindow(v:android.view) { handler.removeCallbacks(updater) }
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
        if(items.isNotEmpty()) now.text="Найдено треков: ${items.size}"
        else now.text="Музыка не найдена в выбранной папке"
    }
    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
}
