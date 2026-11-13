package com.jssofttools.gameraudio

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder

/** Live mic -> effects -> headphones/speaker (use headphones to avoid feedback). */
class VoiceService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var voice = Voice.NORMAL
        @Volatile var gainPct = 150
        @Volatile var echo = false
        @Volatile var noise = true
    }

    @Volatile private var alive = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); running = true }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        val n = Notif.make(this, "Voice Studio", "Live voice is ON")
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(2, n)
        if (!alive) {
            alive = true
            Thread { loop() }.start()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val sr = 44100
        val frame = 512
        var rec: AudioRecord? = null
        var trk: AudioTrack? = null
        var ns: NoiseSuppressor? = null
        var agc: AutomaticGainControl? = null
        try {
            val minIn = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val minOut = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, frame * 8)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) return
            if (noise) {
                try { if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(rec.audioSessionId) } catch (_: Throwable) {}
                try { if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(rec.audioSessionId) } catch (_: Throwable) {}
            }
            trk = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(maxOf(minOut, frame * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            rec.startRecording(); trk.play()
            val fx = VoiceFx(sr)
            val inBuf = ShortArray(frame)
            val out = ShortArray(frame)
            while (alive) {
                val n = rec.read(inBuf, 0, frame)
                if (n <= 0) continue
                val v = voice; val g = gainPct / 100f; val e = echo
                for (i in 0 until n) {
                    val y = fx.process(inBuf[i] / 32768f, v, g, e)
                    out[i] = (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                trk.write(out, 0, n)
            }
        } catch (_: Throwable) {
        } finally {
            try { ns?.release() } catch (_: Throwable) {}
            try { agc?.release() } catch (_: Throwable) {}
            try { rec?.stop() } catch (_: Throwable) {}
            try { rec?.release() } catch (_: Throwable) {}
            try { trk?.stop() } catch (_: Throwable) {}
            try { trk?.release() } catch (_: Throwable) {}
            alive = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        alive = false
        running = false
        super.onDestroy()
    }
}
