package com.sonora.fresh

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Equalizer settings shared between the UI and the audio thread (same process). */
object SoftEq {
    const val BANDS = 10
    val freqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    class Params(val enabled: Boolean, val gains: FloatArray, val bassDb: Float)

    @Volatile var params = Params(false, FloatArray(BANDS), 0f)

    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences("sonora_eq", Context.MODE_PRIVATE)
        val g = FloatArray(BANDS)
        sp.getString("gains", null)?.split(",")?.forEachIndexed { i, v -> if (i < BANDS) g[i] = v.toFloatOrNull() ?: 0f }
        params = Params(sp.getBoolean("enabled", false), g, sp.getFloat("bass", 0f))
    }

    fun save(ctx: Context) {
        val p = params
        ctx.getSharedPreferences("sonora_eq", Context.MODE_PRIVATE).edit()
            .putString("gains", p.gains.joinToString(","))
            .putBoolean("enabled", p.enabled)
            .putFloat("bass", p.bassDb)
            .apply()
    }
}

/**
 * Software equalizer: 10 peaking filters + bass low-shelf (RBJ biquads) applied to the PCM stream.
 * Works the same on every device (system audio effects are often ignored by car head units).
 */
class EqAudioProcessor : BaseAudioProcessor() {
    private val stages = SoftEq.BANDS + 1
    private val b0 = DoubleArray(stages)
    private val b1 = DoubleArray(stages)
    private val b2 = DoubleArray(stages)
    private val a1 = DoubleArray(stages)
    private val a2 = DoubleArray(stages)
    private val activeIdx = IntArray(stages)
    private var activeCount = 0
    private var preGain = 1.0
    private val z1 = Array(8) { DoubleArray(stages) }
    private val z2 = Array(8) { DoubleArray(stages) }
    private var channels = 2
    private var sampleRate = 44100
    private var applied: SoftEq.Params? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..8) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        applied = null
        for (ch in 0 until 8) { z1[ch].fill(0.0); z2[ch].fill(0.0) }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val p = SoftEq.params
        if (p !== applied) { recompute(p); applied = p }
        val out = replaceOutputBuffer(size)
        if (activeCount == 0) {
            out.put(inputBuffer)
        } else {
            inputBuffer.order(ByteOrder.nativeOrder())
            out.order(ByteOrder.nativeOrder())
            val frameBytes = channels * 2
            while (inputBuffer.remaining() >= frameBytes) {
                for (ch in 0 until channels) {
                    var x = inputBuffer.getShort() / 32768.0 * preGain
                    val s1 = z1[ch]
                    val s2 = z2[ch]
                    for (k in 0 until activeCount) {
                        val i = activeIdx[k]
                        val y = b0[i] * x + s1[i]
                        s1[i] = b1[i] * x - a1[i] * y + s2[i]
                        s2[i] = b2[i] * x - a2[i] * y
                        x = y
                    }
                    // soft ceiling instead of harsh digital clipping
                    if (x > 0.9) x = 0.9 + 0.1 * tanh((x - 0.9) / 0.1)
                    else if (x < -0.9) x = -0.9 - 0.1 * tanh((-x - 0.9) / 0.1)
                    out.putShort((x * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
            out.put(inputBuffer) // any trailing partial frame
        }
        out.flip()
    }

    private fun recompute(p: SoftEq.Params) {
        activeCount = 0
        var maxBoost = 0f
        if (p.enabled) {
            for (i in 0 until SoftEq.BANDS) {
                val g = p.gains[i]
                val fc = SoftEq.freqs[i]
                if (abs(g) < 0.1f || fc >= sampleRate * 0.45f) continue
                peaking(i, fc.toDouble(), g.toDouble(), 1.41)
                activeIdx[activeCount++] = i
                if (g > maxBoost) maxBoost = g
            }
            if (p.bassDb >= 0.1f) {
                lowShelf(SoftEq.BANDS, 100.0, p.bassDb.toDouble())
                activeIdx[activeCount++] = SoftEq.BANDS
                if (p.bassDb > maxBoost) maxBoost = p.bassDb
            }
        }
        // headroom: lower the level a bit when boosting so peaks don't clip
        preGain = 10.0.pow(-0.7 * maxBoost / 20.0)
    }

    private fun store(i: Int, nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0[i] = nb0 / na0; b1[i] = nb1 / na0; b2[i] = nb2 / na0
        a1[i] = na1 / na0; a2[i] = na2 / na0
    }

    private fun peaking(i: Int, fc: Double, db: Double, q: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        store(i, 1 + alpha * a, -2 * cosw, 1 - alpha * a, 1 + alpha / a, -2 * cosw, 1 - alpha / a)
    }

    private fun lowShelf(i: Int, fc: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2.0 * PI * fc / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val sqA = sqrt(a)
        store(
            i,
            a * ((a + 1) - (a - 1) * cosw + 2 * sqA * alpha),
            2 * a * ((a - 1) - (a + 1) * cosw),
            a * ((a + 1) - (a - 1) * cosw - 2 * sqA * alpha),
            (a + 1) + (a - 1) * cosw + 2 * sqA * alpha,
            -2 * ((a - 1) + (a + 1) * cosw),
            (a + 1) + (a - 1) * cosw - 2 * sqA * alpha
        )
    }
}
