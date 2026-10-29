package com.harrylong.tvchess.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harrylong.tvchess.R
import com.harrylong.tvchess.audio.TvSoundManager
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.learn.*
import com.harrylong.tvchess.model.*
import com.harrylong.tvchess.storage.GameStorage
import com.harrylong.tvchess.ui.BoardAnimationEffects
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawMoveGuideArrow
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawRichWoodSquare
import com.harrylong.tvchess.ui.ChessPieceRenderer
import com.harrylong.tvchess.ui.ChessPieceRenderer.drawChessPiece
import com.harrylong.tvchess.ui.DeviceProfile
import com.harrylong.tvchess.ui.LocalDeviceProfile
import kotlin.math.sin

// Bảng màu giao diện bài học
private val BgDarkPrimary = Color(0xFF0F172A)
private val BgDarkSecondary = Color(0xFF1E293B)
private val CardSurface = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val GoldAccent = Color(0xFFFACC15)
private val GoldDeep = Color(0xFFEAB308)
private val CyanFocus = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val RedAlert = Color(0xFFEF4444)

/**
 * Màn hình Thực hành & Bài học cờ vua trực quan cho Bé Đậu.
 * Hỗ trợ D-Pad điều hướng TV và cảm ứng điện thoại (dọc & ngang).
 */
@Composable
fun LessonScreen(
    lessonId: String,
    onBack: () -> Unit,
    onGraduate: () -> Unit,
    onNextLesson: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val deviceProfile = LocalDeviceProfile.current

    val lesson = remember(lessonId) {
        LessonRepository.getLessonById(lessonId) ?: LessonRepository.lessons.first()
    }

    var currentStepIndex by remember(lessonId) { mutableIntStateOf(0) }
    val step = lesson.steps.getOrElse(currentStepIndex) { lesson.steps.last() }

    // Quản lý số lần sai & tính số sao
    var totalMistakesInLesson by remember(lessonId) { mutableIntStateOf(0) }
    var currentStepMistakes by remember(lessonId, currentStepIndex) { mutableIntStateOf(0) }

    // Trạng thái thế cờ hiện tại
    var boardState by remember(lessonId, currentStepIndex) {
        val fen = when (step) {
            is ExplainStep -> step.fen
            is PracticeStep -> step.fen
            is QuizStep -> step.fen
            is GraduationStep -> "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        }
        mutableStateOf(ChessEngine.stateFromFen(fen))
    }

    var selectedPos by remember(lessonId, currentStepIndex) { mutableStateOf<Position?>(null) }
    var stepCompleted by remember(lessonId, currentStepIndex) { mutableStateOf(false) }
    var speechText by remember(lessonId, currentStepIndex) { mutableStateOf(step.kidText) }

    // Quiz & Star Collection state
    var collectedStars by remember(lessonId, currentStepIndex) { mutableStateOf(emptySet<Position>()) }
    var quizSelectedSquares by remember(lessonId, currentStepIndex) { mutableStateOf(emptySet<Position>()) }

    // Hoàn thành cả bài học
    var isLessonCompleted by remember(lessonId) { mutableStateOf(false) }
    var earnedStars by remember(lessonId) { mutableIntStateOf(0) }

    // D-Pad cursor trên TV
    var cursorPos by remember { mutableStateOf(Position(6, 4)) }
    val boardFocusRequester = remember { FocusRequester() }
    val nextBtnFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        onBack()
    }

    // Tự động phát giọng nói khi bước mới xuất hiện
    LaunchedEffect(lessonId, currentStepIndex) {
        speechText = step.kidText
        LessonVoiceHelper.speak(context, step.voiceKey, step.kidText)
        if (step is ExplainStep) {
            // Với bước giải thích, bé có thể xem và bấm Tiếp tục bất cứ lúc nào
            stepCompleted = true
        }
        try {
            boardFocusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    // Hàm xử lý khi chọn ô trên bàn cờ
    fun handleSquareClick(clickedPos: Position) {
        cursorPos = clickedPos

        if (stepCompleted && step !is QuizStep) return

        when (step) {
            is ExplainStep -> {
                TvSoundManager.playNavTick()
            }

            is PracticeStep -> {
                val currentSelected = selectedPos
                if (currentSelected == null) {
                    // Chọn quân cờ
                    val piece = boardState.pieceAt(clickedPos)
                    if (piece != null && piece.color == boardState.turn) {
                        selectedPos = clickedPos
                        TvSoundManager.playSelect()
                    }
                } else {
                    if (currentSelected == clickedPos) {
                        // Bỏ chọn
                        selectedPos = null
                        TvSoundManager.playNavTick()
                    } else {
                        // Thử đi quân
                        val pieceAtTarget = boardState.pieceAt(clickedPos)
                        if (pieceAtTarget != null && pieceAtTarget.color == boardState.turn) {
                            // Đổi sang chọn quân cờ khác của mình
                            selectedPos = clickedPos
                            TvSoundManager.playSelect()
                            return
                        }

                        val legalMoves = ChessEngine.getLegalMovesForPiece(boardState, currentSelected)
                        val matchingMove = legalMoves.find { it.to == clickedPos }

                        if (matchingMove == null) {
                            // Nước đi không hợp lệ theo luật cờ
                            currentStepMistakes++
                            totalMistakesInLesson++
                            TvSoundManager.playNavTick()
                            val voiceRes = if (currentStepMistakes >= 2) {
                                speechText = "Đậu xem ô viền vàng nhấp nháy để đi đúng nha! 💡"
                                R.raw.voice_hint_active
                            } else {
                                speechText = step.hint ?: "Nước đi này chưa đúng luật rồi Đậu ơi, thử lại nha! 😊"
                                R.raw.voice_idle_encourage_1
                            }
                            TvSoundManager.speakRaw(context, voiceRes, speechText)
                        } else {
                            // Nước đi hợp lệ theo luật cờ, kiểm tra mục tiêu bài học
                            var isGoalAchieved = false

                            when (val goal = step.goal) {
                                is PracticeGoal.SpecificMove -> {
                                    if (currentSelected == goal.from && clickedPos == goal.to &&
                                        (goal.promotionPiece == null || matchingMove.promotionPiece == goal.promotionPiece)
                                    ) {
                                        isGoalAchieved = true
                                    }
                                }
                                is PracticeGoal.MovePieceToTarget -> {
                                    if (clickedPos in goal.targetSquares) {
                                        isGoalAchieved = true
                                    }
                                }
                                is PracticeGoal.CaptureAllTargets -> {
                                    if (clickedPos in goal.targetSquares) {
                                        val newCollected = collectedStars + clickedPos
                                        collectedStars = newCollected
                                        if (newCollected.containsAll(goal.targetSquares)) {
                                            isGoalAchieved = true
                                        } else {
                                            // Nước đi ăn sao trung gian hợp lệ: cập nhật bàn cờ, không phạt lỗi!
                                            boardState = if (matchingMove.capturedPiece != null) {
                                                TvSoundManager.playCapture()
                                                ChessEngine.applyMove(boardState, matchingMove)
                                            } else {
                                                TvSoundManager.playMove()
                                                ChessEngine.applyMove(boardState, matchingMove)
                                            }
                                            selectedPos = null
                                            val remaining = goal.targetSquares.size - newCollected.size
                                            speechText = "Đậu ăn được 1 sao rồi! Ăn tiếp $remaining sao còn lại nha! ⭐"
                                            TvSoundManager.speakRaw(
                                                context,
                                                R.raw.voice_player_capture_1,
                                                speechText
                                            )
                                            return
                                        }
                                    }
                                }
                                is PracticeGoal.GiveCheckmate -> {
                                    val nextState = ChessEngine.applyMove(boardState, matchingMove)
                                    if (nextState.status == GameStatus.CHECKMATE) {
                                        isGoalAchieved = true
                                    }
                                }
                                is PracticeGoal.EscapeCheck -> {
                                    val nextState = ChessEngine.applyMove(boardState, matchingMove)
                                    val stillCheck = ChessEngine.isKingInCheck(nextState.board, boardState.turn)
                                    if (!stillCheck) {
                                        isGoalAchieved = true
                                    }
                                }
                            }

                            if (isGoalAchieved) {
                                // THÀNH CÔNG!
                                boardState = if (matchingMove.capturedPiece != null) {
                                    TvSoundManager.playCapture()
                                    ChessEngine.applyMove(boardState, matchingMove)
                                } else {
                                    TvSoundManager.playMove()
                                    ChessEngine.applyMove(boardState, matchingMove)
                                }
                                selectedPos = null
                                stepCompleted = true
                                TvSoundManager.playGameOver(isVictory = true)
                                speechText = "Đậu giỏi quá nè! Đúng rồi đó nha! 🎉"
                                TvSoundManager.speakRaw(
                                    context,
                                    R.raw.voice_victory_player_2,
                                    "Xuất sắc quá Đậu ơi! Đậu học cờ vua giỏi số một luôn, thưởng cho Đậu thiệt nhiều ngôi sao nè!"
                                )
                                try {
                                    nextBtnFocusRequester.requestFocus()
                                } catch (_: Throwable) {}
                            } else {
                                // Đúng luật cờ nhưng chưa đạt mục tiêu bài học
                                currentStepMistakes++
                                totalMistakesInLesson++
                                TvSoundManager.playNavTick()
                                val voiceRes = if (currentStepMistakes >= 2) {
                                    speechText = "Đậu xem ô viền vàng nhấp nháy để đi đúng nha! 💡"
                                    R.raw.voice_hint_active
                                } else {
                                    speechText = step.hint ?: "Chưa đúng mục tiêu rồi Đậu ơi, thử lại nghen! 😊"
                                    R.raw.voice_idle_encourage_2
                                }
                                TvSoundManager.speakRaw(context, voiceRes, speechText)
                            }
                        }
                    }
                }
            }

            is QuizStep -> {
                if (clickedPos in step.correctSquares) {
                    val updated = quizSelectedSquares + clickedPos
                    quizSelectedSquares = updated
                    TvSoundManager.playSelect()
                    if (updated.containsAll(step.correctSquares)) {
                        stepCompleted = true
                        TvSoundManager.playGameOver(isVictory = true)
                        speechText = "Oa, bé Đậu tìm đúng hết tất cả các ô rồi! Xuất sắc quá! 🎉"
                        TvSoundManager.speakRaw(
                            context,
                            R.raw.voice_victory_player_2,
                            "Xuất sắc quá Đậu ơi! Đậu học cờ vua giỏi số một luôn, thưởng cho Đậu thiệt nhiều ngôi sao nè!"
                        )
                        try {
                            nextBtnFocusRequester.requestFocus()
                        } catch (_: Throwable) {}
                    } else {
                        val remaining = step.correctSquares.size - updated.size
                        speechText = "Đúng một ô rồi Đậu ơi! Còn $remaining ô nữa nha! ✨"
                        TvSoundManager.speakRaw(context, R.raw.voice_player_move_1, speechText)
                    }
                } else {
                    currentStepMistakes++
                    totalMistakesInLesson++
                    TvSoundManager.playNavTick()
                    val voiceRes = if (currentStepMistakes >= 2) {
                        speechText = step.hint ?: "Đậu xem kỹ lại màu ô và đường đi nha! 💡"
                        R.raw.voice_hint_active
                    } else {
                        speechText = "Ô này chưa đúng rồi Đậu ơi, thử chạm ô khác nghen! 😊"
                        R.raw.voice_idle_encourage_2
                    }
                    TvSoundManager.speakRaw(context, voiceRes, speechText)
                }
            }

            is GraduationStep -> {
                // Tốt nghiệp
            }
        }
    }

    // Chuyển sang bước kế tiếp hoặc hoàn thành bài học
    fun advanceStep() {
        if (currentStepIndex < lesson.steps.lastIndex) {
            currentStepIndex++
        } else {
            // Hoàn thành cả bài học!
            val stars = when {
                totalMistakesInLesson == 0 -> 3
                totalMistakesInLesson <= 2 -> 2
                else -> 1
            }
            earnedStars = stars
            GameStorage.saveLessonStars(context, lesson.id, stars)
            isLessonCompleted = true
            TvSoundManager.playGameOver(isVictory = true)
            TvSoundManager.speakRaw(
                context,
                R.raw.voice_victory_player,
                "Hoan hô! Bé Đậu chiến thắng tuyệt vời luôn! Mình xin bái phục Đậu đó nha!"
            )
        }
    }

    // Bố cục tổng thể
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BgDarkSecondary, BgDarkPrimary),
                    radius = 1800f
                )
            )
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP BAR: Nút Quay Lại + Tên Bài + Tiến độ chấm tròn
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Nút quay lại
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "⬅️", fontSize = 16.sp)
                    Text(text = "Bản đồ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Tiêu đề bài học
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${lesson.emoji} Bài ${lesson.orderNumber}: ${lesson.title}",
                        fontSize = if (deviceProfile == DeviceProfile.PHONE) 16.sp else 20.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldAccent
                    )
                    Text(
                        text = step.title,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Tiến độ chấm tròn (Step dots)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in lesson.steps.indices) {
                        val isCurrent = i == currentStepIndex
                        val isPast = i < currentStepIndex
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCurrent -> GoldAccent
                                        isPast -> EmeraldSuccess
                                        else -> Color(0xFF475569)
                                    }
                                )
                        )
                    }
                }
            }

            // MAIN CONTENT: Trên TV / Tablet / Điện thoại Ngang (Bàn cờ trái, Bảng thông tin phải)
            val isPortrait = false

            if (isPortrait) {
                // DIỆN THOẠI DỌC: Mascot & Speech ở trên -> Bàn cờ ở giữa -> Nút hành động ở dưới
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bong bóng thoại Bé Đậu
                    MascotSpeechBubble(
                        speechText = speechText,
                        onReplayVoice = {
                            LessonVoiceHelper.speak(context, step.voiceKey, speechText)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bàn cờ cờ vua
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LessonBoardView(
                            boardState = boardState,
                            step = step,
                            selectedPos = selectedPos,
                            cursorPos = cursorPos,
                            currentStepMistakes = currentStepMistakes,
                            collectedStars = collectedStars,
                            quizSelectedSquares = quizSelectedSquares,
                            focusRequester = boardFocusRequester,
                            onSquareClick = { handleSquareClick(it) }
                        )
                    }

                    // Thanh điều khiển dưới
                    LessonControlBar(
                        step = step,
                        stepCompleted = stepCompleted,
                        onNext = { advanceStep() },
                        onReplayVoice = {
                            LessonVoiceHelper.speak(context, step.voiceKey, speechText)
                        },
                        onGraduate = onGraduate,
                        nextBtnFocusRequester = nextBtnFocusRequester
                    )
                }
            } else {
                // TV / TABLET / DIỆN THOẠI NGANG: Bàn cờ bên trái, Bảng thông tin & Mascot bên phải
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bàn cờ bên trái (giữ tỉ lệ 1:1)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LessonBoardView(
                            boardState = boardState,
                            step = step,
                            selectedPos = selectedPos,
                            cursorPos = cursorPos,
                            currentStepMistakes = currentStepMistakes,
                            collectedStars = collectedStars,
                            quizSelectedSquares = quizSelectedSquares,
                            focusRequester = boardFocusRequester,
                            onSquareClick = { handleSquareClick(it) }
                        )
                    }

                    // Thông tin & Mascot bên phải
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        MascotSpeechBubble(
                            speechText = speechText,
                            onReplayVoice = {
                                LessonVoiceHelper.speak(context, step.voiceKey, speechText)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LessonControlBar(
                            step = step,
                            stepCompleted = stepCompleted,
                            onNext = { advanceStep() },
                            onReplayVoice = {
                                LessonVoiceHelper.speak(context, step.voiceKey, speechText)
                            },
                            onGraduate = onGraduate,
                            nextBtnFocusRequester = nextBtnFocusRequester
                        )
                    }
                }
            }
        }

        // POPUP HOÀN THÀNH BÀI HỌC (VICTORY DIALOG)
        AnimatedVisibility(
            visible = isLessonCompleted,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            LessonVictoryDialog(
                lesson = lesson,
                stars = earnedStars,
                totalMistakes = totalMistakesInLesson,
                onReplay = {
                    isLessonCompleted = false
                    totalMistakesInLesson = 0
                    currentStepIndex = 0
                },
                onNextLesson = {
                    val next = LessonRepository.getNextLesson(lesson.id)
                    if (next != null && onNextLesson != null) {
                        onNextLesson(next.id)
                    } else {
                        onBack()
                    }
                },
                onBackToHub = onBack
            )
        }
    }
}

/**
 * Hiển thị Bàn cờ Cờ vua và các thành phần trực quan hóa bài học.
 */
@Composable
private fun LessonBoardView(
    boardState: GameState,
    step: LessonStep,
    selectedPos: Position?,
    cursorPos: Position,
    currentStepMistakes: Int,
    collectedStars: Set<Position>,
    quizSelectedSquares: Set<Position>,
    focusRequester: FocusRequester,
    onSquareClick: (Position) -> Unit
) {
    // Pulse animation cho ô gợi ý
    val pulseAlpha = remember { Animatable(0.4f) }
    LaunchedEffect(currentStepMistakes) {
        if (currentStepMistakes >= 2) {
            pulseAlpha.animateTo(
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (cursorPos.row > 0) onSquareClick(cursorPos.copy(row = cursorPos.row - 1))
                        true
                    }
                    Key.DirectionDown -> {
                        if (cursorPos.row < 7) onSquareClick(cursorPos.copy(row = cursorPos.row + 1))
                        true
                    }
                    Key.DirectionLeft -> {
                        if (cursorPos.col > 0) onSquareClick(cursorPos.copy(col = cursorPos.col - 1))
                        true
                    }
                    Key.DirectionRight -> {
                        if (cursorPos.col < 7) onSquareClick(cursorPos.copy(col = cursorPos.col + 1))
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.ButtonA -> {
                        onSquareClick(cursorPos)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val boardSide = minOf(maxWidth, maxHeight)
        val textMeasurer = rememberTextMeasurer()

        Canvas(
            modifier = Modifier
                .size(boardSide)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(step) {
                    detectTapGestures { offset ->
                        val squareSizePx = size.width / 8f
                        val col = (offset.x / squareSizePx).toInt().coerceIn(0, 7)
                        val row = (offset.y / squareSizePx).toInt().coerceIn(0, 7)
                        onSquareClick(Position(row, col))
                    }
                }
        ) {
            val squareSize = size.width / 8f

            // 1. VẼ 64 Ô GỖ
            for (r in 0..7) {
                for (c in 0..7) {
                    val isLight = (r + c) % 2 == 0
                    val topLeft = Offset(c * squareSize, r * squareSize)
                    drawRichWoodSquare(topLeft, squareSize, isLight)
                }
            }

            // 1.5. VẼ KÝ TỰ TỌA ĐỘ HÀNG (8..1) VÀ CỘT (a..h) TO RÕ RÀNG
            for (r in 0..7) {
                for (c in 0..7) {
                    val isLight = (r + c) % 2 == 0
                    val topLeft = Offset(c * squareSize, r * squareSize)
                    val textColor = if (isLight) Color(0xFF2B1304) else Color(0xFFFFF2D1)
                    val shadowColor = if (isLight) Color(0xFFFFF7E8).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.75f)
                    val shadowOffset = if (isLight) Offset(-1f, -1f) else Offset(1.5f, 1.5f)
                    val coordFontSize = (squareSize * 0.24f).toSp()

                    if (c == 0) {
                        val rankStr = (8 - r).toString()
                        val textStyle = TextStyle(
                            color = textColor,
                            fontSize = coordFontSize,
                            fontWeight = FontWeight.Black
                        )
                        val shadowStyle = textStyle.copy(color = shadowColor)
                        val rankLayout = textMeasurer.measure(text = rankStr, style = textStyle)
                        val shadowLayout = textMeasurer.measure(text = rankStr, style = shadowStyle)
                        val rankPos = Offset(topLeft.x + squareSize * 0.07f, topLeft.y + squareSize * 0.05f)
                        drawText(textLayoutResult = shadowLayout, topLeft = rankPos + shadowOffset)
                        drawText(textLayoutResult = rankLayout, topLeft = rankPos)
                    }

                    if (r == 7) {
                        val fileStr = ('a' + c).toString()
                        val textStyle = TextStyle(
                            color = textColor,
                            fontSize = coordFontSize,
                            fontWeight = FontWeight.Black
                        )
                        val shadowStyle = textStyle.copy(color = shadowColor)
                        val fileLayout = textMeasurer.measure(text = fileStr, style = textStyle)
                        val shadowLayout = textMeasurer.measure(text = fileStr, style = shadowStyle)
                        val filePos = Offset(
                            topLeft.x + squareSize - fileLayout.size.width - squareSize * 0.07f,
                            topLeft.y + squareSize - fileLayout.size.height - squareSize * 0.05f
                        )
                        drawText(textLayoutResult = shadowLayout, topLeft = filePos + shadowOffset)
                        drawText(textLayoutResult = fileLayout, topLeft = filePos)
                    }
                }
            }

            // 2. TÔ SÁNG Ô THEO BÀI HỌC (HIGHLIGHTS)
            when (step) {
                is ExplainStep -> {
                    for (pos in step.highlightedSquares) {
                        drawRect(
                            color = GoldAccent.copy(alpha = 0.38f),
                            topLeft = Offset(pos.col * squareSize, pos.row * squareSize),
                            size = Size(squareSize, squareSize)
                        )
                    }
                }
                is QuizStep -> {
                    // Ô đã chọn đúng trong Quiz
                    for (pos in quizSelectedSquares) {
                        drawRect(
                            color = EmeraldSuccess.copy(alpha = 0.45f),
                            topLeft = Offset(pos.col * squareSize, pos.row * squareSize),
                            size = Size(squareSize, squareSize)
                        )
                    }
                }
                is PracticeStep -> {
                    // Tô sáng các ô mục tiêu / ngôi sao cần thu thập
                    val targetPositions = when (val goal = step.goal) {
                        is PracticeGoal.CaptureAllTargets -> goal.targetSquares
                        is PracticeGoal.MovePieceToTarget -> goal.targetSquares
                        else -> step.targetSquares
                    }
                    val remainingTargets = targetPositions - collectedStars
                    for (pos in remainingTargets) {
                        // Nền lấp lánh nhẹ của ô mục tiêu
                        drawRect(
                            color = GoldAccent.copy(alpha = 0.26f + 0.12f * pulseAlpha.value),
                            topLeft = Offset(pos.col * squareSize, pos.row * squareSize),
                            size = Size(squareSize, squareSize)
                        )
                    }

                    // Khi sai từ 2 lần trở lên: tự động highlight ô đúng
                    if (currentStepMistakes >= 2) {
                        val hintFrom = step.recommendedFrom
                        val hintTo = step.recommendedTo
                        if (hintFrom != null) {
                            drawRect(
                                color = CyanFocus.copy(alpha = pulseAlpha.value * 0.45f),
                                topLeft = Offset(hintFrom.col * squareSize, hintFrom.row * squareSize),
                                size = Size(squareSize, squareSize)
                            )
                        }
                        if (hintTo != null) {
                            drawRect(
                                color = GoldAccent.copy(alpha = pulseAlpha.value * 0.55f),
                                topLeft = Offset(hintTo.col * squareSize, hintTo.row * squareSize),
                                size = Size(squareSize, squareSize)
                            )
                        }
                    }
                }
                else -> {}
            }

            // 3. VẼ Ô ĐANG CHỌN (SELECTED SQUARE)
            if (selectedPos != null) {
                drawRect(
                    color = CyanFocus.copy(alpha = 0.4f),
                    topLeft = Offset(selectedPos.col * squareSize, selectedPos.row * squareSize),
                    size = Size(squareSize, squareSize)
                )
                drawRect(
                    color = CyanFocus,
                    topLeft = Offset(selectedPos.col * squareSize, selectedPos.row * squareSize),
                    size = Size(squareSize, squareSize),
                    style = Stroke(width = squareSize * 0.08f)
                )
            }

            // 4. VẼ CÁC QUÂN CỜ TRÊN BÀN CỜ
            for (r in 0..7) {
                for (c in 0..7) {
                    val piece = boardState.board[r][c]
                    if (piece != null) {
                        val topLeft = Offset(c * squareSize, r * squareSize)
                        val isSelected = selectedPos == Position(r, c)
                        drawChessPiece(
                            piece = piece,
                            topLeft = topLeft,
                            squareSize = squareSize,
                            isSelected = isSelected
                        )
                    }
                }
            }

            // 4.5. VẼ CÁC NGÔI SAO MỤC TIÊU CHO BƯỚC THỰC HÀNH (PRACTICE)
            if (step is PracticeStep) {
                val targetPositions = when (val goal = step.goal) {
                    is PracticeGoal.CaptureAllTargets -> goal.targetSquares
                    is PracticeGoal.MovePieceToTarget -> goal.targetSquares
                    else -> step.targetSquares
                }
                val remainingTargets = targetPositions - collectedStars
                for (pos in remainingTargets) {
                    val pieceOnTarget = boardState.board[pos.row][pos.col]
                    if (pieceOnTarget == null) {
                        // Ô trống: vẽ ngôi sao to ở chính giữa ô
                        val cx = pos.col * squareSize + squareSize * 0.5f
                        val cy = pos.row * squareSize + squareSize * 0.5f
                        drawPracticeTargetStar(cx, cy, squareSize * 0.28f, pulseAlpha.value)
                    } else {
                        // Ô có quân địch cần bắt: vẽ huy hiệu ngôi sao góc trên-phải ô
                        val cx = pos.col * squareSize + squareSize * 0.74f
                        val cy = pos.row * squareSize + squareSize * 0.26f
                        drawPracticeTargetStar(cx, cy, squareSize * 0.20f, pulseAlpha.value)
                    }
                }
            }

            // 5. VẼ CÁC MŨI TÊN MINH HỌA (ARROWS)
            if (step is ExplainStep) {
                for (arrow in step.arrows) {
                    val fromCenter = Offset(
                        arrow.first.col * squareSize + squareSize / 2f,
                        arrow.first.row * squareSize + squareSize / 2f
                    )
                    val toCenter = Offset(
                        arrow.second.col * squareSize + squareSize / 2f,
                        arrow.second.row * squareSize + squareSize / 2f
                    )
                    drawMoveGuideArrow(
                        fromCenter = fromCenter,
                        toCenter = toCenter,
                        squareSize = squareSize,
                        color = GoldAccent,
                        pulseAlpha = 0.95f
                    )
                }
            } else if (step is PracticeStep && currentStepMistakes >= 2) {
                // Vẽ mũi tên gợi ý nếu sai nhiều
                val hintFrom = step.recommendedFrom
                val hintTo = step.recommendedTo
                if (hintFrom != null && hintTo != null) {
                    val fromCenter = Offset(
                        hintFrom.col * squareSize + squareSize / 2f,
                        hintFrom.row * squareSize + squareSize / 2f
                    )
                    val toCenter = Offset(
                        hintTo.col * squareSize + squareSize / 2f,
                        hintTo.row * squareSize + squareSize / 2f
                    )
                    drawMoveGuideArrow(
                        fromCenter = fromCenter,
                        toCenter = toCenter,
                        squareSize = squareSize,
                        color = GoldAccent,
                        pulseAlpha = pulseAlpha.value
                    )
                }
            }

            // 6. VẼ CON TRỎ D-PAD CHO ĐIỀU KHIỂN TV
            drawRect(
                color = CyanFocus.copy(alpha = 0.85f),
                topLeft = Offset(cursorPos.col * squareSize, cursorPos.row * squareSize),
                size = Size(squareSize, squareSize),
                style = Stroke(width = squareSize * 0.06f)
            )
        }
    }
}

/**
 * Vẽ huy hiệu ngôi sao vàng 5 cánh lấp lánh (Target Star) dành cho bài học thực hành.
 */
private fun DrawScope.drawPracticeTargetStar(
    cx: Float,
    cy: Float,
    outerRadius: Float,
    pulse: Float = 1f
) {
    // Vòng hào quang vàng mềm phát sáng
    drawCircle(
        color = Color(0xFFFDE047).copy(alpha = 0.35f * pulse),
        radius = outerRadius * 1.35f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFFF59E0B).copy(alpha = 0.55f),
        radius = outerRadius * 0.95f,
        center = Offset(cx, cy)
    )

    // Thân ngôi sao 5 cánh hoàng kim
    val starPath = Path().apply {
        val numPoints = 5
        val rOuter = outerRadius
        val rInner = outerRadius * 0.42f
        for (i in 0 until numPoints * 2) {
            val r = if (i % 2 == 0) rOuter else rInner
            val angle = (i * Math.PI / numPoints - Math.PI / 2).toFloat()
            val x = cx + r * kotlin.math.cos(angle)
            val y = cy + r * kotlin.math.sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(starPath, color = Color(0xFFFACC15), style = Fill)
    drawPath(
        starPath,
        color = Color.White,
        style = Stroke(width = (outerRadius * 0.12f).coerceAtLeast(1.5f), join = StrokeJoin.Round)
    )
    // Điểm sáng lấp lánh (Specular glint)
    drawCircle(
        color = Color.White,
        radius = outerRadius * 0.18f,
        center = Offset(cx - outerRadius * 0.25f, cy - outerRadius * 0.28f)
    )
}

/**
 * Bong bóng thoại nhân vật Mascot dễ thương cùng Bé Đậu.
 */
@Composable
private fun MascotSpeechBubble(
    speechText: String,
    onReplayVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(1.5.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar Mascot Chibi
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(2.dp, GoldAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🦖", fontSize = 28.sp)
            }

            // Lời thoại thân thương
            Text(
                text = speechText,
                fontSize = 15.sp,
                color = Color.White,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            // Nút nghe lại giọng đọc
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(GoldDeep.copy(alpha = 0.2f))
                    .clickable { onReplayVoice() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔊", fontSize = 18.sp)
            }
        }
    }
}

/**
 * Thanh nút điều khiển dưới chân màn hình (Nghe lại / Tiếp tục / Tốt nghiệp).
 */
@Composable
private fun LessonControlBar(
    step: LessonStep,
    stepCompleted: Boolean,
    onNext: () -> Unit,
    onReplayVoice: () -> Unit,
    onGraduate: () -> Unit,
    nextBtnFocusRequester: FocusRequester
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nút nghe lại
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .clickable { onReplayVoice() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "🔊", fontSize = 18.sp)
            Text(text = "Nghe lại", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Nút Tiếp Tục hoặc Tốt Nghiệp
        if (step is GraduationStep) {
            Button(
                onClick = onGraduate,
                colors = ButtonDefaults.buttonColors(containerColor = GoldDeep),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .focusRequester(nextBtnFocusRequester)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "⚔️ ĐẤU THỬ VỚI KHỦNG LONG NHÍ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        } else {
            val btnColor = if (stepCompleted) EmeraldSuccess else Color(0xFF475569)
            Button(
                onClick = {
                    if (stepCompleted) {
                        TvSoundManager.playSelect()
                        onNext()
                    }
                },
                enabled = stepCompleted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnColor,
                    disabledContainerColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.focusRequester(nextBtnFocusRequester)
            ) {
                Text(
                    text = if (stepCompleted) "Tiếp tục ➡️" else "Bé hãy làm theo hướng dẫn ✨",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stepCompleted) Color.White else Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Hộp thoại ăn mừng khi hoàn thành cả bài học cờ vua.
 */
@Composable
private fun LessonVictoryDialog(
    lesson: Lesson,
    stars: Int,
    totalMistakes: Int,
    onReplay: () -> Unit,
    onNextLesson: () -> Unit,
    onBackToHub: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .border(2.5.dp, GoldAccent, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "🎉", fontSize = 48.sp)

            Text(
                text = "HOAN HÔ BÉ ĐẬU!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = GoldAccent
            )

            Text(
                text = "Đậu đã hoàn thành xuất sắc Bài ${lesson.orderNumber}: ${lesson.title}!",
                fontSize = 15.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Hiển thị 3 ngôi sao vàng
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (s in 1..3) {
                    Text(
                        text = if (s <= stars) "⭐" else "☆",
                        fontSize = 36.sp,
                        color = if (s <= stars) GoldAccent else Color(0xFF64748B)
                    )
                }
            }

            Text(
                text = when (stars) {
                    3 -> "Tuyệt đối không mắc lỗi nào! Xuất sắc đạt 3 Sao! ⭐⭐⭐"
                    2 -> "Bé chỉ nhầm một xíu thôi! Đạt 2 Sao thật đáng khen! ⭐⭐"
                    else -> "Bé đã kiên trì hoàn thành bài học! Cố gắng tiếp nghen! ⭐"
                },
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Các nút lựa chọn
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReplay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "🔄 Học lại", color = Color.White)
                }

                Button(
                    onClick = onNextLesson,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDeep),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(text = "Bài tiếp theo ➡️", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
