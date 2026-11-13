package com.jssofttools.gameraudio

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder
import kotlin.math.log10

/** Applies global (session 0) audio effects so game / media sound gets louder. */
class BoostService : Service() {
    companion object { @Volatile var running = false }

    private var le: LoudnessEnhancer? = null
    private var eq: Equalizer? = null
    private var bb: BassBoost? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        try { le = LoudnessEnhancer(0) } catch (_: Throwable) {}
        try { eq = Equalizer(0, 0) } catch (_: Throwable) {}
        try { bb = BassBoost(0, 0) } catch (_: Throwable) {}
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        val n = Notif.make(this, "Game Sound Booster", "Boost is ON")
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
        i?.let {
            apply(it.getIntExtra("gain", 300), it.getIntExtra("bass", 0), it.getBooleanExtra("foot", false))
        }
        return START_STICKY
    }

    private fun apply(pct: Int, bass: Int, foot: Boolean) {
        try {
            val mb = (2000.0 * log10(pct.coerceAtLeast(100) / 100.0)).toInt()
            le?.setTargetGain(mb); le?.enabled = true
        } catch (_: Throwable) {}
        try {
            bb?.let {
                if (it.strengthSupported) {
                    it.setStrength(bass.coerceIn(0, 1000).toShort()); it.enabled = bass > 0
                }
            }
        } catch (_: Throwable) {}
        try {
            eq?.let { e ->
                val r = e.bandLevelRange
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000
                    val lvl: Short = if (foot && hz in 1500..9000) r[1] else 0
                    e.setBandLevel(b.toShort(), lvl)
                }
                e.enabled = foot
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        running = false
        try { le?.enabled = false; le?.release() } catch (_: Throwable) {}
        try { eq?.enabled = false; eq?.release() } catch (_: Throwable) {}
        try { bb?.enabled = false; bb?.release() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
