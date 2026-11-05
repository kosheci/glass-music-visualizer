package app.glass.music

import android.media.audiofx.Visualizer

/** Audio session 0 = output mix, if the device exposes it. */
class VisualizerInput {
    private var visualizer: Visualizer? = null
    fun start(): Boolean {
        if (visualizer != null) return true
        return try {
            val v = Visualizer(0)
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = 1024.coerceIn(range[0], range[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) SpectrumBus.visualizerFft(fft, samplingRate)
                }
            }, (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1000), false, true)
            v.enabled = true
            visualizer = v
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }
    fun stop() {
        val v = visualizer
        visualizer = null
        try { v?.enabled = false } catch (_: Exception) {}
        try { v?.release() } catch (_: Exception) {}
    }
}
