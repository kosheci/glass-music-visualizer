package app.glass.music

import android.os.SystemClock
import kotlin.math.*

/** FFT input shared across the activity and a live overlay in the SAME process. */
object SpectrumBus {
    const val BAR_COUNT = 36
    @Volatile var levels: FloatArray = FloatArray(BAR_COUNT)
        private set
    @Volatile var lastAudioMs: Long = 0L
        private set
    @Volatile var captureActive: Boolean = false

    private fun commit(levelsIn: FloatArray) {
        levels = levelsIn
        if (levelsIn.maxOrNull()?.let { it > 0.055f } == true) {
            lastAudioMs = SystemClock.elapsedRealtime()
        }
    }

    /** Android Visualizer FFT: unsigned 8-bit complex spectrum encoded in signed bytes. */
    fun visualizerFft(data: ByteArray, sampleRateMilliHz: Int) {
        if (captureActive) return
        val hz = sampleRateMilliHz / 1000.0
        if (data.size < 16 || hz <= 0.0) return
        val n = data.size
        val output = FloatArray(BAR_COUNT)
        for (i in 0 until BAR_COUNT) {
            val fLow = 45.0 * (14000.0 / 45.0).pow(i.toDouble() / BAR_COUNT)
            val fHigh = 45.0 * (14000.0 / 45.0).pow((i + 1.0) / BAR_COUNT)
            val left = (fLow * n / hz).toInt().coerceIn(1, n / 2 - 1)
            val right = max(left, (fHigh * n / hz).toInt().coerceIn(1, n / 2 - 1))
            var maxMag = 0.0
            for (k in left..right) {
                val re = data[2 * k].toDouble()
                val im = data[2 * k + 1].toDouble()
                maxMag = max(maxMag, hypot(re, im))
            }
            output[i] = (ln(1.0 + maxMag) / ln(105.0)).toFloat().coerceIn(0f, 1f)
        }
        commit(output)
    }

    /** PCM captured with MediaProjection; true frequency analysis, no artificial animation. */
    fun pcm(samples: ShortArray, count: Int, sampleRateHz: Int) {
        val n = 1024
        if (count < n) return
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val start = count - n
        for (i in 0 until n) {
            val hann = 0.5 - 0.5 * cos(2.0 * Math.PI * i / (n - 1))
            re[i] = samples[start + i] / 32768.0 * hann
        }
        // In-place radix-2 FFT.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val t = re[i]; re[i] = re[j]; re[j] = t
                val u = im[i]; im[i] = im[j]; im[j] = u
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var offset = 0
            while (offset < n) {
                var currRe = 1.0
                var currIm = 0.0
                for (p in 0 until len / 2) {
                    val uRe = re[offset + p]
                    val uIm = im[offset + p]
                    val vRe = re[offset + p + len / 2] * currRe - im[offset + p + len / 2] * currIm
                    val vIm = re[offset + p + len / 2] * currIm + im[offset + p + len / 2] * currRe
                    re[offset + p] = uRe + vRe
                    im[offset + p] = uIm + vIm
                    re[offset + p + len / 2] = uRe - vRe
                    im[offset + p + len / 2] = uIm - vIm
                    val nextRe = currRe * wRe - currIm * wIm
                    currIm = currRe * wIm + currIm * wRe
                    currRe = nextRe
                }
                offset += len
            }
            len = len shl 1
        }
        val output = FloatArray(BAR_COUNT)
        for (b in 0 until BAR_COUNT) {
            val fLow = 45.0 * (14000.0 / 45.0).pow(b.toDouble() / BAR_COUNT)
            val fHigh = 45.0 * (14000.0 / 45.0).pow((b + 1.0) / BAR_COUNT)
            val low = (fLow * n / sampleRateHz).toInt().coerceIn(1, n / 2 - 1)
            val high = max(low, (fHigh * n / sampleRateHz).toInt().coerceIn(1, n / 2 - 1))
            var peak = 0.0
            for (k in low..high) peak = max(peak, hypot(re[k], im[k]) * 2 / n)
            // Normalize logarithmically so both quiet and loud music remain visible.
            output[b] = (ln(1.0 + peak * 600.0) / ln(8.0)).toFloat().coerceIn(0f, 1f)
        }
        commit(output)
    }
}
