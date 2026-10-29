package com.harrylong.tvchess.learn

import android.content.Context
import com.harrylong.tvchess.audio.TvSoundManager

/**
 * Trợ thủ phát giọng nói cho các bài học cờ vua của Bé Đậu:
 * Tự động tìm kiếm file âm thanh trong res/raw theo [voiceKey].
 * Nếu đã có file âm thanh thì phát qua [TvSoundManager.speakRaw] (kèm cơ chế Audio Ducking tự giảm nhạc/SFX),
 * nếu file âm thanh chưa được nạp thì âm thầm bỏ qua một cách an toàn.
 */
object LessonVoiceHelper {

    fun speak(context: Context, voiceKey: String, fallbackText: String = "") {
        if (voiceKey.isBlank()) return
        val resId = try {
            context.resources.getIdentifier(voiceKey, "raw", context.packageName)
        } catch (_: Throwable) {
            0
        }
        if (resId != 0) {
            TvSoundManager.speakRaw(context, resId, fallbackText)
        }
    }
}
