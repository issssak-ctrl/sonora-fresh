package com.sonora.fresh

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var saveState: (() -> Unit)? = null
    private val stateHandler = Handler(Looper.getMainLooper())
    override fun onCreate() {
        super.onCreate()
        SoftEq.load(this)
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(EqAudioProcessor(), VisualizerAudioProcessor()))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        session = MediaSession.Builder(this, player).build()

        // Remember what was playing (track, position, shuffle/repeat) so the app can restore it after a restart.
        val state = getSharedPreferences("sonora_state", MODE_PRIVATE)
        val save = {
            val id = player.currentMediaItem?.mediaId
            if (!id.isNullOrEmpty()) {
                state.edit()
                    .putString("last_id", id)
                    .putLong("last_pos", player.currentPosition.coerceAtLeast(0L))
                    .putBoolean("last_shuffle", player.shuffleModeEnabled)
                    .putInt("last_repeat", player.repeatMode)
                    .apply()
            }
        }
        saveState = save
        val tick = object : Runnable {
            override fun run() {
                save()
                if (player.isPlaying) stateHandler.postDelayed(this, 10_000L)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { save() }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                save()
                stateHandler.removeCallbacks(tick)
                if (isPlaying) stateHandler.postDelayed(tick, 10_000L)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { save() }
            override fun onRepeatModeChanged(repeatMode: Int) { save() }
        })
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        saveState?.invoke()
        stateHandler.removeCallbacksAndMessages(null)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
