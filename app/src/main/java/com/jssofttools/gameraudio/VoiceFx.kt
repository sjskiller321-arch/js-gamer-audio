package com.jssofttools.gameraudio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

enum class Voice(val label: String) {
    NORMAL("Normal"), DEEP("Deep man"), GIANT("Giant"), CHIPMUNK("Chipmunk"),
    ROBOT("Robot"), ALIEN("Alien"), MEGAPHONE("Megaphone / radio")
}

/** Simple real-time voice DSP: pitch shift, ring-mod, distortion, echo, gain + soft clip. */
class VoiceFx(private val sr: Int) {
    private val w = sr * 0.03f
    private val n = (w + 8).toInt()
    private val buf = FloatArray(n)
    private var wi = 0
    private var d = 0f

    private val echoBuf = FloatArray((sr * 0.28).toInt())
    private var ei = 0
    private var phase = 0.0
    private var lp = 0f

    private fun read(delay: Float): Float {
        var pos = wi - delay
        while (pos < 0) pos += n
        val i = pos.toInt()
        val f = pos - i
        return buf[i % n] * (1 - f) + buf[(i + 1) % n] * f
    }

    private fun tri(x: Float) = 1f - abs(2f * x / w - 1f)

    private fun pitch(x: Float, r: Float): Float {
        buf[wi] = x
        d += (1f - r)
        if (d < 0) d += w else if (d >= w) d -= w
        var d2 = d + w / 2f
        if (d2 >= w) d2 -= w
        val y = read(d) * tri(d) + read(d2) * tri(d2)
        wi = (wi + 1) % n
        return y
    }

    private fun ring(x: Float, hz: Double, mix: Float): Float {
        phase += 2 * PI * hz / sr
        if (phase > 2 * PI) phase -= 2 * PI
        return x * (1 - mix) + x * sin(phase).toFloat() * mix
    }

    fun process(x: Float, v: Voice, gain: Float, echo: Boolean): Float {
        var y = when (v) {
            Voice.NORMAL -> x
            Voice.DEEP -> pitch(x, 0.78f)
            Voice.GIANT -> pitch(x, 0.62f)
            Voice.CHIPMUNK -> pitch(x, 1.55f)
            Voice.ROBOT -> ring(x, 55.0, 0.9f)
            Voice.ALIEN -> ring(pitch(x, 1.2f), 320.0, 0.5f)
            Voice.MEGAPHONE -> {
                lp += 0.1f * (x - lp)
                val hp = x - lp
                (hp * 4f).coerceIn(-0.6f, 0.6f)
            }
        }
        y *= gain
        if (echo) {
            val wet = echoBuf[ei]
            echoBuf[ei] = y + wet * 0.45f
            ei = (ei + 1) % echoBuf.size
            y += wet * 0.6f
        }
        return if (gain > 1f || echo) tanh(y) else y
    }
}
