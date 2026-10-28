package com.sonora.fresh

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Latest spectrum levels (0..1). Written by the audio thread, read by the UI. */
object AudioLevels {
    const val BANDS = 64
    @Volatile var bands: FloatArray = FloatArray(BANDS)
}

/**
 * Passes audio through unchanged and measures its spectrum (FFT) on the way.
 * No RECORD_AUDIO permission needed.
 */
class VisualizerAudioProcessor : BaseAudioProcessor() {
    private val n = 2048
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val samples = FloatArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val smooth = FloatArray(AudioLevels.BANDS)
    private var filled = 0
    private var channels = 2
    private var sampleRate = 44100

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Unsupported formats: stay inactive, audio plays normally without visualization.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate
        filled = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        analyze(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    private fun analyze(buf: ByteBuffer) {
        val frameBytes = channels * 2
        while (buf.remaining() >= frameBytes) {
            var sum = 0
            for (ch in 0 until channels) sum += buf.getShort().toInt()
            samples[filled++] = sum / (32768f * channels)
            if (filled == n) {
                computeBands()
                // 50% overlap: keep the newest half so updates stay ~43 per second
                System.arraycopy(samples, n / 2, samples, 0, n / 2)
                filled = n / 2
            }
        }
    }

    private fun computeBands() {
        for (i in 0 until n) { re[i] = samples[i] * window[i]; im[i] = 0f }
        fft()
        val bands = AudioLevels.BANDS
        val out = FloatArray(bands)
        val binHz = sampleRate.toFloat() / n
        val fMin = 40f
        val fMax = min(14000f, sampleRate / 2f - 1f)
        val ratio = fMax / fMin
        for (b in 0 until bands) {
            val lo = fMin * ratio.pow(b.toFloat() / bands)
            val hi = fMin * ratio.pow((b + 1f) / bands)
            val i0 = (lo / binHz).toInt().coerceIn(1, n / 2 - 1)
            val i1 = max(i0, (hi / binHz).toInt()).coerceAtMost(n / 2 - 1)
            var peak = 0f
            for (k in i0..i1) {
                val m = re[k] * re[k] + im[k] * im[k]
                if (m > peak) peak = m
            }
            val mag = sqrt(peak) / (n / 4f)
            // +26 dB tilt: high bands carry far less energy, lift them so the right side stays alive.
            val db = 20f * log10(mag + 1e-9f) + 26f * b / (bands - 1)
            val level = ((db + 65f) / 55f).coerceIn(0f, 1f)
            smooth[b] += (level - smooth[b]) * (if (level > smooth[b]) 0.7f else 0.2f)
            out[b] = smooth[b]
        }
        AudioLevels.bands = out
    }

    private fun fft() {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = i + k + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
