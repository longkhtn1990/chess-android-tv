package com.harrylong.tvchess.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bộ tổng hợp âm thanh vật lý thời gian thực (Acoustic SFX Synthesizer) 44.1kHz PCM 16-bit
 * dành riêng cho "Đậu Học Cờ Vua".
 *
 * Áp dụng mô hình vật lý Modal Resonant Wood Physics:
 * - Xung nén Hertzian phi tuyến tính (tiếng gõ nén đầm của gỗ đặc).
 * - Cộng hưởng hốc bàn cờ rỗng và thớ gỗ mun/sồi đa hài âm (5 mode cộng hưởng).
 * - Damping phụ thuộc tần số theo cơ chế suy hao đàn hồi sợi xenlulo tự nhiên.
 * - Loại bỏ hoàn toàn sóng sin trơn đơn điệu, mang lại âm thanh gỗ mộc mạc, dày dặn, ấm áp.
 * - Hỗ trợ hệ số volumeScale phục vụ Audio Ducking khi phát cùng giọng nói tương tác của bé Đậu.
 */
object AcousticSfxSynthesizer {

    const val SAMPLE_RATE: Int = 44100
    private const val TWO_PI: Double = 2.0 * PI

    /**
     * Tiếng đặt quân cờ gỗ thật xuống mặt bàn cờ (Soft / Heavy).
     */
    fun synthesizeWoodMove(isHeavy: Boolean = false, volumeScale: Float = 1f): ShortArray {
        val durationMs = if (isHeavy) 130 else 95
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)

        // 5 mode cộng hưởng vật lý của hộp gỗ (freq, amp, damping)
        val modes = if (isHeavy) {
            arrayOf(
                Triple(128.0, 0.46, 26.0),
                Triple(245.0, 0.36, 42.0),
                Triple(510.0, 0.22, 70.0),
                Triple(1020.0, 0.12, 115.0),
                Triple(2100.0, 0.05, 180.0)
            )
        } else {
            arrayOf(
                Triple(178.0, 0.42, 36.0),
                Triple(365.0, 0.35, 54.0),
                Triple(730.0, 0.22, 88.0),
                Triple(1460.0, 0.14, 140.0),
                Triple(2800.0, 0.06, 220.0)
            )
        }

        val transientDur = if (isHeavy) 0.008 else 0.006
        val transientDecay = if (isHeavy) 420.0 else 520.0
        val pitchDrop = if (isHeavy) 0.09 else 0.05
        val rand = Random(if (isHeavy) 101 else 202)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            // 1. Hertzian impact transient (xung nén gỗ đặc)
            var transient = 0.0
            if (t < transientDur) {
                val progress = t / transientDur
                val compression = sin(PI * progress).let { it * it * it } // Nén dạng lũy thừa
                val grain = (rand.nextDouble() * 2.0 - 1.0) * 0.20
                transient = (compression + grain) * exp(-transientDecay * t) * 0.78
            }

            // 2. Wood cavity modal resonance (cộng hưởng thớ gỗ và hốc bàn cờ)
            var modal = 0.0
            val pBend = 1.0 + pitchDrop * exp(-140.0 * t)
            for (m in modes) {
                val env = exp(-m.third * t)
                modal += sin(TWO_PI * (m.first * pBend) * t) * m.second * env
            }

            val raw = (transient * 0.55 + modal * 0.72) * vScale * 0.88
            samples[i] = clampSample(raw)
        }

        applyWindow(samples, fadeSamples = 60)
        return samples
    }

    /**
     * Tiếng lướt quân cờ trên mặt gỗ (âm dạ cọ xát êm ái + cú chạm nhẹ ở cuối).
     */
    fun synthesizeSlideWhoosh(volumeScale: Float = 1f): ShortArray {
        val durationMs = 125
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)
        val rand = Random(404)

        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val prog = i.toDouble() / numSamples

            val freq = 320.0 + 220.0 * sin(PI * prog)
            phase += TWO_PI * freq / SAMPLE_RATE
            if (phase > TWO_PI) phase -= TWO_PI

            val friction = (rand.nextDouble() * 2.0 - 1.0) * 0.32
            val body = sin(phase) * 0.68 + friction
            val env = sin(PI * prog)

            var raw = body * env * 0.45

            // Cú chạm nhẹ cuối cú lướt
            if (prog > 0.80) {
                val dtTap = (prog - 0.80) * (durationMs / 1000.0)
                val tapEnv = exp(-80.0 * dtTap)
                raw += sin(TWO_PI * 180.0 * dtTap) * tapEnv * 0.25
            }

            samples[i] = clampSample(raw * vScale * 0.85)
        }

        applyWindow(samples, fadeSamples = 60)
        return samples
    }

    /**
     * Tiếng 2 quân cờ gỗ va chạm giòn giã (Double wood impact) kết hợp chuỗi chuông mộc C5 - E5 - G5 - C6.
     */
    fun synthesizeCaptureImpact(volumeScale: Float = 1f): ShortArray {
        val durationMs = 380
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)
        val rand = Random(303)

        val hit1Modes = arrayOf(Triple(205.0, 0.40, 38.0), Triple(430.0, 0.35, 60.0), Triple(890.0, 0.22, 100.0))
        val hit2Modes = arrayOf(Triple(235.0, 0.38, 42.0), Triple(480.0, 0.32, 65.0), Triple(980.0, 0.20, 110.0))
        val hit2Delay = 0.018

        val chimes = arrayOf(
            Triple(0.045, 523.25, 0.28), // C5
            Triple(0.105, 659.25, 0.26), // E5
            Triple(0.165, 783.99, 0.25), // G5
            Triple(0.225, 1046.50, 0.28) // C6
        )

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sampleVal = 0.0

            // Cú chạm 1
            val env1 = if (t < 0.008) exp(-380.0 * t) else 0.0
            val trans1 = (sin(PI * min(1.0, t / 0.007)) + (rand.nextDouble() * 2 - 1) * 0.15) * env1
            var modal1 = 0.0
            for (m in hit1Modes) {
                modal1 += sin(TWO_PI * m.first * t) * m.second * exp(-m.third * t)
            }
            sampleVal += (trans1 * 0.50 + modal1 * 0.65)

            // Cú chạm 2 (nảy dồn)
            if (t >= hit2Delay) {
                val t2 = t - hit2Delay
                val env2 = if (t2 < 0.008) exp(-420.0 * t2) else 0.0
                val trans2 = (sin(PI * min(1.0, t2 / 0.006)) + (rand.nextDouble() * 2 - 1) * 0.15) * env2
                var modal2 = 0.0
                for (m in hit2Modes) {
                    modal2 += sin(TWO_PI * m.first * t2) * m.second * exp(-m.third * t2)
                }
                sampleVal += (trans2 * 0.45 + modal2 * 0.55) * 0.85
            }

            // Chuông mộc ngân vang
            for (c in chimes) {
                val st = c.first
                if (t >= st) {
                    val dt = t - st
                    val cEnv = exp(-14.0 * dt)
                    val freq = c.second
                    val cTone = sin(TWO_PI * freq * dt) + 0.18 * sin(TWO_PI * (freq * 2.0) * dt)
                    sampleVal += cTone * cEnv * c.third
                }
            }

            samples[i] = clampSample(sampleVal * vScale * 0.80)
        }

        applyWindow(samples, fadeSamples = 80)
        return samples
    }

    /**
     * Tiếng gõ phím Marimba mộc dịu tai khi di chuyển D-Pad (E5 - 660Hz).
     */
    fun synthesizeNavMarimba(volumeScale: Float = 1f): ShortArray {
        val durationMs = 65
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)

        val f0 = 660.0
        val f1 = 1980.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = min(1.0, t / 0.0018)
            val decay = exp(-54.0 * t)
            val env = attack * decay

            val tone = 0.86 * sin(TWO_PI * f0 * t) + 0.14 * sin(TWO_PI * f1 * t) * exp(-85.0 * t)
            val raw = tone * env * vScale * 0.45
            samples[i] = clampSample(raw)
        }

        applyWindow(samples, fadeSamples = 40)
        return samples
    }

    /**
     * Tiếng chọn quân cờ mộc tươi vui 2 nốt ấm áp (C5 -> G5).
     */
    fun synthesizeSelectChime(volumeScale: Float = 1f): ShortArray {
        val durationMs = 160
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)

        val n1F = 523.25
        val n2F = 783.99
        val n2St = 0.050

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var valSample = 0.0

            val e1 = exp(-28.0 * t)
            valSample += (sin(TWO_PI * n1F * t) + 0.18 * sin(TWO_PI * (n1F * 2.0) * t)) * e1 * 0.45

            if (t >= n2St) {
                val dt = t - n2St
                val e2 = exp(-22.0 * dt)
                valSample += (sin(TWO_PI * n2F * dt) + 0.20 * sin(TWO_PI * (n2F * 2.0) * dt)) * e2 * 0.60
            }

            samples[i] = clampSample(valSample * vScale * 0.65)
        }

        applyWindow(samples, fadeSamples = 60)
        return samples
    }

    /**
     * Tiếng cảnh báo Chiếu tướng mộc du dương kịch tính (D5 + F#5 -> A5 + D6).
     */
    fun synthesizeCheckAlert(volumeScale: Float = 1f): ShortArray {
        val durationMs = 290
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)
        val chord2St = 0.105

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var valSample = 0.0

            val att1 = min(1.0, t / 0.012)
            val e1 = att1 * exp(-14.0 * t)
            valSample += (sin(TWO_PI * 587.33 * t) + 0.85 * sin(TWO_PI * 739.99 * t)) * e1 * 0.42

            if (t >= chord2St) {
                val dt = t - chord2St
                val att2 = min(1.0, dt / 0.010)
                val e2 = att2 * exp(-11.0 * dt)
                valSample += (sin(TWO_PI * 880.0 * dt) + 0.75 * sin(TWO_PI * 1174.66 * dt)) * e2 * 0.52
            }

            samples[i] = clampSample(valSample * vScale * 0.68)
        }

        applyWindow(samples, fadeSamples = 70)
        return samples
    }

    /**
     * Fanfare mộc chiến thắng & khích lệ (C-Major Fanfare / Giai điệu mộc âu yếm).
     */
    fun synthesizeGameOverFanfare(isVictory: Boolean, volumeScale: Float = 1f): ShortArray {
        val durationMs = if (isVictory) 680 else 580
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val vScale = volumeScale.coerceIn(0f, 1.5f)

        if (isVictory) {
            val notes = arrayOf(
                Pair(0.0, 523.25),
                Pair(0.110, 659.25),
                Pair(0.220, 783.99),
                Pair(0.330, 1046.50)
            )
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                var sum = 0.0

                for (idx in notes.indices) {
                    val st = notes[idx].first
                    if (t >= st) {
                        val dt = t - st
                        val att = min(1.0, dt / 0.008)
                        val dec = if (idx == notes.lastIndex) 5.2 else 11.0
                        val env = att * exp(-dec * dt)
                        val f = notes[idx].second
                        val tone = sin(TWO_PI * f * dt) + 0.22 * sin(TWO_PI * (f * 2.0) * dt)
                        sum += tone * env * (if (idx == notes.lastIndex) 0.50 else 0.36)
                    }
                }

                if (t >= 0.330) {
                    val dt = t - 0.330
                    val cEnv = min(1.0, dt / 0.015) * exp(-5.0 * dt)
                    sum += 0.18 * sin(TWO_PI * 783.99 * dt) * cEnv
                    sum += 0.14 * sin(TWO_PI * 1318.51 * dt) * cEnv
                }

                samples[i] = clampSample(sum * vScale * 0.75)
            }
        } else {
            val notes = arrayOf(
                Pair(0.0, 783.99),
                Pair(0.120, 659.25),
                Pair(0.240, 523.25),
                Pair(0.360, 587.33)
            )
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                var sum = 0.0

                for (idx in notes.indices) {
                    val st = notes[idx].first
                    if (t >= st) {
                        val dt = t - st
                        val att = min(1.0, dt / 0.012)
                        val dec = if (idx == notes.lastIndex) 6.0 else 10.0
                        val env = att * exp(-dec * dt)
                        val f = notes[idx].second
                        val tone = sin(TWO_PI * f * dt) + 0.15 * sin(TWO_PI * (f * 2.0) * dt)
                        sum += tone * env * 0.42
                    }
                }

                samples[i] = clampSample(sum * vScale * 0.70)
            }
        }

        applyWindow(samples, fadeSamples = 80)
        return samples
    }

    /**
     * Phát mẫu âm thanh PCM 16-bit 44.1kHz trực tiếp qua AudioTrack.
     */
    fun playTrack(samples: ShortArray) {
        if (samples.isEmpty()) return
        try {
            val bufferSize = maxOf(
                samples.size * 2,
                AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 25L)
            track.release()
        } catch (_: Throwable) {
        }
    }

    private fun applyWindow(samples: ShortArray, fadeSamples: Int) {
        val count = min(fadeSamples, samples.size / 2)
        if (count <= 0) return

        for (i in 0 until count) {
            val factor = i.toDouble() / count
            samples[i] = (samples[i] * factor).toInt().toShort()
        }

        val len = samples.size
        for (i in 0 until count) {
            val factor = i.toDouble() / count
            val idx = len - 1 - i
            samples[idx] = (samples[idx] * factor).toInt().toShort()
        }
    }

    private fun clampSample(value: Double): Short {
        return (value * Short.MAX_VALUE)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
