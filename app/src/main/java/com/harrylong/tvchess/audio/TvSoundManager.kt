package com.harrylong.tvchess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.harrylong.tvchess.R
import com.harrylong.tvchess.model.Difficulty
import com.harrylong.tvchess.model.PieceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale

/**
 * Trình quản lý Âm thanh & Giọng nói tương tác dành cho "Đậu Học Cờ Vua".
 *
 * Tích hợp:
 * 1. Toàn bộ 49 clip giọng nói tiếng Việt miền Nam trong trẻo, ấm áp, tự nhiên gọi bé Đậu (không méo pitch).
 * 2. Cơ chế Audio Ducking thông minh: Tự động giảm âm lượng SFX xuống 35% khi có giọng nói phát lên.
 * 3. Chống lặp câu thoại liên tiếp (Anti-Repetition) cho trải nghiệm sống động tự nhiên.
 * 4. Hệ thống âm thanh vật lý gỗ thật độ trễ 0ms qua SoundPool (sfx_wood_*.m4a) kết hợp
 *    bộ tổng hợp cộng hưởng gỗ đa mode AcousticSfxSynthesizer.
 */
object TvSoundManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var isSoundEnabled: Boolean = true

    private var activeVoicePlayer: MediaPlayer? = null
    private val voiceLock = Any()

    @Volatile
    var isVoicePlaying: Boolean = false
        private set

    private fun updateVoicePlaying(playing: Boolean) {
        isVoicePlaying = playing
        try {
            BgmPlayer.duck(playing)
        } catch (_: Throwable) {}
    }

    private var lastVoiceResId: Int = -1

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // SoundPool cho âm thanh gỗ thật độ trễ 0ms
    private var soundPool: SoundPool? = null
    private val soundIdMap = mutableMapOf<Int, Int>()
    private val loadedSoundIds = Collections.synchronizedSet(mutableSetOf<Int>())
    @Volatile
    private var isSoundPoolReady: Boolean = false

    /**
     * Hệ số âm lượng SFX phục vụ Audio Ducking:
     * Tự động giảm xuống 0.35f khi giọng nói tương tác đang phát để tiếng gọi "Đậu" luôn rõ nhất!
     */
    fun currentSfxVolumeScale(): Float = if (isVoicePlaying) 0.35f else 1.0f

    /**
     * Khởi tạo SoundPool nạp sẵn các mẫu âm thanh gỗ thật độ trễ 0ms.
     */
    fun initSoundPool(context: Context) {
        if (soundPool != null) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSoundIds.add(sampleId)
                }
            }
            soundPool = pool

            val appCtx = context.applicationContext
            val sfxResIds = intArrayOf(
                R.raw.sfx_wood_move_soft,
                R.raw.sfx_wood_move_heavy,
                R.raw.sfx_wood_capture,
                R.raw.sfx_wood_slide,
                R.raw.sfx_wood_nav,
                R.raw.sfx_wood_select,
                R.raw.sfx_wood_check,
                R.raw.sfx_wood_fanfare_win,
                R.raw.sfx_wood_fanfare_loss
            )
            for (resId in sfxResIds) {
                try {
                    val soundId = pool.load(appCtx, resId, 1)
                    soundIdMap[resId] = soundId
                } catch (_: Throwable) {}
            }
            isSoundPoolReady = true
        } catch (_: Throwable) {}
    }

    /**
     * Chọn ngẫu nhiên chống lặp trong danh sách câu thoại.
     */
    private fun pickNonRepeating(options: List<Pair<Int, String>>): Pair<Int, String> {
        if (options.isEmpty()) return -1 to ""
        if (options.size == 1) {
            lastVoiceResId = options[0].first
            return options[0]
        }
        val candidates = options.filter { it.first != lastVoiceResId }
        val selected = if (candidates.isNotEmpty()) candidates.random() else options.random()
        lastVoiceResId = selected.first
        return selected
    }

    /**
     * Khởi tạo TextToSpeech dự phòng và nạp trước SoundPool SFX.
     */
    fun initTts(context: Context) {
        initSoundPool(context)
        if (tts != null) return
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("vi", "VN"))
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
                    tts?.setPitch(1.05f) // Giọng tự nhiên, trong trẻo
                    tts?.setSpeechRate(0.96f) // Tốc độ nói tròn vành rõ chữ
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            updateVoicePlaying(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            updateVoicePlaying(false)
                        }

                        override fun onError(utteranceId: String?) {
                            updateVoicePlaying(false)
                        }
                    })
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Giải phóng an toàn MediaPlayer: hủy listener, dừng, reset và release trong try-catch
     * nhằm triệt tiêu hoàn toàn cảnh báo: W MediaPlayer: mediaplayer went away with unhandled events.
     */
    private fun safelyReleasePlayer(player: MediaPlayer) {
        try {
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            if (player.isPlaying) {
                player.stop()
            }
            player.reset()
            player.release()
        } catch (_: Throwable) {}
    }

    /**
     * Phát đoạn giọng nói tương tác với Bé Đậu.
     */
    fun playVoiceClip(context: Context, resId: Int, fallbackText: String = "") {
        if (!isSoundEnabled) return
        scope.launch {
            synchronized(voiceLock) {
                try {
                    activeVoicePlayer?.let { player ->
                        safelyReleasePlayer(player)
                    }
                    activeVoicePlayer = null
                    updateVoicePlaying(false)

                    val mp = MediaPlayer.create(context.applicationContext, resId)
                    if (mp != null) {
                        activeVoicePlayer = mp
                        updateVoicePlaying(true)
                        mp.setOnCompletionListener { completed ->
                            synchronized(voiceLock) {
                                safelyReleasePlayer(completed)
                                if (activeVoicePlayer === completed) {
                                    activeVoicePlayer = null
                                    updateVoicePlaying(false)
                                }
                            }
                        }
                        mp.setOnErrorListener { player, _, _ ->
                            synchronized(voiceLock) {
                                safelyReleasePlayer(player)
                                if (activeVoicePlayer === player) {
                                    activeVoicePlayer = null
                                    updateVoicePlaying(false)
                                }
                            }
                            true
                        }
                        mp.start()
                    } else if (isTtsReady && fallbackText.isNotEmpty()) {
                        updateVoicePlaying(true)
                        tts?.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, "kid_voice")
                    }
                } catch (_: Throwable) {
                    if (isTtsReady && fallbackText.isNotEmpty()) {
                        try {
                            updateVoicePlaying(true)
                            tts?.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, "kid_voice")
                        } catch (_: Throwable) {
                            updateVoicePlaying(false)
                        }
                    } else {
                        updateVoicePlaying(false)
                    }
                }
            }
        }
    }

    /**
     * Phát đoạn giọng nói thô theo resource ID (phục vụ Chế độ Bài học & Câu đố).
     * Tự động bỏ qua nếu resId = 0 (file âm thanh chưa được nạp vào raw).
     */
    fun speakRaw(context: Context, resId: Int, fallbackText: String = "") {
        if (resId != 0) {
            playVoiceClip(context, resId, fallbackText)
        }
    }

    // =========================================================================
    // 1. CHÀO HỎI & VÁN MỚI
    // =========================================================================

    fun speakWelcomeDau(context: Context) {
        val chosen = pickNonRepeating(
            listOf(
                R.raw.voice_welcome_dau_1 to "Chào bé Đậu dễ thương! Hôm nay mình cùng học cờ vua thiệt vui nha!",
                R.raw.voice_welcome_dau_2 to "Đậu ơi! Bàn cờ sẵn sàng rồi nè, Đậu chọn một quân lính để xuất phát nghen!"
            )
        )
        playVoiceClip(context, chosen.first, chosen.second)
    }

    fun speakNewGameDau(context: Context) {
        playVoiceClip(
            context,
            R.raw.voice_new_game_dau,
            "Ván cờ mới bắt đầu rồi! Chúc bé Đậu của mình đánh thắng thiệt giòn giã nha!"
        )
    }

    fun speakBotGreeting(context: Context, difficulty: Difficulty) {
        val (resId, text) = when (difficulty) {
            Difficulty.EASY -> R.raw.voice_bot_dino to "Xin chào bé Đậu! Mình là Khủng Long Nhí vui vẻ nè! Đậu chơi cờ với mình nha!"
            Difficulty.MEDIUM -> R.raw.voice_bot_kingkong to "Chào bé Đậu nha! Mình là King Kong Con khỏe mạnh đây! Đậu cố gắng thắng mình nghen!"
            Difficulty.HARD -> R.raw.voice_bot_godzilla to "Chào Đậu ơi! Mình là Rồng Nhí siêu cấp nè! Bé Đậu đã sẵn sàng thi tài chưa ta?"
        }
        playVoiceClip(context, resId, text)
    }

    // =========================================================================
    // 2. DẠY ĐẬU CHỌN TỪNG QUÂN CỜ (2 BIẾN THỂ CHỐNG LẶP CHO MỖI QUÂN)
    // =========================================================================

    fun speakPieceSelection(context: Context, pieceType: PieceType) {
        val options = when (pieceType) {
            PieceType.PAWN -> listOf(
                R.raw.voice_select_pawn to "Đậu chọn Lính Tốt dũng cảm nè! Lính Tốt đi thẳng về phía trước từng bước nha Đậu!",
                R.raw.voice_select_pawn_2 to "Lính Tốt nhỏ mà có võ đó Đậu ơi! Tiến lên cuối bàn cờ là được biến hình luôn nha!"
            )
            PieceType.KNIGHT -> listOf(
                R.raw.voice_select_knight to "Ngựa Chiến của Đậu xuất kích! Ngựa nhảy hình chữ L qua đầu mấy quân khác được luôn đó Đậu!",
                R.raw.voice_select_knight_2 to "Đậu chọn Ngựa Chiến thông minh nè! Nhảy chữ L thiệt khéo để bắt quân đối phương nha!"
            )
            PieceType.BISHOP -> listOf(
                R.raw.voice_select_bishop to "Quân Tượng thông thái nè Đậu ơi! Tượng trượt đường chéo thiệt nhanh khắp bàn cờ nha!",
                R.raw.voice_select_bishop_2 to "Đậu chọn Quân Tượng rồi! Mình nhắm đường chéo nào trống để trượt tới nghen Đậu!"
            )
            PieceType.ROOK -> listOf(
                R.raw.voice_select_rook to "Xe Pháo Đài vững chắc của Đậu đây! Xe chạy thẳng ngang dọc mạnh mẽ lắm nha!",
                R.raw.voice_select_rook_2 to "Oa, Xe Pháo Đài xuất trận kìa Đậu ơi! Đi ngang hay đi dọc đều siêu mạnh luôn!"
            )
            PieceType.QUEEN -> listOf(
                R.raw.voice_select_queen to "Đậu chọn Nữ Hoàng quyền năng luôn! Nữ Hoàng đi ngang, đi dọc hay đi chéo gì cũng được hết trơn!",
                R.raw.voice_select_queen_2 to "Nữ Hoàng mạnh nhất bàn cờ của Đậu ra tay rồi! Đậu chọn ô đi thiệt chuẩn nha!"
            )
            PieceType.KING -> listOf(
                R.raw.voice_select_king to "Đức Vua tối cao của Đậu nè! Đậu nhớ bảo vệ Vua cẩn thận và đi từng bước một thôi nha!",
                R.raw.voice_select_king_2 to "Đức Vua đi chậm mà chắc nha Đậu ơi! Đừng để quân đối phương tới gần Vua nghen!"
            )
        }
        val chosen = pickNonRepeating(options)
        playVoiceClip(context, chosen.first, chosen.second)
    }

    // =========================================================================
    // 3. PHẢN HỒI KHI BÉ ĐẬU ĐI NƯỚC CỜ
    // =========================================================================

    fun speakPlayerMoveReaction(
        context: Context,
        isCapture: Boolean,
        isCheck: Boolean,
        isCheckmate: Boolean,
        isCastling: Boolean = false,
        isDraw: Boolean = false
    ) {
        when {
            isCheckmate -> {
                val chosen = pickNonRepeating(
                    listOf(
                        R.raw.voice_victory_player to "Hoan hô! Bé Đậu chiến thắng tuyệt vời luôn! Mình xin bái phục Đậu đó nha!",
                        R.raw.voice_victory_player_2 to "Xuất sắc quá Đậu ơi! Đậu học cờ vua giỏi số một luôn, thưởng cho Đậu thiệt nhiều ngôi sao nè!"
                    )
                )
                playVoiceClip(context, chosen.first, chosen.second)
            }
            isDraw -> {
                playVoiceClip(
                    context,
                    R.raw.voice_draw,
                    "Ván cờ hòa rồi Đậu ơi! Hai tụi mình tài ngang sức ngang tài luôn nha!"
                )
            }
            isCheck -> {
                val chosen = pickNonRepeating(
                    listOf(
                        R.raw.voice_check_player to "Trời ơi, Đậu chiếu tướng mình rồi! Đậu đánh hay quá vậy nè!",
                        R.raw.voice_check to "Ái chà, cẩn thận nha Đậu ơi! Chiếu tướng rồi kìa!"
                    )
                )
                playVoiceClip(context, chosen.first, chosen.second)
            }
            isCastling -> {
                playVoiceClip(
                    context,
                    R.raw.voice_castling,
                    "Tuyệt vời! Đậu vừa đi nước Nhập thành, bảo vệ Đức Vua an toàn trong pháo đài luôn!"
                )
            }
            isCapture -> {
                val chosen = pickNonRepeating(
                    listOf(
                        R.raw.voice_player_capture_1 to "Ối! Đậu bắt mất quân của mình rồi! Đậu đánh giỏi quá trời luôn!",
                        R.raw.voice_player_capture_2 to "Oa! Hoan hô bé Đậu! Một pha bắt quân siêu đỉnh luôn nha!",
                        R.raw.voice_player_capture_3 to "Hu hu, mất tiêu một quân lính của mình rồi! Đậu tinh mắt thiệt đó nha!",
                        R.raw.voice_player_capture_4 to "Quá xuất sắc luôn Đậu ơi! Đậu vừa thu phục thêm một quân cờ nữa kìa!"
                    )
                )
                playVoiceClip(context, chosen.first, chosen.second)
            }
            else -> {
                val chosen = pickNonRepeating(
                    listOf(
                        R.raw.voice_player_move_1 to "Nước đi hay quá Đậu ơi! Đợi mình gãi đầu suy nghĩ chút xíu nha!",
                        R.raw.voice_player_move_2 to "Chà, hôm nay Đậu đánh cờ khéo ghê ta! Tới lượt mình tính nước đi nè!",
                        R.raw.voice_player_move_3 to "Đậu thông minh quá chừng luôn! Để mình xem coi nên đi quân nào tiếp đây ta!",
                        R.raw.voice_player_move_4 to "Hay lắm Đậu nha! Nước cờ này làm mình phải suy nghĩ kỹ mới được!",
                        R.raw.voice_player_move_5 to "Giỏi quá Đậu ơi! Tới lượt mình đi rồi, Đậu quan sát bàn cờ nha!"
                    )
                )
                playVoiceClip(context, chosen.first, chosen.second)
            }
        }
    }

    // =========================================================================
    // 4. PHẢN HỒI KHI BOT ĐI NƯỚC CỜ
    // =========================================================================

    fun speakBotMoveReaction(
        context: Context,
        isCapture: Boolean,
        isCheck: Boolean,
        isCheckmate: Boolean,
        isDraw: Boolean = false
    ) {
        when {
            isCheckmate -> {
                playVoiceClip(
                    context,
                    R.raw.voice_victory_bot,
                    "Ván này mình thắng mất rồi! Đậu đánh hay lắm, mình chơi lại ván nữa để Đậu phục thù nha!"
                )
            }
            isDraw -> {
                playVoiceClip(
                    context,
                    R.raw.voice_draw,
                    "Ván cờ hòa rồi Đậu ơi! Hai tụi mình tài ngang sức ngang tài luôn nha!"
                )
            }
            isCheck -> {
                val chosen = pickNonRepeating(
                    listOf(
                        R.raw.voice_check_bot to "Ủa, Đức Vua đang bị chiếu tướng kìa Đậu ơi! Đậu mau cứu Vua nha!",
                        R.raw.voice_check to "Ái chà, cẩn thận nha Đậu ơi! Chiếu tướng rồi kìa!"
                    )
                )
                playVoiceClip(context, chosen.first, chosen.second)
            }
            isCapture -> {
                val chosen = pickNonRepeating(
                    listOf(
                        R.raw.voice_bot_capture to "Hì hì, mình bắt được một quân rồi! Hông sao đâu Đậu ơi, Đậu cố lên nha!",
                        R.raw.voice_bot_capture_2 to "Đậu mất một lính rồi, nhưng Đậu đừng buồn nha, mình tìm cách bắt lại nghen!",
                        R.raw.voice_bot_capture_3 to "Cố lên Đậu ơi! Quan sát kỹ bàn cờ hoặc bấm nút Mách nước để mình phụ Đậu nha!"
                    )
                )
                playVoiceClip(context, chosen.first, chosen.second)
            }
            else -> {
                // Nước đi thường của bot chỉ phát âm thanh gỗ vật lý để không làm ồn
            }
        }
    }

    // =========================================================================
    // 5. PHONG CẤP, MÁCH NƯỚC & NHẮC SUY NGHĨ
    // =========================================================================

    fun speakPromotionPrompt(context: Context) {
        playVoiceClip(
            context,
            R.raw.voice_promotion,
            "Quá giỏi luôn Đậu ơi! Lính Tốt tới cuối bàn cờ rồi, Đậu chọn biến hình thành Nữ Hoàng nha!"
        )
    }

    fun speakHintActive(context: Context) {
        playVoiceClip(
            context,
            R.raw.voice_hint_active,
            "Mình mách nước cho Đậu rồi đó! Đậu hãy đi quân cờ đang phát sáng màu xanh nha!"
        )
    }

    fun speakIdleEncouragement(context: Context) {
        val chosen = pickNonRepeating(
            listOf(
                R.raw.voice_idle_encourage_1 to "Đậu ơi, Đậu đang suy nghĩ nước đi hả? Nếu khó quá thì bấm nút bóng đèn để mình mách nước cho nha!",
                R.raw.voice_idle_encourage_2 to "Tới lượt bé Đậu đi cờ đó! Đậu thử chọn một quân cờ có vòng sáng màu vàng xem sao nghen!"
            )
        )
        playVoiceClip(context, chosen.first, chosen.second)
    }

    // =========================================================================
    // 6. ĐỌC NÚT MENU CHO BÉ CHƯA BIẾT CHỮ
    // =========================================================================

    fun speakMenuButton(context: Context, menuIndex: Int, difficulty: Difficulty) {
        when (menuIndex) {
            0 -> playVoiceClip(context, R.raw.voice_menu_resume, "Tiếp tục chơi cờ nha Đậu!")
            1 -> playVoiceClip(context, R.raw.voice_menu_undo, "Đi lại nước cờ vừa rồi!")
            2 -> playVoiceClip(context, R.raw.voice_menu_hint, "Bóng đèn mách nước đi hay cho Đậu!")
            3 -> speakBotGreeting(context, difficulty)
            4 -> playVoiceClip(context, R.raw.voice_menu_new_game, "Bắt đầu ván cờ mới nha!")
            5 -> playVoiceClip(context, R.raw.voice_menu_color, "Đổi màu quân cờ của Đậu!")
            6 -> playVoiceClip(context, R.raw.voice_menu_sound, "Bật tắt âm thanh!")
            7 -> playVoiceClip(context, R.raw.voice_menu_exit, "Thoát ra màn hình ti vi!")
        }
    }

    // =========================================================================
    // 7. ÂM THANH HIỆU ỨNG GỖ THẬT ĐỘ TRỄ 0ms (SOUNDPOOL + ACOUSTIC SFX SYNTHESIZER)
    // =========================================================================

    private fun playSfxResource(
        resId: Int,
        baseVolume: Float = 1.0f,
        fallbackSynthesizer: () -> ShortArray
    ) {
        if (!isSoundEnabled) return
        val scale = currentSfxVolumeScale()
        val vol = (baseVolume * scale).coerceIn(0f, 1f)

        var playedByPool = false
        val pool = soundPool
        if (isSoundPoolReady && pool != null) {
            val soundId = soundIdMap[resId] ?: 0
            if (soundId > 0 && loadedSoundIds.contains(soundId)) {
                try {
                    val streamId = pool.play(soundId, vol, vol, 1, 0, 1.0f)
                    if (streamId != 0) {
                        playedByPool = true
                    }
                } catch (_: Throwable) {}
            }
        }

        if (!playedByPool) {
            scope.launch {
                val samples = fallbackSynthesizer()
                AcousticSfxSynthesizer.playTrack(samples)
            }
        }
    }

    fun playNavTick() {
        playSfxResource(R.raw.sfx_wood_nav, baseVolume = 0.55f) {
            AcousticSfxSynthesizer.synthesizeNavMarimba(currentSfxVolumeScale())
        }
    }

    fun playSelect() {
        playSfxResource(R.raw.sfx_wood_select, baseVolume = 0.70f) {
            AcousticSfxSynthesizer.synthesizeSelectChime(currentSfxVolumeScale())
        }
    }

    fun playSlideWhoosh() {
        playSfxResource(R.raw.sfx_wood_slide, baseVolume = 0.65f) {
            AcousticSfxSynthesizer.synthesizeSlideWhoosh(currentSfxVolumeScale())
        }
    }

    fun playMove(isHeavy: Boolean = false) {
        val resId = if (isHeavy) R.raw.sfx_wood_move_heavy else R.raw.sfx_wood_move_soft
        playSfxResource(resId, baseVolume = 0.90f) {
            AcousticSfxSynthesizer.synthesizeWoodMove(isHeavy, currentSfxVolumeScale())
        }
    }

    fun playCapture() {
        playSfxResource(R.raw.sfx_wood_capture, baseVolume = 0.95f) {
            AcousticSfxSynthesizer.synthesizeCaptureImpact(currentSfxVolumeScale())
        }
    }

    fun playCheck() {
        playSfxResource(R.raw.sfx_wood_check, baseVolume = 0.85f) {
            AcousticSfxSynthesizer.synthesizeCheckAlert(currentSfxVolumeScale())
        }
    }

    fun playGameOver(isVictory: Boolean) {
        val resId = if (isVictory) R.raw.sfx_wood_fanfare_win else R.raw.sfx_wood_fanfare_loss
        playSfxResource(resId, baseVolume = 0.90f) {
            AcousticSfxSynthesizer.synthesizeGameOverFanfare(isVictory, currentSfxVolumeScale())
        }
    }

    /**
     * Giải phóng tài nguyên âm thanh khi thoát ứng dụng.
     */
    fun release() {
        synchronized(voiceLock) {
            try {
                activeVoicePlayer?.let { player ->
                    safelyReleasePlayer(player)
                }
            } catch (_: Throwable) {}
            activeVoicePlayer = null
            updateVoicePlaying(false)
        }
        try {
            soundPool?.release()
        } catch (_: Throwable) {}
        soundPool = null
        soundIdMap.clear()
        loadedSoundIds.clear()
        isSoundPoolReady = false

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        isTtsReady = false
    }
}
