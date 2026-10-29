package com.harrylong.tvchess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.annotation.RawRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trình phát nhạc nền thư giãn (Background Music Player) dành cho "Đậu Học Cờ Vua".
 *
 * Tính năng chính:
 * 1. Phát lặp vòng liền mạch (isLooping = true) 5 bản nhạc nền theo chủ đề.
 * 2. Crossfade 1.5 giây mượt mà khi chuyển đổi bản nhạc giữa các màn hình.
 * 3. Audio Ducking 2 tầng:
 *    - Tự động giảm âm lượng xuống ~25% khi có giọng nói phát lên, phục hồi sau 600ms.
 *    - Tự động giảm âm lượng khi hệ thống thông báo (AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK).
 * 4. Tôn trọng Audio Focus toàn diện trên Android TV & Điện thoại (tạm dừng khi mất focus hoặc cuộc gọi đến).
 * 5. Bật/tắt riêng biệt khỏi hiệu ứng âm thanh SFX, điều chỉnh âm lượng (mặc định 40%).
 */
object BgmPlayer {

    private const val TAG = "BgmPlayer"
    private const val DEFAULT_VOLUME = 0.40f
    private const val DUCK_RATIO = 0.25f
    private const val CROSSFADE_DURATION_MS = 1500L
    private const val DUCK_RESTORE_DELAY_MS = 600L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Trạng thái cấu hình & phát nhạc
    var isEnabled: Boolean = true
        private set

    var masterVolume: Float = DEFAULT_VOLUME
        private set

    @Volatile
    private var isVoiceDucked: Boolean = false

    @Volatile
    private var isFocusDucked: Boolean = false

    @Volatile
    private var isPausedByUserOrSystem: Boolean = false

    @RawRes
    var currentTrackResId: Int = 0
        private set

    private var appContext: Context? = null
    private var activePlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null

    // Quản lý AudioFocus
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    // Các tác vụ Coroutine điều khiển âm lượng mượt mà
    private var crossfadeJob: Job? = null
    private var duckRestoreJob: Job? = null
    private var volumeRampJob: Job? = null

    val isPlaying: Boolean
        get() = activePlayer?.isPlaying == true

    /**
     * Bắt đầu phát bản nhạc nền theo resource ID.
     * Nếu đang phát bản nhạc khác, thực hiện crossfade 1.5s mượt mà giữa 2 bài.
     */
    fun play(context: Context, @RawRes trackResId: Int) {
        val appCtx = context.applicationContext
        appContext = appCtx

        if (trackResId == 0) return

        // Nếu cùng bản nhạc đang phát thì tiếp tục, không cần khởi động lại
        if (currentTrackResId == trackResId && activePlayer != null) {
            if (isEnabled && !isPlaying && !isPausedByUserOrSystem) {
                requestAudioFocus()
                try {
                    activePlayer?.start()
                } catch (e: Exception) {
                    Log.w(TAG, "Lỗi khi phát tiếp BGM", e)
                }
            }
            return
        }

        currentTrackResId = trackResId

        if (!isEnabled) {
            // Nhạc nền đang bị tắt theo cài đặt của người dùng: ghi nhớ track nhưng không phát
            stopAndReleasePlayers()
            return
        }

        // Yêu cầu Audio Focus trước khi phát
        requestAudioFocus()

        val previousPlayer = activePlayer
        crossfadeJob?.cancel()
        volumeRampJob?.cancel()

        try {
            val newPlayer = MediaPlayer.create(appCtx, trackResId)
            if (newPlayer == null) {
                Log.e(TAG, "Không thể tạo MediaPlayer cho resId=$trackResId")
                return
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            newPlayer.setAudioAttributes(audioAttributes)
            newPlayer.isLooping = true

            if (previousPlayer != null && previousPlayer.isPlaying) {
                // Có bản nhạc đang phát: thực hiện Crossfade 1.5s
                fadingOutPlayer?.let { safeReleasePlayer(it) }
                fadingOutPlayer = previousPlayer
                activePlayer = newPlayer

                val targetVol = calculateEffectiveVolume()
                newPlayer.setVolume(0f, 0f)
                newPlayer.start()

                crossfadeJob = scope.launch {
                    val steps = 30
                    val stepDelay = CROSSFADE_DURATION_MS / steps
                    val startOutVol = targetVol // Giả định bài cũ đang ở mức effective volume

                    for (step in 1..steps) {
                        delay(stepDelay)
                        val progress = step.toFloat() / steps
                        val outVol = ((1f - progress) * startOutVol).coerceIn(0f, 1f)
                        val inVol = (progress * targetVol).coerceIn(0f, 1f)

                        try {
                            fadingOutPlayer?.setVolume(outVol, outVol)
                            activePlayer?.setVolume(inVol, inVol)
                        } catch (_: Exception) {}
                    }

                    // Hoàn tất crossfade: giải phóng bài cũ
                    fadingOutPlayer?.let {
                        safeReleasePlayer(it)
                        fadingOutPlayer = null
                    }
                    activePlayer?.let {
                        val finalVol = calculateEffectiveVolume()
                        it.setVolume(finalVol, finalVol)
                    }
                }
            } else {
                // Bắt đầu từ trạng thái im lặng: Fade-in nhẹ nhàng 1.5s
                previousPlayer?.let { safeReleasePlayer(it) }
                activePlayer = newPlayer

                val targetVol = calculateEffectiveVolume()
                newPlayer.setVolume(0f, 0f)
                newPlayer.start()

                crossfadeJob = scope.launch {
                    val steps = 25
                    val stepDelay = CROSSFADE_DURATION_MS / steps
                    for (step in 1..steps) {
                        delay(stepDelay)
                        val progress = step.toFloat() / steps
                        val inVol = (progress * targetVol).coerceIn(0f, 1f)
                        try {
                            activePlayer?.setVolume(inVol, inVol)
                        } catch (_: Exception) {}
                    }
                    activePlayer?.let {
                        val finalVol = calculateEffectiveVolume()
                        it.setVolume(finalVol, finalVol)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi tạo phát nhạc nền BGM", e)
        }
    }

    /**
     * Bật hoặc tắt tính năng phát Nhạc nền.
     */
    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled

        if (!enabled) {
            crossfadeJob?.cancel()
            volumeRampJob?.cancel()
            stopAndReleasePlayers()
            abandonAudioFocus()
        } else {
            if (currentTrackResId != 0 && appContext != null) {
                play(appContext!!, currentTrackResId)
            }
        }
    }

    /**
     * Điều chỉnh âm lượng Nhạc nền (0.0f đến 1.0f).
     * Mặc định là 0.4f (40%).
     */
    fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        applyVolumeImmediate()
    }

    /**
     * Hạ âm lượng (Ducking) xuống ~25% khi giọng nói tiếng Việt đang phát,
     * và phục hồi về 100% sau 600ms kể từ khi giọng nói dứt.
     */
    fun duck(ducked: Boolean) {
        duckRestoreJob?.cancel()

        if (ducked) {
            isVoiceDucked = true
            rampVolume(calculateEffectiveVolume(), durationMs = 120L)
        } else {
            duckRestoreJob = scope.launch {
                delay(DUCK_RESTORE_DELAY_MS)
                isVoiceDucked = false
                rampVolume(calculateEffectiveVolume(), durationMs = 280L)
            }
        }
    }

    /**
     * Tạm dừng nhạc nền (khi app chuyển vào nền hoặc mất AudioFocus).
     */
    fun pause() {
        isPausedByUserOrSystem = true
        try {
            if (activePlayer?.isPlaying == true) {
                activePlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi pause BGM", e)
        }
    }

    /**
     * Tiếp tục phát nhạc nền (khi app quay lại tiền cảnh hoặc nhận lại AudioFocus).
     */
    fun resume() {
        if (!isEnabled || currentTrackResId == 0) return
        isPausedByUserOrSystem = false

        if (activePlayer != null) {
            requestAudioFocus()
            try {
                applyVolumeImmediate()
                activePlayer?.start()
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi khi resume BGM", e)
            }
        } else if (appContext != null) {
            play(appContext!!, currentTrackResId)
        }
    }

    /**
     * Giải phóng toàn bộ tài nguyên nhạc nền (gọi khi thoát ứng dụng).
     */
    fun release() {
        crossfadeJob?.cancel()
        duckRestoreJob?.cancel()
        volumeRampJob?.cancel()
        stopAndReleasePlayers()
        abandonAudioFocus()
        appContext = null
        currentTrackResId = 0
    }

    // --------------------------------------------------------------------------
    // Quản lý Âm lượng & Nội suy làm mượt (Volume Interpolation)
    // --------------------------------------------------------------------------

    private fun calculateEffectiveVolume(): Float {
        if (!isEnabled) return 0f
        var vol = masterVolume
        if (isVoiceDucked) {
            vol *= DUCK_RATIO
        }
        if (isFocusDucked) {
            vol *= DUCK_RATIO
        }
        return vol.coerceIn(0f, 1f)
    }

    private fun applyVolumeImmediate() {
        val vol = calculateEffectiveVolume()
        try {
            activePlayer?.setVolume(vol, vol)
        } catch (_: Exception) {}
    }

    private fun rampVolume(targetVol: Float, durationMs: Long) {
        volumeRampJob?.cancel()
        val player = activePlayer ?: return
        if (crossfadeJob?.isActive == true) return // Tránh xung đột nếu đang crossfade bài mới

        volumeRampJob = scope.launch {
            val steps = 12
            val stepDelay = durationMs / steps
            val currentVol = calculateEffectiveVolume()

            for (step in 1..steps) {
                delay(stepDelay)
                val progress = step.toFloat() / steps
                val v = (currentVol + progress * (targetVol - currentVol)).coerceIn(0f, 1f)
                try {
                    player.setVolume(v, v)
                } catch (_: Exception) {}
            }
            try {
                player.setVolume(targetVol, targetVol)
            } catch (_: Exception) {}
        }
    }

    private fun safeReleasePlayer(player: MediaPlayer) {
        try {
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        } catch (_: Exception) {}
    }

    private fun stopAndReleasePlayers() {
        activePlayer?.let { safeReleasePlayer(it) }
        activePlayer = null
        fadingOutPlayer?.let { safeReleasePlayer(it) }
        fadingOutPlayer = null
    }

    // --------------------------------------------------------------------------
    // Xử lý Audio Focus (Android TV & Điện Thoại)
    // --------------------------------------------------------------------------

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Mất quyền âm thanh vĩnh viễn (app khác chiếm toàn quyền)
                hasAudioFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Mất quyền tạm thời (chuông điện thoại, thông báo quan trọng)
                hasAudioFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Cho phép giảm âm lượng để âm thanh khác phát đè lên
                isFocusDucked = true
                applyVolumeImmediate()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Nhận lại toàn quyền âm thanh
                hasAudioFocus = true
                if (isFocusDucked) {
                    isFocusDucked = false
                    applyVolumeImmediate()
                }
                if (isPausedByUserOrSystem && isEnabled) {
                    resume()
                }
            }
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val ctx = appContext ?: return
        val am = audioManager ?: (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also {
            audioManager = it
        } ?: return

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }
}
