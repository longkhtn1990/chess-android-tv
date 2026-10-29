package com.harrylong.tvchess.ui.game

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harrylong.tvchess.audio.BgmPlayer
import com.harrylong.tvchess.audio.TvSoundManager
import com.harrylong.tvchess.engine.ChessAI
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.input.GamepadAction
import com.harrylong.tvchess.model.*
import com.harrylong.tvchess.storage.GameStorage
import com.harrylong.tvchess.ui.DeviceProfile
import com.harrylong.tvchess.ui.detectDeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel trung tâm quản lý toàn bộ trạng thái và luật chơi của "Đậu Học Cờ Vua".
 * Tách biệt hoàn toàn logic cờ vua, AI quái thú, âm thanh & tương tác D-Pad/Gamepad
 * khỏi mã nguồn giao diện hiển thị (TvChessScreen).
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    // =========================================================================
    // TRẠNG THÁI VÁN CỜ & LỊCH SỬ NƯỚC ĐI
    // =========================================================================

    var stateStack by mutableStateOf<List<GameState>>(listOf(ChessEngine.createInitialState()))
        private set

    val gameState: GameState get() = stateStack.last()

    var playerColor by mutableStateOf(PieceColor.WHITE)
        private set

    var difficulty by mutableStateOf(Difficulty.EASY)
        private set

    var soundEnabled by mutableStateOf(true)
        private set

    var is3DViewMode by mutableStateOf(
        detectDeviceProfile(
            application.applicationContext,
            application.resources.configuration.smallestScreenWidthDp
        ) != DeviceProfile.PHONE
    )

    var navMode by mutableStateOf(BoardNavigationMode.SMART_JUMP)

    var botMood by mutableStateOf(BotMood.IDLE_HAPPY)

    var botSpeechText by mutableStateOf("Chào bé Đậu! Bấm OK hoặc nút A chọn quân nha!")

    var focusZone by mutableStateOf(FocusZone.PIECE_SELECTION)

    var cursorPos by mutableStateOf(Position(6, 4))

    var selectedPiecePos by mutableStateOf<Position?>(null)

    var hintMove by mutableStateOf<ChessMove?>(null)

    var isAiThinking by mutableStateOf(false)
        private set

    var isCalculatingHint by mutableStateOf(false)
        private set

    var pendingPromotionMoves by mutableStateOf<List<ChessMove>>(emptyList())

    var selectedPromotionIndex by mutableIntStateOf(0)

    var selectedGameOverAction by mutableIntStateOf(0)

    var hasDismissedGameOverDialog by mutableStateOf(false)

    var selectedMenuIndex by mutableIntStateOf(0)

    // Job xử lý AI & Idle encouragement
    private var aiJob: Job? = null
    private var idleJob: Job? = null

    // =========================================================================
    // TRẠNG THÁI PHÁI SINH (DERIVED PROPERTIES)
    // =========================================================================

    val isBoardFlipped: Boolean
        get() = playerColor == PieceColor.BLACK

    val isPlayerTurn: Boolean
        get() = !gameState.status.isGameOver && gameState.turn == playerColor

    val movablePiecePositions: List<Position>
        get() = if (isPlayerTurn) {
            ChessEngine.getMovablePiecePositions(gameState, playerColor)
        } else {
            emptyList()
        }

    val movesForSelectedPiece: List<ChessMove>
        get() = selectedPiecePos?.let { ChessEngine.getLegalMovesForPiece(gameState, it) } ?: emptyList()

    val previewMovesForCursorPiece: List<ChessMove>
        get() = if (focusZone == FocusZone.PIECE_SELECTION && isPlayerTurn) {
            ChessEngine.getLegalMovesForPiece(gameState, cursorPos)
        } else {
            emptyList()
        }

    val canUndo: Boolean
        get() = stateStack.size > 1 && !isAiThinking

    init {
        TvSoundManager.initTts(context)
        TvSoundManager.initSoundPool(context)
        loadSavedSession()
    }

    /**
     * Khôi phục ván cờ đã lưu trước đó nếu có.
     */
    fun loadSavedSession(): Boolean {
        val session = GameStorage.loadSession(context) ?: return false
        stateStack = session.stateStack
        playerColor = session.playerColor
        difficulty = session.difficulty
        soundEnabled = session.soundEnabled
        TvSoundManager.isSoundEnabled = soundEnabled
        BgmPlayer.setEnabled(soundEnabled)

        cursorPos = if (playerColor == PieceColor.WHITE) Position(6, 4) else Position(1, 4)
        selectedPiecePos = null
        hintMove = null
        focusZone = FocusZone.PIECE_SELECTION
        hasDismissedGameOverDialog = false

        checkTurnStateAfterRestore()
        return true
    }

    private fun checkTurnStateAfterRestore() {
        if (gameState.status.isGameOver) {
            handleGameOverTrigger()
        } else if (!isPlayerTurn) {
            triggerAiTurnIfNeeded()
        } else {
            restartIdleTimer()
        }
    }

    /**
     * Tự động lưu trạng thái ván cờ hiện tại.
     */
    private fun autoSave() {
        GameStorage.saveSession(context, gameState, playerColor, difficulty, soundEnabled)
    }

    // =========================================================================
    // KHỞI TẠO VÁN MỚI & THAO TÁC CƠ BẢN
    // =========================================================================

    /**
     * Bắt đầu một ván cờ mới cho bé Đậu với màu quân và độ khó đã chọn.
     */
    fun startNewGame(newPlayerColor: PieceColor = playerColor, newDifficulty: Difficulty = difficulty) {
        Log.d("TVCHESS_GAMEPLAY", "startNewGame: playerColor=$newPlayerColor, difficulty=$newDifficulty")
        aiJob?.cancel()
        idleJob?.cancel()

        playerColor = newPlayerColor
        difficulty = newDifficulty
        val initial = ChessEngine.createInitialState()
        stateStack = listOf(initial)
        selectedPiecePos = null
        hintMove = null
        pendingPromotionMoves = emptyList()
        hasDismissedGameOverDialog = false
        focusZone = FocusZone.PIECE_SELECTION
        cursorPos = if (newPlayerColor == PieceColor.WHITE) Position(6, 4) else Position(1, 4)
        botMood = BotMood.IDLE_HAPPY
        botSpeechText = "Ván mới bắt đầu! Chúc bé Đậu chơi vui!"

        autoSave()
        TvSoundManager.speakNewGameDau(context)

        if (newPlayerColor == PieceColor.BLACK) {
            triggerAiTurnIfNeeded()
        } else {
            restartIdleTimer()
        }
    }

    /**
     * Đi lại (Undo) lượt đi vừa rồi.
     */
    fun undoLastTurn() {
        if (!canUndo) return
        aiJob?.cancel()
        isAiThinking = false
        hintMove = null
        selectedPiecePos = null
        hasDismissedGameOverDialog = false

        val currentState = stateStack.last()
        val dropCount = if (stateStack.size >= 3 && currentState.turn == playerColor) 2 else 1
        val newStack = stateStack.dropLast(dropCount).ifEmpty { listOf(ChessEngine.createInitialState()) }
        stateStack = newStack
        focusZone = FocusZone.PIECE_SELECTION

        val movable = ChessEngine.getMovablePiecePositions(newStack.last(), playerColor)
        if (movable.isNotEmpty()) {
            cursorPos = movable.first()
        }

        botMood = BotMood.IDLE_HAPPY
        botSpeechText = "Đã đi lại! Đậu chọn nước đi khác nhé!"
        TvSoundManager.playSelect()

        autoSave()
        restartIdleTimer()
    }

    /**
     * Xin gợi ý nước đi tối ưu (Bóng đèn mách nước).
     */
    fun requestHint() {
        if (!isPlayerTurn || isAiThinking || isCalculatingHint) return
        viewModelScope.launch {
            isCalculatingHint = true
            botMood = BotMood.THINKING
            botSpeechText = "Để tớ tìm nước đi siêu hay mách cho Đậu!"

            val currentState = stateStack.last()
            val best = withContext(Dispatchers.Default) {
                ChessAI.findBestMove(currentState, Difficulty.HARD)
            }
            isCalculatingHint = false
            if (best != null) {
                hintMove = best
                cursorPos = best.from
                selectedPiecePos = null
                focusZone = FocusZone.PIECE_SELECTION
                botMood = BotMood.CLAPPING
                botSpeechText = "Đậu đi ${best.piece.type.kidFriendlyName} màu xanh nhé!"
                TvSoundManager.speakHintActive(context)
            }
        }
    }

    /**
     * Chuyển nhanh con trỏ sang quân cờ đi được kế tiếp (RB / Tab).
     */
    fun cycleNextPiece() {
        if (!isPlayerTurn) return
        val currentMovable = movablePiecePositions
        if (currentMovable.isEmpty()) return
        val currentIndex = currentMovable.indexOf(cursorPos)
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % currentMovable.size else 0
        cursorPos = currentMovable[nextIndex]
        selectedPiecePos = null
        focusZone = FocusZone.PIECE_SELECTION
        TvSoundManager.playNavTick()

        val p = gameState.pieceAt(cursorPos)
        if (p != null) {
            botSpeechText = "${p.type.kidFriendlyName} của Đậu sẵn sàng!"
        }
    }

    // =========================================================================
    // THỰC THI NƯỚC ĐI CỦA NGƯỜI CHƠI
    // =========================================================================

    fun commitPlayerMove(move: ChessMove) {
        val currentState = stateStack.last()
        if (!ChessEngine.isStrictFideLegalMove(currentState, move)) return

        idleJob?.cancel()
        hintMove = null
        selectedPiecePos = null
        pendingPromotionMoves = emptyList()
        focusZone = FocusZone.PIECE_SELECTION
        cursorPos = move.to

        val nextState = ChessEngine.applyMove(currentState, move)
        Log.d("TVCHESS_GAMEPLAY", "Player move executed: from=${move.from} to=${move.to} piece=${move.piece.type} capture=${move.capturedPiece?.type} status=${nextState.status}")
        stateStack = stateStack + nextState

        val isCheck = nextState.status == GameStatus.CHECK || nextState.status == GameStatus.CHECKMATE
        val isCheckmate = nextState.status == GameStatus.CHECKMATE
        val isCapture = move.capturedPiece != null || move.isEnPassant
        val isCastling = move.isKingsideCastle || move.isQueensideCastle
        val isDraw = nextState.status.isGameOver && nextState.status != GameStatus.CHECKMATE

        when {
            isCheck -> TvSoundManager.playCheck()
            isCapture -> TvSoundManager.playCapture()
            else -> TvSoundManager.playMove()
        }

        TvSoundManager.speakPlayerMoveReaction(
            context = context,
            isCapture = isCapture,
            isCheck = isCheck,
            isCheckmate = isCheckmate,
            isCastling = isCastling,
            isDraw = isDraw
        )

        autoSave()

        if (nextState.status.isGameOver) {
            handleGameOverTrigger()
        } else {
            triggerAiTurnIfNeeded()
        }
    }

    // =========================================================================
    // XỬ LÝ LƯỢT ĐI CỦA AI QUÁI THÚ
    // =========================================================================

    private fun triggerAiTurnIfNeeded() {
        if (gameState.status.isGameOver || gameState.turn == playerColor) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            isAiThinking = true
            val lastPlayerMove = gameState.lastMove
            if (lastPlayerMove != null && lastPlayerMove.capturedPiece != null) {
                botMood = BotMood.CRYING
                botSpeechText = "Hu hu! Mất ${lastPlayerMove.capturedPiece.type.kidFriendlyName} của tớ rồi!"
                delay(1300L)
            } else {
                botMood = BotMood.CLAPPING
                botSpeechText = "Quá giỏi luôn Đậu ơi!"
                delay(650L)
            }

            botMood = BotMood.THINKING
            botSpeechText = "Hừm... Để tớ gãi đầu tính nước đi nhé!"

            val minThinkTimeMs = when (difficulty) {
                Difficulty.EASY -> 700L
                Difficulty.MEDIUM -> 1000L
                Difficulty.HARD -> 1200L
            }
            val startAiMs = System.currentTimeMillis()

            val aiMove = withContext(Dispatchers.Default) {
                ChessAI.findBestMove(gameState, difficulty)
            }

            val elapsedMs = System.currentTimeMillis() - startAiMs
            val remainingDelay = (minThinkTimeMs - elapsedMs).coerceAtLeast(0L)
            if (remainingDelay > 0L) {
                delay(remainingDelay)
            }

            if (aiMove != null) {
                val currentState = stateStack.last()
                if (currentState.turn != playerColor && !currentState.status.isGameOver) {
                    val nextState = ChessEngine.applyMove(currentState, aiMove)
                    Log.d("TVCHESS_GAMEPLAY", "AI move executed: from=${aiMove.from} to=${aiMove.to} piece=${aiMove.piece.type} capture=${aiMove.capturedPiece?.type} status=${nextState.status}")
                    stateStack = stateStack + nextState

                    val isCheck = nextState.status == GameStatus.CHECK || nextState.status == GameStatus.CHECKMATE
                    val isCapture = aiMove.capturedPiece != null

                    when {
                        isCheck -> {
                            botMood = BotMood.SURPRISED
                            botSpeechText = "Đậu cẩn thận nghen! Chiếu tướng kìa!"
                            TvSoundManager.playCheck()
                            TvSoundManager.speakBotMoveReaction(
                                context,
                                isCapture,
                                isCheck = true,
                                isCheckmate = nextState.status == GameStatus.CHECKMATE
                            )
                        }
                        isCapture -> {
                            botMood = BotMood.PROUD_LAUGH
                            botSpeechText = "Hì hì, tớ bắt được 1 lính nhé!"
                            TvSoundManager.playCapture()
                            TvSoundManager.speakBotMoveReaction(
                                context,
                                isCapture = true,
                                isCheck = false,
                                isCheckmate = false
                            )
                        }
                        else -> {
                            botMood = BotMood.IDLE_HAPPY
                            botSpeechText = "Tới lượt bé Đậu đi cờ đó!"
                            TvSoundManager.playMove()
                        }
                    }
                }
            }

            isAiThinking = false
            autoSave()

            if (gameState.status.isGameOver) {
                handleGameOverTrigger()
            } else {
                onPlayerTurnStarted()
            }
        }
    }

    private fun onPlayerTurnStarted() {
        val movable = movablePiecePositions
        if (movable.isNotEmpty() && cursorPos !in movable && navMode == BoardNavigationMode.SMART_JUMP) {
            cursorPos = ChessEngine.findNextSmartJump(
                current = cursorPos,
                candidates = movable,
                direction = DPadDirection.UP,
                isBoardFlipped = isBoardFlipped
            )
        }
        if (focusZone == FocusZone.MOVE_SELECTION) {
            focusZone = FocusZone.PIECE_SELECTION
            selectedPiecePos = null
        }
        restartIdleTimer()
    }

    private fun handleGameOverTrigger() {
        val playerWon = gameState.status == GameStatus.CHECKMATE && gameState.turn != playerColor
        Log.d("TVCHESS_GAMEPLAY", "handleGameOverTrigger: status=${gameState.status}, playerWon=$playerWon")

        if (playerWon) {
            GameStorage.incrementWinCount(context, difficulty)
        }

        if (!hasDismissedGameOverDialog) {
            focusZone = FocusZone.GAME_OVER_DIALOG
            selectedGameOverAction = 0

            if (playerWon) {
                botMood = BotMood.CLAPPING
                botSpeechText = "HOAN HÔ! BÉ ĐẬU CHIẾN THẮNG!"
                TvSoundManager.speakPlayerMoveReaction(
                    context = context,
                    isCapture = false,
                    isCheck = false,
                    isCheckmate = true
                )
            } else {
                botMood = BotMood.PROUD_LAUGH
                botSpeechText = "Tớ thắng ván này! Đậu chơi lại với tớ nhé!"
                TvSoundManager.speakBotMoveReaction(
                    context = context,
                    isCapture = false,
                    isCheck = false,
                    isCheckmate = true
                )
            }
        }
    }

    private fun restartIdleTimer() {
        idleJob?.cancel()
        if (!isPlayerTurn || isAiThinking) return
        idleJob = viewModelScope.launch {
            delay(26000L)
            if (isPlayerTurn && !isAiThinking) {
                botSpeechText = "Đậu ơi! Bấm nút X hoặc Bóng Đèn nếu cần mách nước nha!"
                TvSoundManager.speakIdleEncouragement(context)
            }
        }
    }

    // =========================================================================
    // XỬ LÝ NÚT BẤM VÀ DI CHUYỂN CON TRỎ D-PAD
    // =========================================================================

    fun handleBoardAction() {
        if (!isPlayerTurn || isAiThinking) return
        val currentState = stateStack.last()
        val currentMovable = movablePiecePositions

        when (focusZone) {
            FocusZone.PIECE_SELECTION -> {
                if (cursorPos in currentMovable) {
                    val legalForPiece = ChessEngine.getLegalMovesForPiece(currentState, cursorPos)
                    val chosenPiece = currentState.pieceAt(cursorPos)
                    if (legalForPiece.isNotEmpty() && chosenPiece != null) {
                        selectedPiecePos = cursorPos
                        focusZone = FocusZone.MOVE_SELECTION
                        if (hintMove != null && hintMove?.from != cursorPos) {
                            hintMove = null
                        }

                        val targetMoves = legalForPiece.map { it.to }
                        val currentHint = hintMove
                        val preferredTarget = if (currentHint != null && currentHint.from == cursorPos && currentHint.to in targetMoves) {
                            currentHint.to
                        } else when (chosenPiece.type) {
                            PieceType.PAWN -> {
                                val forward2Row = if (chosenPiece.color == PieceColor.WHITE) cursorPos.row - 2 else cursorPos.row + 2
                                val forward1Row = if (chosenPiece.color == PieceColor.WHITE) cursorPos.row - 1 else cursorPos.row + 1
                                targetMoves.firstOrNull { it.row == forward2Row && it.col == cursorPos.col }
                                    ?: targetMoves.firstOrNull { it.row == forward1Row && it.col == cursorPos.col }
                                    ?: targetMoves.first()
                            }
                            PieceType.KNIGHT -> {
                                val forwardDir = if (chosenPiece.color == PieceColor.WHITE) -1 else 1
                                targetMoves.minByOrNull { (it.row - cursorPos.row) * forwardDir } ?: targetMoves.first()
                            }
                            PieceType.KING -> {
                                // Vua: Ưu tiên các ô đi 1 bước bình thường, tránh auto-jump vào ô Nhập thành gây bất ngờ cho người chơi
                                val nonCastleTargets = legalForPiece.filter { !it.isKingsideCastle && !it.isQueensideCastle }.map { it.to }
                                nonCastleTargets.firstOrNull() ?: targetMoves.first()
                            }
                            else -> targetMoves.first()
                        }
                        cursorPos = preferredTarget

                        Log.d("TVCHESS_GAMEPLAY", "Piece selected at $selectedPiecePos, cursor advanced to $cursorPos")
                        botMood = BotMood.SURPRISED
                        val hasCastleMove = legalForPiece.any { it.isKingsideCastle || it.isQueensideCastle }
                        botSpeechText = if (hasCastleMove) {
                            "Đức Vua sẵn sàng! Có cả ô Nhập Thành 🛡️ bảo vệ Vua nữa đó!"
                        } else {
                            "${chosenPiece.type.kidFriendlyName} đã sẵn sàng! Bấm A để đi tới đây, hoặc bấm mũi tên chọn ô khác nhé!"
                        }
                        TvSoundManager.playSelect()
                        TvSoundManager.speakPieceSelection(context, chosenPiece.type)
                    }
                }
            }

            FocusZone.MOVE_SELECTION -> {
                val activeLegalMoves = movesForSelectedPiece
                val matchingMoves = activeLegalMoves.filter { it.to == cursorPos }
                if (matchingMoves.isNotEmpty()) {
                    Log.d("TVCHESS_GAMEPLAY", "Commit move from $selectedPiecePos to $cursorPos")
                    if (matchingMoves.any { it.promotionPiece != null }) {
                        pendingPromotionMoves = matchingMoves
                        selectedPromotionIndex = 0
                        focusZone = FocusZone.PROMOTION_DIALOG
                        TvSoundManager.playSelect()
                        TvSoundManager.speakPromotionPrompt(context)
                    } else {
                        commitPlayerMove(matchingMoves.first())
                    }
                } else if (cursorPos in currentMovable && cursorPos != selectedPiecePos) {
                    val legalForPiece = ChessEngine.getLegalMovesForPiece(currentState, cursorPos)
                    val chosenPiece = currentState.pieceAt(cursorPos)
                    if (legalForPiece.isNotEmpty() && chosenPiece != null) {
                        selectedPiecePos = cursorPos
                        val targets = legalForPiece.map { it.to }
                        cursorPos = targets.first()
                        Log.d("TVCHESS_GAMEPLAY", "Switched piece to $selectedPiecePos, cursor to $cursorPos")
                        botMood = BotMood.SURPRISED
                        botSpeechText = "Đổi sang ${chosenPiece.type.kidFriendlyName}! Bấm A để đi nhé!"
                        TvSoundManager.playSelect()
                        TvSoundManager.speakPieceSelection(context, chosenPiece.type)
                    }
                } else if (cursorPos == selectedPiecePos) {
                    Log.d("TVCHESS_GAMEPLAY", "Deselected piece at $selectedPiecePos")
                    focusZone = FocusZone.PIECE_SELECTION
                    selectedPiecePos = null
                    botMood = BotMood.IDLE_HAPPY
                    botSpeechText = "Đã bỏ chọn! Đậu chọn quân khác nghen!"
                    TvSoundManager.playNavTick()
                } else {
                    Log.d("TVCHESS_GAMEPLAY", "Invalid square clicked $cursorPos -> Deselect")
                    focusZone = FocusZone.PIECE_SELECTION
                    selectedPiecePos = null
                    botMood = BotMood.IDLE_HAPPY
                    botSpeechText = "Đậu chọn lại quân cờ nghen!"
                    TvSoundManager.playNavTick()
                }
            }

            else -> {}
        }
    }

    fun handleDirection(direction: DPadDirection, forceFreeGrid: Boolean = false) {
        val currentState = stateStack.last()
        val currentMovable = movablePiecePositions

        when (focusZone) {
            FocusZone.PIECE_SELECTION -> {
                if (!isPlayerTurn) return

                if (forceFreeGrid || navMode == BoardNavigationMode.FREE_GRID) {
                    val nextPos = cursorPos.stepOnGrid(direction, isBoardFlipped)
                    if (nextPos != cursorPos) {
                        cursorPos = nextPos
                        val p = currentState.pieceAt(nextPos)
                        if (p != null) {
                            botSpeechText = "${p.type.kidFriendlyName} (${p.color.vietnameseName})"
                        }
                        TvSoundManager.playNavTick()
                    }
                } else {
                    if (currentMovable.isEmpty()) return
                    val nextPos = ChessEngine.findNextSmartJump(
                        current = cursorPos,
                        candidates = currentMovable,
                        direction = direction,
                        isBoardFlipped = isBoardFlipped
                    )
                    if (nextPos != cursorPos) {
                        cursorPos = nextPos
                        val p = currentState.pieceAt(nextPos)
                        if (p != null) {
                            botSpeechText = "${p.type.kidFriendlyName} của Đậu sẵn sàng!"
                        }
                        TvSoundManager.playNavTick()
                    }
                }
            }

            FocusZone.MOVE_SELECTION -> {
                val activeLegalMoves = movesForSelectedPiece
                val targetSquares = activeLegalMoves.map { it.to }.distinct()

                if (forceFreeGrid || navMode == BoardNavigationMode.FREE_GRID) {
                    val nextPos = cursorPos.stepOnGrid(direction, isBoardFlipped)
                    if (nextPos != cursorPos) {
                        cursorPos = nextPos
                        val isCap = currentState.pieceAt(nextPos) != null
                        val isTarget = targetSquares.contains(nextPos)
                        val isCastle = activeLegalMoves.any { it.to == nextPos && (it.isKingsideCastle || it.isQueensideCastle) }
                        botMood = if (isCap && isTarget) BotMood.SURPRISED else BotMood.IDLE_HAPPY
                        botSpeechText = when {
                            nextPos == selectedPiecePos -> "Quân đang chọn (Bấm A để hủy chọn)"
                            isCastle -> "Tuyệt chiêu Nhập Thành 🛡️! Vua và Xe đổi chỗ bảo vệ thành trì! Bấm A để đi!"
                            isTarget && isCap -> "Ối! Ô đó bắt được quân! Bấm A để ăn nhé!"
                            isTarget -> "Ô xanh lá hợp lệ! Bấm A để đi tới đây!"
                            else -> "Ô này không đi được, Đậu chọn ô xanh lá nhé!"
                        }
                        TvSoundManager.playNavTick()
                    }
                } else {
                    val candidates = (targetSquares + listOfNotNull(selectedPiecePos)).distinct()
                    if (candidates.isEmpty()) return
                    val nextPos = ChessEngine.findNextSmartJump(
                        current = cursorPos,
                        candidates = candidates,
                        direction = direction,
                        isBoardFlipped = isBoardFlipped
                    )
                    if (nextPos != cursorPos) {
                        cursorPos = nextPos
                        val isCap = currentState.pieceAt(nextPos) != null
                        val isTarget = targetSquares.contains(nextPos)
                        val isCastle = activeLegalMoves.any { it.to == nextPos && (it.isKingsideCastle || it.isQueensideCastle) }
                        botMood = if (isCap && isTarget) BotMood.SURPRISED else BotMood.IDLE_HAPPY
                        botSpeechText = when {
                            nextPos == selectedPiecePos -> "Quân đang chọn (Bấm B để hủy chọn)"
                            isCastle -> "Tuyệt chiêu Nhập Thành 🛡️! Vua và Xe đổi chỗ bảo vệ thành trì! Bấm A để đi!"
                            isTarget && isCap -> "Ối! Ô đó bắt được quân! Bấm A để ăn nhé!"
                            isTarget -> "Ô xanh lá hợp lệ! Bấm A để đi tới đây!"
                            else -> "Bấm A để chọn ô này!"
                        }
                        TvSoundManager.playNavTick()
                    } else {
                        val nextPiece = ChessEngine.findNextSmartJump(
                            current = selectedPiecePos ?: cursorPos,
                            candidates = currentMovable,
                            direction = direction,
                            isBoardFlipped = isBoardFlipped
                        )
                        if (nextPiece != selectedPiecePos && nextPiece != cursorPos) {
                            val nextPieceLegal = ChessEngine.getLegalMovesForPiece(currentState, nextPiece)
                            val p = currentState.pieceAt(nextPiece)
                            if (nextPieceLegal.isNotEmpty() && p != null) {
                                selectedPiecePos = nextPiece
                                val targets = nextPieceLegal.map { it.to }
                                cursorPos = targets.first()
                                botMood = BotMood.SURPRISED
                                botSpeechText = "Đổi sang ${p.type.kidFriendlyName}! Bấm A để đi nhé!"
                                TvSoundManager.playSelect()
                                TvSoundManager.speakPieceSelection(context, p.type)
                            }
                        }
                    }
                }
            }

            FocusZone.SIDE_MENU -> {
                val verticalOrder = listOf(0, 2, 4, 6, 1, 3, 5, 7)
                val pos = verticalOrder.indexOf(selectedMenuIndex).let { if (it >= 0) it else 0 }
                val row = selectedMenuIndex / 2
                val col = selectedMenuIndex % 2
                var nextIndex = selectedMenuIndex

                when (direction) {
                    DPadDirection.UP -> {
                        val prevPos = (pos - 1 + verticalOrder.size) % verticalOrder.size
                        nextIndex = verticalOrder[prevPos]
                    }
                    DPadDirection.DOWN -> {
                        val nextPos = (pos + 1) % verticalOrder.size
                        nextIndex = verticalOrder[nextPos]
                    }
                    DPadDirection.LEFT -> {
                        if (col == 1) {
                            nextIndex = row * 2
                        } else {
                            focusZone = FocusZone.PIECE_SELECTION
                            TvSoundManager.speakMenuButton(context, 0, difficulty)
                            return
                        }
                    }
                    DPadDirection.RIGHT -> {
                        nextIndex = row * 2 + 1
                    }
                }

                if (nextIndex != selectedMenuIndex) {
                    selectedMenuIndex = nextIndex
                    TvSoundManager.playNavTick()
                    TvSoundManager.speakMenuButton(context, nextIndex, difficulty)
                }
            }

            FocusZone.PROMOTION_DIALOG -> {
                val count = PieceType.PROMOTION_OPTIONS.size
                when (direction) {
                    DPadDirection.LEFT, DPadDirection.UP -> {
                        selectedPromotionIndex = (selectedPromotionIndex - 1 + count) % count
                        val promoType = PieceType.PROMOTION_OPTIONS[selectedPromotionIndex]
                        TvSoundManager.speakPieceSelection(context, promoType)
                    }
                    DPadDirection.RIGHT, DPadDirection.DOWN -> {
                        selectedPromotionIndex = (selectedPromotionIndex + 1) % count
                        val promoType = PieceType.PROMOTION_OPTIONS[selectedPromotionIndex]
                        TvSoundManager.speakPieceSelection(context, promoType)
                    }
                }
            }

            FocusZone.GAME_OVER_DIALOG -> {
                selectedGameOverAction = if (selectedGameOverAction == 0) 1 else 0
                TvSoundManager.playNavTick()
            }
        }
    }

    fun executeMenuAction(index: Int, onExitApp: () -> Unit = {}) {
        when (index) {
            0 -> {
                focusZone = FocusZone.PIECE_SELECTION
                TvSoundManager.speakMenuButton(context, 0, difficulty)
            }
            1 -> {
                undoLastTurn()
                focusZone = FocusZone.PIECE_SELECTION
            }
            2 -> {
                focusZone = FocusZone.PIECE_SELECTION
                requestHint()
            }
            3 -> {
                val nextDiff = difficulty.next()
                difficulty = nextDiff
                botMood = BotMood.IDLE_HAPPY
                botSpeechText = "Xin chào! Tớ là ${nextDiff.monsterName}!"
                autoSave()
                TvSoundManager.speakBotGreeting(context, nextDiff)
            }
            4 -> {
                startNewGame(playerColor, difficulty)
            }
            5 -> {
                startNewGame(playerColor.opposite(), difficulty)
            }
            6 -> {
                soundEnabled = !soundEnabled
                TvSoundManager.isSoundEnabled = soundEnabled
                BgmPlayer.setEnabled(soundEnabled)
                GameStorage.setMusicEnabled(context, soundEnabled)
                autoSave()
                if (soundEnabled) {
                    TvSoundManager.speakMenuButton(context, 6, difficulty)
                }
            }
            7 -> onExitApp()
        }
    }

    fun handleOkButton(onExitApp: () -> Unit = {}) {
        when (focusZone) {
            FocusZone.PIECE_SELECTION, FocusZone.MOVE_SELECTION -> handleBoardAction()
            FocusZone.SIDE_MENU -> executeMenuAction(selectedMenuIndex, onExitApp)
            FocusZone.PROMOTION_DIALOG -> {
                val chosenType = PieceType.PROMOTION_OPTIONS[selectedPromotionIndex]
                val move = pendingPromotionMoves.firstOrNull { it.promotionPiece == chosenType }
                    ?: pendingPromotionMoves.firstOrNull()
                if (move != null) {
                    commitPlayerMove(move)
                }
            }
            FocusZone.GAME_OVER_DIALOG -> {
                if (selectedGameOverAction == 0) {
                    startNewGame(playerColor, difficulty)
                } else {
                    hasDismissedGameOverDialog = true
                    focusZone = FocusZone.SIDE_MENU
                    selectedMenuIndex = 4
                    TvSoundManager.speakMenuButton(context, 4, difficulty)
                }
            }
        }
    }

    fun handleBackButton(onExitApp: () -> Unit = {}) {
        when (focusZone) {
            FocusZone.MOVE_SELECTION -> {
                selectedPiecePos?.let { cursorPos = it }
                selectedPiecePos = null
                hintMove = null
                focusZone = FocusZone.PIECE_SELECTION
                botMood = BotMood.IDLE_HAPPY
                botSpeechText = "Đã hủy chọn! Đậu chọn quân khác nhé!"
                TvSoundManager.playNavTick()
            }
            FocusZone.PIECE_SELECTION -> {
                val isPhoneOrTablet = detectDeviceProfile(context, context.resources.configuration.smallestScreenWidthDp) != DeviceProfile.TV
                if (isPhoneOrTablet) {
                    onExitApp()
                } else {
                    focusZone = FocusZone.SIDE_MENU
                    TvSoundManager.playSelect()
                    TvSoundManager.speakMenuButton(context, selectedMenuIndex, difficulty)
                }
            }
            FocusZone.SIDE_MENU -> {
                focusZone = FocusZone.PIECE_SELECTION
                TvSoundManager.speakMenuButton(context, 0, difficulty)
            }
            FocusZone.PROMOTION_DIALOG -> {
                pendingPromotionMoves = emptyList()
                focusZone = FocusZone.MOVE_SELECTION
                TvSoundManager.playNavTick()
            }
            FocusZone.GAME_OVER_DIALOG -> {
                hasDismissedGameOverDialog = true
                focusZone = FocusZone.SIDE_MENU
                selectedMenuIndex = 4
                TvSoundManager.playNavTick()
            }
        }
    }

    fun toggle3DViewMode() {
        is3DViewMode = !is3DViewMode
        val modeName = if (is3DViewMode) "3D Bàn Cờ Hoàng Gia" else "2D Trực diện"
        botSpeechText = "Đã chuyển sang chế độ $modeName!"
        TvSoundManager.playSelect()
    }

    fun toggleNavMode() {
        navMode = navMode.next()
        botSpeechText = "Chế độ: ${navMode.vietnameseTitle} ${navMode.badgeEmoji}"
        TvSoundManager.playSelect()
    }

    fun speakCurrentPieceInfo() {
        val p = gameState.pieceAt(cursorPos)
        if (p != null) {
            TvSoundManager.speakPieceSelection(context, p.type)
        }
    }

    fun toggleMenu() {
        if (focusZone == FocusZone.SIDE_MENU) {
            focusZone = FocusZone.PIECE_SELECTION
        } else {
            focusZone = FocusZone.SIDE_MENU
            selectedMenuIndex = 0
        }
        TvSoundManager.playSelect()
    }

    // =========================================================================
    // SỰ KIỆN TƯƠNG TÁC TỪ UI & INPUT DEVICES
    // =========================================================================

    fun onSquareClick(clickedPos: Position) {
        if (!isPlayerTurn || isAiThinking) return
        val currentMovable = movablePiecePositions
        val currentLegalForSelected = movesForSelectedPiece

        if (focusZone == FocusZone.MOVE_SELECTION) {
            if (clickedPos == selectedPiecePos) {
                cursorPos = clickedPos
                handleBoardAction()
                return
            }
            val movesToClicked = currentLegalForSelected.filter { it.to == clickedPos }
            if (movesToClicked.isNotEmpty()) {
                cursorPos = clickedPos
                handleBoardAction()
                return
            }
            if (clickedPos in currentMovable) {
                cursorPos = clickedPos
                handleBoardAction()
                return
            }
            cursorPos = clickedPos
            handleBoardAction()
            return
        }

        if (clickedPos in currentMovable) {
            cursorPos = clickedPos
            focusZone = FocusZone.PIECE_SELECTION
            handleBoardAction()
        }
    }

    fun onMenuItemClick(index: Int, onExitApp: () -> Unit = {}) {
        selectedMenuIndex = index
        executeMenuAction(index, onExitApp)
    }

    fun onPromotionSelect(index: Int) {
        selectedPromotionIndex = index
        handleOkButton()
    }

    fun onGameOverAction(actionIndex: Int) {
        selectedGameOverAction = actionIndex
        handleOkButton()
    }

    fun onGamepadAction(action: GamepadAction, onExitApp: () -> Unit = {}) {
        Log.d("TVCHESS_GAMEPLAY", "GamepadAction received in ViewModel: $action")
        when (action) {
            is GamepadAction.Navigate -> handleDirection(action.direction, forceFreeGrid = action.forceFreeGrid)
            is GamepadAction.PrimaryConfirmA -> handleOkButton(onExitApp)
            is GamepadAction.SecondaryCancelB -> handleBackButton(onExitApp)
            is GamepadAction.QuickHintX -> requestHint()
            is GamepadAction.QuickUndoY -> undoLastTurn()
            is GamepadAction.ToggleNavModeLB -> toggleNavMode()
            is GamepadAction.CyclePieceRB -> cycleNextPiece()
            is GamepadAction.SpeakPieceInfoTrigger -> speakCurrentPieceInfo()
            is GamepadAction.ToggleMenuStart -> toggleMenu()
            is GamepadAction.SwitchMonsterSelect -> toggle3DViewMode()
        }
    }

    fun handleKeyEvent(key: Key, onExitApp: () -> Unit = {}): Boolean {
        return when (key) {
            Key.DirectionUp, Key.W -> {
                handleDirection(DPadDirection.UP)
                true
            }
            Key.DirectionDown, Key.S -> {
                handleDirection(DPadDirection.DOWN)
                true
            }
            Key.DirectionLeft, Key.A -> {
                handleDirection(DPadDirection.LEFT)
                true
            }
            Key.DirectionRight, Key.D -> {
                handleDirection(DPadDirection.RIGHT)
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.ButtonA -> {
                handleOkButton(onExitApp)
                true
            }
            Key.Back, Key.Escape, Key.ButtonB, Key.Menu -> {
                handleBackButton(onExitApp)
                true
            }
            Key.ButtonX, Key.H -> {
                requestHint()
                true
            }
            Key.ButtonY, Key.U -> {
                undoLastTurn()
                true
            }
            Key.ButtonL1 -> {
                toggleNavMode()
                true
            }
            Key.ButtonR1, Key.Tab -> {
                cycleNextPiece()
                true
            }
            Key.ButtonStart -> {
                toggleMenu()
                true
            }
            Key.ButtonSelect -> {
                executeMenuAction(3, onExitApp)
                true
            }
            Key.V, Key.Three -> {
                toggle3DViewMode()
                true
            }
            else -> false
        }
    }

    // =========================================================================
    // QUẢN LÝ VÒNG ĐỜI (LIFECYCLE) - TRIỆT TIÊU HOÀN TOÀN TÁC VỤ CHẠY NGẦM
    // =========================================================================

    fun onAppBackgrounded() {
        idleJob?.cancel()
        TvSoundManager.stopAll()
    }

    fun onAppForegrounded() {
        if (isPlayerTurn && !isAiThinking) {
            restartIdleTimer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        aiJob?.cancel()
        idleJob?.cancel()
        TvSoundManager.stopAll()
    }
}
