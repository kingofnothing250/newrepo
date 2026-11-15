package com.nyxora.music.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nyxora.music.MainActivity
import com.nyxora.music.audio.HighResAudioEngine

@OptIn(UnstableApi::class)
class NyxoraPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        var audioEngine: HighResAudioEngine? = null
    }

    override fun onCreate() {
        super.onCreate()
        val engine = HighResAudioEngine(this)
        audioEngine = engine

        engine.player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                engine.attachHardwareDsp(audioSessionId)
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, engine.player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        audioEngine?.release()
        audioEngine = null
        super.onDestroy()
    }
}
