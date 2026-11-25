package com.example.jarvisteste.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Extrai MFCC (Mel-Frequency Cepstral Coefficients) de um trecho de voz e
 * compara padrões com DTW (Dynamic Time Warping).
 *
 * Tudo roda no aparelho: nem o áudio nem a impressão vocal saem do celular.
 * O uso de MFCC + DTW é adequado para verificar uma frase fixa — a palavra de
 * ativação — e evita depender de um modelo externo ou de biometria do sistema.
 */
object VoiceFeatures {

    const val SAMPLE_RATE = 16_000
    const val FRAMES = 40
    const val DIMS = 14

    private const val NUM_MFCC = 13
    private const val MEL_BANDS = 20
    private const val FFT_SIZE = 512
    private const val FRAME_SIZE = 400 // 25 ms a 16 kHz
    private const val HOP_SIZE = 160 // 10 ms a 16 kHz
    private const val PRE_EMPHASIS = 0.97f

    private val melFilterCache = mutableMapOf<Int, Array<FloatArray>>()

    private fun filtersFor(sampleRate: Int): Array<FloatArray> = synchronized(melFilterCache) {
        melFilterCache.getOrPut(sampleRate) { buildMelFilters(sampleRate) }
    }

    /** Devolve 40 x 14 coeficientes normalizados, ou null se não houver voz. */
    fun extract(input: FloatArray, sampleRate: Int = SAMPLE_RATE): FloatArray? {
        if (input.isEmpty()) return null
        val active = trimSilence(input, sampleRate) ?: return null
        val filters = filtersFor(sampleRate)

        val emphasized = FloatArray(active.size)
        emphasized[0] = active[0]
        for (i in 1 until active.size) {
            emphasized[i] = active[i] - PRE_EMPHASIS * active[i - 1]
        }

        val frameCount = max(1, (emphasized.size - FRAME_SIZE) / HOP_SIZE + 1)
        val raw = Array(frameCount) { FloatArray(DIMS) }
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val window = FloatArray(FRAME_SIZE) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat()
        }

        for (frame in 0 until frameCount) {
            val start = frame * HOP_SIZE
            for (i in 0 until FRAME_SIZE) {
                val sample = if (start + i < emphasized.size) emphasized[start + i] else 0f
                re[i] = sample * window[i]
                im[i] = 0f
            }
            for (i in FRAME_SIZE until FFT_SIZE) {
                re[i] = 0f
                im[i] = 0f
            }

            fft(re, im)

            val power = FloatArray(FFT_SIZE / 2)
            var energy = 0f
            for (k in 1 until power.size) {
                power[k] = re[k] * re[k] + im[k] * im[k]
                energy += power[k]
            }

            for (coefficient in 0 until NUM_MFCC) {
                var sum = 0.0
                for (band in 0 until MEL_BANDS) {
                    val bandEnergy = bandEnergy(power, band, filters)
                    sum += ln(bandEnergy + 1e-8) *
                        cos(PI * coefficient * (band + 0.5) / MEL_BANDS)
                }
                raw[frame][coefficient] = sum.toFloat()
            }
            raw[frame][NUM_MFCC] = ln(energy / FRAME_SIZE + 1e-8).toFloat()
        }

        // Normaliza cada coeficiente para reduzir diferenças de volume, microfone
        // e distância — o que muda muito pouco entre uma pessoa e outra é o
        // formato do espectro ao longo do tempo.
        for (dim in 0 until DIMS) {
            var mean = 0f
            for (frame in 0 until frameCount) mean += raw[frame][dim]
            mean /= frameCount
            var variance = 0f
            for (frame in 0 until frameCount) {
                val delta = raw[frame][dim] - mean
                variance += delta * delta
            }
            val std = sqrt(variance / frameCount).coerceAtLeast(1e-3f)
            for (frame in 0 until frameCount) {
                raw[frame][dim] = (raw[frame][dim] - mean) / std
            }
        }

        // Amostras de durações diferentes viram a mesma quantidade de quadros.
        val out = FloatArray(FRAMES * DIMS)
        for (frame in 0 until FRAMES) {
            val position = if (FRAMES == 1) {
                0f
            } else {
                frame.toFloat() * (frameCount - 1) / (FRAMES - 1)
            }
            val low = position.toInt().coerceIn(0, frameCount - 1)
            val high = min(low + 1, frameCount - 1)
            val blend = position - low
            for (dim in 0 until DIMS) {
                out[frame * DIMS + dim] =
                    raw[low][dim] * (1f - blend) + raw[high][dim] * blend
            }
        }
        return out
    }

    /** Corta silêncio no começo/fim para cadastro e verificação ficarem iguais. */
    private fun trimSilence(input: FloatArray, sampleRate: Int): FloatArray? {
        val window = max(1, sampleRate / 50) // 20 ms
        val frames = input.size / window
        if (frames < 4) return null
        val rms = FloatArray(frames)
        var loudest = 0f
        for (frame in 0 until frames) {
            var sum = 0f
            val from = frame * window
            for (i in 0 until window) {
                val value = input[from + i]
                sum += value * value
            }
            rms[frame] = sqrt(sum / window)
            loudest = max(loudest, rms[frame])
        }
        if (loudest < 0.008f) return null

        val threshold = max(0.008f, loudest * 0.14f)
        var first = -1
        var last = -1
        for (frame in 0 until frames) {
            if (rms[frame] >= threshold) {
                if (first < 0) first = frame
                last = frame
            }
        }
        if (first < 0 || last - first < 4) return null

        val margin = max(1, (sampleRate * 0.12f).toInt() / window)
        val from = max(0, first - margin) * window
        val to = min(input.size, (last + margin + 1) * window)
        if (to - from < sampleRate / 5) return null
        return input.copyOfRange(from, to)
    }

    private fun bandEnergy(
        power: FloatArray,
        band: Int,
        filters: Array<FloatArray>
    ): Float {
        val weights = filters[band]
        var sum = 0f
        for (k in 1 until min(power.size, weights.size)) {
            sum += power[k] * weights[k]
        }
        return sum
    }

    private fun buildMelFilters(sampleRate: Int): Array<FloatArray> {
        val minMel = hzToMel(80.0)
        val maxMel = hzToMel(7_600.0)
        val points = DoubleArray(MEL_BANDS + 2) { i ->
            minMel + (maxMel - minMel) * i / (MEL_BANDS + 1)
        }
        val hz = points.map(::melToHz)
        return Array(MEL_BANDS) { band ->
            FloatArray(FFT_SIZE / 2) { k ->
                val frequency = k.toDouble() * sampleRate / FFT_SIZE
                val left = hz[band]
                val center = hz[band + 1]
                val right = hz[band + 2]
                val weight = when {
                    frequency < left || frequency > right -> 0.0
                    frequency < center -> (frequency - left) / (center - left)
                    else -> (right - frequency) / (right - center)
                }
                weight.coerceIn(0.0, 1.0).toFloat()
            }
        }
    }

    private fun hzToMel(hz: Double): Double = 2_595.0 * ln(1.0 + hz / 700.0) / ln(10.0)

    private fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2_595.0) - 1.0)

    /** FFT radix-2 iterativa, em cascas, sem dependências externas. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n / 2
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tmpRe = re[i]
                re[i] = re[j]
                re[j] = tmpRe
                val tmpIm = im[i]
                im[i] = im[j]
                im[j] = tmpIm
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepRe = cos(angle).toFloat()
            val stepIm = sin(angle).toFloat()
            var start = 0
            while (start < n) {
                var wr = 1f
                var wi = 0f
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val uRe = re[even]
                    val uIm = im[even]
                    val vRe = re[odd] * wr - im[odd] * wi
                    val vIm = re[odd] * wi + im[odd] * wr
                    re[even] = uRe + vRe
                    im[even] = uIm + vIm
                    re[odd] = uRe - vRe
                    im[odd] = uIm - vIm
                    val nextWr = wr * stepRe - wi * stepIm
                    wi = wr * stepIm + wi * stepRe
                    wr = nextWr
                }
                start += length
            }
            length *= 2
        }
    }
}

/** Compara uma gravação com os padrões cadastrados e devolve 0..1 (melhor casamento). */
object VoiceprintMatcher {

    private const val WINDOW_MS = 1_800
    private const val STEP_MS = 350

    /**
     * Procura a melhor janela dentro do buffer recente. O reconhecedor do
     * Android devolve a transcrição algum tempo depois da fala, então o trecho
     * útil pode estar no meio do buffer, não necessariamente no fim.
     */
    fun bestScore(pcm: FloatArray, sampleRate: Int, templates: List<FloatArray>): Float {
        if (pcm.isEmpty() || templates.isEmpty()) return 0f
        val window = sampleRate * WINDOW_MS / 1_000
        val step = sampleRate * STEP_MS / 1_000
        if (window <= 0 || step <= 0) return 0f

        var best = 0f
        var start = 0
        while (start + window <= pcm.size) {
            best = max(best, scoreWindow(pcm, start, window, sampleRate, templates))
            start += step
        }
        val tail = max(0, pcm.size - window)
        if (tail != start - step && tail + window <= pcm.size) {
            best = max(best, scoreWindow(pcm, tail, window, sampleRate, templates))
        }
        return best
    }

    /** Quanto maior o rigor, maior a pontuação exigida. */
    fun threshold(strictness: Int): Float =
        0.54f + strictness.coerceIn(0, 100) / 100f * 0.24f

    fun accepts(score: Float, strictness: Int): Boolean = score >= threshold(strictness)

    private fun scoreWindow(
        pcm: FloatArray,
        start: Int,
        length: Int,
        sampleRate: Int,
        templates: List<FloatArray>
    ): Float {
        val slice = pcm.copyOfRange(start, start + length)
        val features = VoiceFeatures.extract(slice, sampleRate) ?: return 0f
        var best = 0f
        for (template in templates) {
            best = max(best, templateScore(features, template))
        }
        return best
    }

    /** DTW com distância cosseno quadro a quadro; devolve 1 = idêntico. */
    private fun templateScore(sample: FloatArray, template: FloatArray): Float {
        val n = VoiceFeatures.FRAMES
        val m = VoiceFeatures.FRAMES
        val expected = n * VoiceFeatures.DIMS
        if (sample.size != expected || template.size != expected) return 0f

        val dp = FloatArray(n * m) { Float.POSITIVE_INFINITY }
        val pathLength = IntArray(n * m)
        for (i in 0 until n) {
            for (j in 0 until m) {
                val cost = frameDistance(
                    sample,
                    template,
                    i * VoiceFeatures.DIMS,
                    j * VoiceFeatures.DIMS
                )
                val index = i * m + j
                if (i == 0 && j == 0) {
                    dp[index] = cost
                    pathLength[index] = 1
                    continue
                }
                val from = when {
                    i == 0 -> index - 1
                    j == 0 -> index - m
                    dp[index - 1] <= dp[index - m] && dp[index - 1] <= dp[index - m - 1] ->
                        index - 1
                    dp[index - m] <= dp[index - m - 1] -> index - m
                    else -> index - m - 1
                }
                dp[index] = dp[from] + cost
                pathLength[index] = pathLength[from] + 1
            }
        }
        val index = n * m - 1
        val average = dp[index] / pathLength[index].coerceAtLeast(1)
        return (1f - average / 2f).coerceIn(0f, 1f)
    }

    private fun frameDistance(a: FloatArray, b: FloatArray, offsetA: Int, offsetB: Int): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (d in 0 until VoiceFeatures.DIMS) {
            val x = a[offsetA + d]
            val y = b[offsetB + d]
            dot += x * y
            normA += x * x
            normB += y * y
        }
        if (normA <= 1e-8f || normB <= 1e-8f) return 1f
        val cosine = (dot / sqrt(normA * normB)).coerceIn(-1f, 1f)
        return 1f - cosine
    }
}
