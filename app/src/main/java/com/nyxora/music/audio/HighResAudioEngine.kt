package com.nyxora.music.audio

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@OptIn(UnstableApi::class)
class HighResAudioEngine(context: Context) {

    private var dynamicsProcessing: DynamicsProcessing? = null
    val eqFreqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val bandGains = FloatArray(10) { 0f }

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true) 
                    .setEnableAudioTrackPlaybackParams(true)
                    .build()
            }
        }
    )
    .setLoadControl(
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 450, 1_200)
            .build()
    )
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build(),
        true
    )
    .setHandleAudioBecomingNoisy(true)
    .build()

    fun attachHardwareDsp(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            dynamicsProcessing?.release()
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, true, 10, true, 6, true, 10, true
            ).build()

            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -5f, -0.3f)
                setLimiterAllChannelsTo(limiter)
                for (i in eqFreqs.indices) {
                    setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, eqFreqs[i], bandGains[i]))
                }
                enabled = true
            }
        }
    }

    fun setBandGain(index: Int, db: Float) {
        if (index !in 0..9) return
        bandGains[index] = db.coerceIn(-12f, 12f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.setPreEqBandAllChannelsTo(
                index,
                DynamicsProcessing.EqBand(true, eqFreqs[index], bandGains[index])
            )
        }
    }

    fun setSubBassBoost(percent: Int) {
        val db = (percent.coerceIn(0, 100) / 100f) * 9.5f
        setBandGain(0, db)
        setBandGain(1, db * 0.8f)
    }

    fun release() {
        runCatching { dynamicsProcessing?.release() }
        player.release()
    }
}
