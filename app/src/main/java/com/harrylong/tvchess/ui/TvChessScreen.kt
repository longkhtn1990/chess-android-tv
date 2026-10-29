package com.harrylong.tvchess.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harrylong.tvchess.ui.game.GameViewModel
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harrylong.tvchess.R
import com.harrylong.tvchess.audio.AcousticSfxSynthesizer
import com.harrylong.tvchess.audio.TvSoundManager
import com.harrylong.tvchess.engine.ChessAI
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.input.DareuGamepadController
import com.harrylong.tvchess.input.GamepadAction
import com.harrylong.tvchess.input.GamepadEventBus
import com.harrylong.tvchess.model.*
import com.harrylong.tvchess.storage.GameStorage
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawAnimatedMovingPiece
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawCaptureStarburst
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawCaptureStarburst3D
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawMoveGuideArrow
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawMoveGuideArrow3D
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawRichWoodSquare
import com.harrylong.tvchess.ui.BoardAnimationEffects.drawVictoryCelebrationParticles
import com.harrylong.tvchess.ui.ChessPieceRenderer.drawChessPiece
import com.harrylong.tvchess.ui.MonsterMascotRenderer.drawMonsterMascot
import com.harrylong.tvchess.ui.IsometricBoard3DRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



// Bảng màu Tươi Sáng & Ấm Áp thân thiện với Trẻ Em trên màn hình TV 4K 16:9
private val BgDarkPrimary = Color(0xFF0F172A)
private val BgDarkSecondary = Color(0xFF1E293B)
private val CardSurface = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val GoldAccent = Color(0xFFFACC15)
private val GoldDeep = Color(0xFFEAB308)
private val CyanFocus = Color(0xFF38BDF8)
private val EmeraldValid = Color(0xFF10B981)
private val CrimsonDanger = Color(0xFFEF4444)

// Màu khung bàn cờ gỗ ấm rõ nét
private val WoodFrameOuter = Color(0xFF432818)

@Composable
fun TvChessScreen(
    viewModel: GameViewModel = viewModel(),
    onExitApp: () -> Unit = {}
) {
    val rootFocusRequester = remember { FocusRequester() }

    // Quản lý Animation trượt quân và âm thanh Whoosh khi có nước đi mới
    var animatingMove by remember { mutableStateOf<ChessMove?>(null) }
    val moveAnim = remember { Animatable(1f) }

    // Animation bung sao và pháo giấy Starburst khi ăn quân
    var captureBurstPos by remember { mutableStateOf<Position?>(null) }
    val captureBurstAnim = remember { Animatable(1f) }

    // Animation rung lắc nhẹ bàn cờ khi Chiếu tướng
    val checkShakeAnim = remember { Animatable(0f) }

    LaunchedEffect(viewModel.gameState.lastMove) {
        val lastMove = viewModel.gameState.lastMove ?: return@LaunchedEffect
        animatingMove = lastMove
        TvSoundManager.playSlideWhoosh()
        moveAnim.snapTo(0f)
        moveAnim.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(viewModel.gameState.lastMove) {
        val lastMove = viewModel.gameState.lastMove ?: return@LaunchedEffect
        if (lastMove.capturedPiece != null || lastMove.isEnPassant) {
            captureBurstPos = lastMove.to
            captureBurstAnim.snapTo(0f)
            captureBurstAnim.animateTo(1f, tween(680, easing = LinearOutSlowInEasing))
        }
    }

    LaunchedEffect(viewModel.gameState.status, viewModel.gameState.moveHistory.size) {
        if (viewModel.gameState.status == GameStatus.CHECK || viewModel.gameState.status == GameStatus.CHECKMATE) {
            checkShakeAnim.snapTo(0f)
            checkShakeAnim.animateTo(6f, tween(50, easing = FastOutLinearInEasing))
            checkShakeAnim.animateTo(-6f, tween(50, easing = LinearEasing))
            checkShakeAnim.animateTo(4f, tween(50, easing = LinearEasing))
            checkShakeAnim.animateTo(-4f, tween(50, easing = LinearEasing))
            checkShakeAnim.animateTo(0f, tween(60, easing = LinearOutSlowInEasing))
        }
    }

    // Lắng nghe sự kiện từ GamepadEventBus
    val currentOnGamepadAction by rememberUpdatedState { action: GamepadAction ->
        viewModel.onGamepadAction(action, onExitApp)
    }

    LaunchedEffect(Unit) {
        GamepadEventBus.actions.collect { action ->
            currentOnGamepadAction(action)
        }
    }

    val context = LocalContext.current
    val deviceProfile = LocalDeviceProfile.current
    val haptic = LocalHapticFeedback.current
    val isGamepadConnected by GamepadEventBus.isGamepadConnected.collectAsState()
    val showGamepadHud = deviceProfile == DeviceProfile.TV || isGamepadConnected

    // Giữ màn hình sáng CHỈ KHI đang ở trong ván cờ (GAME screen)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Quản lý Lifecycle - Ngừng ngay các tác vụ đếm giờ / phát âm thanh ngầm khi Activity bị che hoặc chuyển vào nền
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP,
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onAppBackgrounded()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    viewModel.onAppForegrounded()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Rung phản hồi (Haptic feedback) xúc giác khi chọn quân, ăn quân hoặc bị chiếu
    LaunchedEffect(viewModel.selectedPiecePos) {
        if (viewModel.selectedPiecePos != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(viewModel.gameState.lastMove) {
        val lastMove = viewModel.gameState.lastMove ?: return@LaunchedEffect
        if (lastMove.capturedPiece != null || lastMove.isEnPassant) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(viewModel.gameState.status) {
        if (viewModel.gameState.status == GameStatus.CHECK || viewModel.gameState.status == GameStatus.CHECKMATE) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    BackHandler(enabled = true) {
        if (viewModel.focusZone == FocusZone.MOVE_SELECTION) {
            viewModel.handleBackButton(onExitApp)
        } else if (viewModel.focusZone == FocusZone.SIDE_MENU) {
            viewModel.handleBackButton(onExitApp)
        } else if (viewModel.focusZone == FocusZone.PROMOTION_DIALOG || viewModel.focusZone == FocusZone.GAME_OVER_DIALOG) {
            viewModel.handleBackButton(onExitApp)
        } else {
            // Ở màn hình bàn cờ chính: trên TV thì mở Side Menu, trên Điện thoại / Tablet thì quay lại Trang chủ
            if (deviceProfile == DeviceProfile.TV) {
                viewModel.handleBackButton(onExitApp)
            } else {
                onExitApp()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BgDarkSecondary, BgDarkPrimary),
                    radius = 1500f
                )
            )
            .safeDrawingPadding()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                viewModel.handleKeyEvent(event.key, onExitApp)
            }
    ) {
        val isPortrait = maxHeight > maxWidth
        val isPhonePortrait = (deviceProfile == DeviceProfile.PHONE || deviceProfile == DeviceProfile.TABLET) && isPortrait

        when {
            deviceProfile == DeviceProfile.TV -> {
                TvGameLayout(
                    viewModel = viewModel,
                    animatingMove = animatingMove,
                    moveAnimProgress = moveAnim.value,
                    captureBurstPos = captureBurstPos,
                    captureBurstProgress = captureBurstAnim.value,
                    checkShakeOffset = checkShakeAnim.value,
                    showGamepadHud = showGamepadHud,
                    onExitApp = onExitApp
                )
            }
            isPhonePortrait -> {
                PhonePortraitGameLayout(
                    viewModel = viewModel,
                    animatingMove = animatingMove,
                    moveAnimProgress = moveAnim.value,
                    captureBurstPos = captureBurstPos,
                    captureBurstProgress = captureBurstAnim.value,
                    checkShakeOffset = checkShakeAnim.value,
                    showGamepadHud = showGamepadHud,
                    onExitApp = onExitApp
                )
            }
            else -> {
                LandscapeTabletGameLayout(
                    viewModel = viewModel,
                    animatingMove = animatingMove,
                    moveAnimProgress = moveAnim.value,
                    captureBurstPos = captureBurstPos,
                    captureBurstProgress = captureBurstAnim.value,
                    checkShakeOffset = checkShakeAnim.value,
                    showGamepadHud = showGamepadHud,
                    onExitApp = onExitApp
                )
            }
        }

        // ================= HỘP THOẠI MENU TRÊN ĐIỆN THOẠI / TABLET (MODAL OVERLAY) =================
        if (viewModel.focusZone == FocusZone.SIDE_MENU && deviceProfile != DeviceProfile.TV) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { viewModel.toggleMenu() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 520.dp)
                        .clickable(enabled = false) {}
                ) {
                    KidsVisualControlPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        focusZone = viewModel.focusZone,
                        selectedMenuIndex = viewModel.selectedMenuIndex,
                        difficulty = viewModel.difficulty,
                        playerColor = viewModel.playerColor,
                        soundEnabled = viewModel.soundEnabled,
                        canUndo = viewModel.canUndo,
                        isLandscapeCompact = true,
                        onMenuItemClick = { index ->
                            viewModel.onMenuItemClick(index, onExitApp)
                        }
                    )
                }
            }
        }

        // ================= HỘP THOẠI PHONG CẤP TỐT (PROMOTION) =================
        if (viewModel.focusZone == FocusZone.PROMOTION_DIALOG) {
            PromotionDialogOverlay(
                playerColor = viewModel.playerColor,
                selectedIndex = viewModel.selectedPromotionIndex,
                onSelectPiece = { index ->
                    viewModel.onPromotionSelect(index)
                }
            )
        }

        // ================= HỘP THOẠI KẾT THÚC VÁN CỜ (GAME OVER) =================
        if (viewModel.focusZone == FocusZone.GAME_OVER_DIALOG) {
            GameOverDialogOverlay(
                gameState = viewModel.gameState,
                playerColor = viewModel.playerColor,
                difficulty = viewModel.difficulty,
                selectedAction = viewModel.selectedGameOverAction,
                onSelectAction = { actionIndex ->
                    viewModel.onGameOverAction(actionIndex)
                }
            )
        }
    }
}

/**
 * Layout bàn cờ trên Android TV 16:9 – Giữ nguyên trải nghiệm nguyên bản không đổi.
 */
@Composable
private fun TvGameLayout(
    viewModel: GameViewModel,
    animatingMove: ChessMove?,
    moveAnimProgress: Float,
    captureBurstPos: Position?,
    captureBurstProgress: Float,
    checkShakeOffset: Float,
    showGamepadHud: Boolean,
    onExitApp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ================= CỘT TRÁI: SÂN KHẤU QUÁI THÚ HOẠT HÌNH SỐNG ĐỘNG =================
        MonsterStagePanel(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight(),
            gameState = viewModel.gameState,
            playerColor = viewModel.playerColor,
            difficulty = viewModel.difficulty,
            botMood = viewModel.botMood,
            botSpeechText = viewModel.botSpeechText,
            isAiThinking = viewModel.isAiThinking
        )

        // ================= CỘT GIỮA: BÀN CỜ HOÀNG GIA 4K & THANH TAY CẦM HUD =================
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .offset(x = checkShakeOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                ChessBoardView(
                    gameState = viewModel.gameState,
                    difficulty = viewModel.difficulty,
                    isBoardFlipped = viewModel.isBoardFlipped,
                    is3DViewMode = viewModel.is3DViewMode,
                    focusZone = viewModel.focusZone,
                    cursorPos = viewModel.cursorPos,
                    selectedPiecePos = viewModel.selectedPiecePos,
                    movablePiecePositions = viewModel.movablePiecePositions,
                    validTargetMoves = viewModel.movesForSelectedPiece,
                    previewMoves = viewModel.previewMovesForCursorPiece,
                    hintMove = viewModel.hintMove,
                    animatingMove = animatingMove,
                    moveAnimProgress = moveAnimProgress,
                    captureBurstPos = captureBurstPos,
                    captureBurstProgress = captureBurstProgress,
                    onSquareClick = { clickedPos ->
                        viewModel.onSquareClick(clickedPos)
                    }
                )
            }

            // Thanh HUD phím tắt tay cầm Dareu H101X và chế độ điều hướng
            if (showGamepadHud) {
                DareuGamepadBar(
                    navMode = viewModel.navMode,
                    is3DViewMode = viewModel.is3DViewMode,
                    onToggle3DView = {
                        viewModel.toggle3DViewMode()
                    }
                )
            }
        }

        // ================= CỘT PHẢI: LỊCH SỬ NƯỚC CỜ & CỤM 8 NÚT BẤM THU GỌN =================
        Column(
            modifier = Modifier
                .weight(0.26f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MoveHistoryPanel(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxWidth(),
                gameState = viewModel.gameState
            )

            KidsVisualControlPanel(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxWidth(),
                focusZone = viewModel.focusZone,
                selectedMenuIndex = viewModel.selectedMenuIndex,
                difficulty = viewModel.difficulty,
                playerColor = viewModel.playerColor,
                soundEnabled = viewModel.soundEnabled,
                canUndo = viewModel.canUndo,
                onMenuItemClick = { index ->
                    viewModel.onMenuItemClick(index, onExitApp)
                }
            )
        }
    }
}

/**
 * Layout cho điện thoại chiều dọc: Thanh Quái Thú gọn ở trên -> Bàn cờ vuông full width -> Thanh hành động ở dưới.
 */
@Composable
private fun PhonePortraitGameLayout(
    viewModel: GameViewModel,
    animatingMove: ChessMove?,
    moveAnimProgress: Float,
    captureBurstPos: Position?,
    captureBurstProgress: Float,
    checkShakeOffset: Float,
    showGamepadHud: Boolean,
    onExitApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. THANH QUÁI THÚ GỌN Ở TRÊN CÙNG
        CompactMonsterTopBar(
            difficulty = viewModel.difficulty,
            botMood = viewModel.botMood,
            botSpeechText = viewModel.botSpeechText,
            isAiThinking = viewModel.isAiThinking,
            gameState = viewModel.gameState,
            playerColor = viewModel.playerColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 2. BÀN CỜ VUÔNG FULL CHIỀU RỘNG
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset(x = checkShakeOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            ChessBoardView(
                gameState = viewModel.gameState,
                difficulty = viewModel.difficulty,
                isBoardFlipped = viewModel.isBoardFlipped,
                is3DViewMode = viewModel.is3DViewMode,
                focusZone = viewModel.focusZone,
                cursorPos = viewModel.cursorPos,
                selectedPiecePos = viewModel.selectedPiecePos,
                movablePiecePositions = viewModel.movablePiecePositions,
                validTargetMoves = viewModel.movesForSelectedPiece,
                previewMoves = viewModel.previewMovesForCursorPiece,
                hintMove = viewModel.hintMove,
                animatingMove = animatingMove,
                moveAnimProgress = moveAnimProgress,
                captureBurstPos = captureBurstPos,
                captureBurstProgress = captureBurstProgress,
                onSquareClick = { clickedPos ->
                    viewModel.onSquareClick(clickedPos)
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. NẾU CÓ TAY CẦM KẾT NỐI: HIỆN HUD PHÍM TẮT
        if (showGamepadHud) {
            DareuGamepadBar(
                navMode = viewModel.navMode,
                is3DViewMode = viewModel.is3DViewMode,
                onToggle3DView = { viewModel.toggle3DViewMode() },
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // 4. THANH HÀNH ĐỘNG CẢM ỨNG Ở DƯỚI CÙNG (GỢI Ý, ĐI LẠI, 2D/3D, MENU)
        PhoneActionBar(
            canUndo = viewModel.canUndo,
            is3DViewMode = viewModel.is3DViewMode,
            onHint = { viewModel.requestHint() },
            onUndo = { viewModel.undoLastTurn() },
            onToggle3D = { viewModel.toggle3DViewMode() },
            onMenu = { viewModel.toggleMenu() }
        )
    }
}

/**
 * Layout cho Điện thoại chiều ngang & Máy tính bảng: Bàn cờ bên trái, Bảng thông tin bên phải.
 */
@Composable
private fun LandscapeTabletGameLayout(
    viewModel: GameViewModel,
    animatingMove: ChessMove?,
    moveAnimProgress: Float,
    captureBurstPos: Position?,
    captureBurstProgress: Float,
    checkShakeOffset: Float,
    showGamepadHud: Boolean,
    onExitApp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CỘT TRÁI: BÀN CỜ
        Column(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .offset(x = checkShakeOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                ChessBoardView(
                    gameState = viewModel.gameState,
                    difficulty = viewModel.difficulty,
                    isBoardFlipped = viewModel.isBoardFlipped,
                    is3DViewMode = viewModel.is3DViewMode,
                    focusZone = viewModel.focusZone,
                    cursorPos = viewModel.cursorPos,
                    selectedPiecePos = viewModel.selectedPiecePos,
                    movablePiecePositions = viewModel.movablePiecePositions,
                    validTargetMoves = viewModel.movesForSelectedPiece,
                    previewMoves = viewModel.previewMovesForCursorPiece,
                    hintMove = viewModel.hintMove,
                    animatingMove = animatingMove,
                    moveAnimProgress = moveAnimProgress,
                    captureBurstPos = captureBurstPos,
                    captureBurstProgress = captureBurstProgress,
                    onSquareClick = { clickedPos ->
                        viewModel.onSquareClick(clickedPos)
                    }
                )
            }

            if (showGamepadHud) {
                Spacer(modifier = Modifier.height(4.dp))
                DareuGamepadBar(
                    navMode = viewModel.navMode,
                    is3DViewMode = viewModel.is3DViewMode,
                    onToggle3DView = { viewModel.toggle3DViewMode() }
                )
            }
        }

        // CỘT PHẢI: BẢNG THÔNG TIN QUÁI THÚ + LỊCH SỬ NƯỚC CỜ + THANH NÚT BẤM CẢM ỨNG
        Column(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CompactMonsterTopBar(
                difficulty = viewModel.difficulty,
                botMood = viewModel.botMood,
                botSpeechText = viewModel.botSpeechText,
                isAiThinking = viewModel.isAiThinking,
                gameState = viewModel.gameState,
                playerColor = viewModel.playerColor
            )

            MoveHistoryPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                gameState = viewModel.gameState
            )

            PhoneActionBar(
                canUndo = viewModel.canUndo,
                is3DViewMode = viewModel.is3DViewMode,
                onHint = { viewModel.requestHint() },
                onUndo = { viewModel.undoLastTurn() },
                onToggle3D = { viewModel.toggle3DViewMode() },
                onMenu = { viewModel.toggleMenu() }
            )
        }
    }
}

/**
 * Thanh thông tin Quái Thú thu gọn trên điện thoại kèm quân cờ ăn được.
 */
@Composable
private fun CompactMonsterTopBar(
    difficulty: Difficulty,
    botMood: BotMood,
    botSpeechText: String,
    isAiThinking: Boolean,
    gameState: GameState,
    playerColor: PieceColor,
    modifier: Modifier = Modifier
) {
    val aiColor = playerColor.opposite()
    val aiCaptured = if (aiColor == PieceColor.WHITE) gameState.capturedByWhite else gameState.capturedByBlack
    val playerCaptured = if (playerColor == PieceColor.WHITE) gameState.capturedByWhite else gameState.capturedByBlack
    val isKidTurn = !gameState.status.isGameOver && gameState.turn == playerColor

    val mascotResId = when (difficulty) {
        Difficulty.EASY -> R.drawable.mascot_dino
        Difficulty.MEDIUM -> R.drawable.mascot_kingkong
        Difficulty.HARD -> R.drawable.mascot_godzilla
    }

    val stageBorderColor = when (difficulty) {
        Difficulty.EASY -> Color(0xFF34D399)
        Difficulty.MEDIUM -> Color(0xFFF59E0B)
        Difficulty.HARD -> Color(0xFF38BDF8)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.5.dp, if (isKidTurn) CardBorder else stageBorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(2.dp, stageBorderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = mascotResId),
                contentDescription = difficulty.monsterName,
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Fit
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = difficulty.monsterName,
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "⭐".repeat(difficulty.starCount), fontSize = 10.sp)
                Text(
                    text = if (isAiThinking) "🤔 Đang nghĩ..." else if (isKidTurn) "👉 Lượt bé Đậu" else "Lượt quái thú",
                    color = if (isKidTurn) EmeraldValid else CyanFocus,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = botSpeechText,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (aiCaptured.isNotEmpty() || playerCaptured.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (aiCaptured.isNotEmpty()) {
                            Text(
                                text = aiCaptured.take(5).joinToString("") { it.unicodeSymbol },
                                color = CrimsonDanger,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thanh nút bấm điều khiển cảm ứng nhanh trên điện thoại với kích thước chạm >= 48dp.
 */
@Composable
private fun PhoneActionBar(
    canUndo: Boolean,
    is3DViewMode: Boolean,
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onToggle3D: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.2.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButtonTouch(
            icon = "💡",
            label = "Gợi ý",
            accentColor = GoldAccent,
            enabled = true,
            onClick = onHint,
            modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
        )

        ActionButtonTouch(
            icon = "↩️",
            label = "Đi lại",
            accentColor = if (canUndo) Color(0xFFF97316) else Color(0xFF64748B),
            enabled = canUndo,
            onClick = onUndo,
            modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
        )

        ActionButtonTouch(
            icon = if (is3DViewMode) "🎲" else "📐",
            label = if (is3DViewMode) "3D" else "2D",
            accentColor = CyanFocus,
            enabled = true,
            onClick = onToggle3D,
            modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
        )

        ActionButtonTouch(
            icon = "📋",
            label = "Menu",
            accentColor = EmeraldValid,
            enabled = true,
            onClick = onMenu,
            modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
        )
    }
}

@Composable
private fun ActionButtonTouch(
    icon: String,
    label: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.75f))
            .border(1.dp, if (enabled) accentColor.copy(alpha = 0.55f) else Color(0xFF334155), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = label,
                color = if (enabled) Color.White else Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Thanh gợi ý phím tắt tay cầm Dareu H101X & Remote Xiaomi gọn gàng ở chân bàn cờ.
 */
@Composable
fun DareuGamepadBar(
    modifier: Modifier = Modifier,
    navMode: BoardNavigationMode,
    is3DViewMode: Boolean = true,
    onToggle3DView: () -> Unit = {}
) {
    val hints = remember { DareuGamepadController.getDareuShortcutHints() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.88f))
            .border(1.2.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            hints.take(3).forEach { hint ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(hint.colorHex))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = hint.buttonLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = hint.actionTitle,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Nút Chuyển đổi Bàn cờ 3D Tabletop Isometric ↔ 2D Trực diện
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (is3DViewMode) Color(0xFF0284C7) else Color(0xFF334155))
                    .border(1.2.dp, if (is3DViewMode) CyanFocus else Color(0xFF64748B), RoundedCornerShape(8.dp))
                    .clickable { onToggle3DView() }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (is3DViewMode) "🎲 3D (V)" else "📐 2D (V)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Chế độ điều hướng hiện tại (Nhấn LB để đổi)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF8B5CF6))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LB",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "${navMode.badgeEmoji} ${navMode.vietnameseTitle}",
                    color = GoldAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MonsterStagePanel(
    modifier: Modifier,
    gameState: GameState,
    playerColor: PieceColor,
    difficulty: Difficulty,
    botMood: BotMood,
    botSpeechText: String,
    isAiThinking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "monsterAnim")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breathPhase"
    )
    val actionPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "actionPhase"
    )
    val blinkTimer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blinkTimer"
    )
    val isBlinking = blinkTimer > 0.92f

    val aiColor = playerColor.opposite()
    val aiCaptured = if (aiColor == PieceColor.WHITE) gameState.capturedByWhite else gameState.capturedByBlack
    val playerCaptured = if (playerColor == PieceColor.WHITE) gameState.capturedByWhite else gameState.capturedByBlack

    val stageBorderColor = when (difficulty) {
        Difficulty.EASY -> Color(0xFF34D399)
        Difficulty.MEDIUM -> Color(0xFFF59E0B)
        Difficulty.HARD -> Color(0xFF38BDF8)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Sân khấu lớn của Quái Thú Đối Thủ
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .border(2.5.dp, stageBorderColor, RoundedCornerShape(20.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Thanh tên Quái Thú + Số sao cấp độ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = difficulty.monsterName.uppercase(),
                    color = stageBorderColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "⭐".repeat(difficulty.starCount),
                    fontSize = 14.sp
                )
            }

            // Bong bóng cảm thán vui nhộn
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(2.dp, GoldAccent, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = botMood.bubbleEmoji,
                        fontSize = 24.sp
                    )
                    Text(
                        text = botSpeechText,
                        color = Color(0xFF0F172A),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }

            // Nhân vật Hoạt Hình Chibi chuyển động liên tục
            val mascotResId = when (difficulty) {
                Difficulty.EASY -> R.drawable.mascot_dino
                Difficulty.MEDIUM -> R.drawable.mascot_kingkong
                Difficulty.HARD -> R.drawable.mascot_godzilla
            }
            val mascotBitmap = ImageBitmap.imageResource(id = mascotResId)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawMonsterMascot(
                        difficulty = difficulty,
                        mood = if (isAiThinking && botMood != BotMood.CRYING) BotMood.THINKING else botMood,
                        breathPhase = breathPhase,
                        actionPhase = actionPhase,
                        isBlinking = isBlinking,
                        customBitmap = mascotBitmap
                    )
                }
            }

            // Khay quân cờ mà Quái Thú đã ăn được
            VisualTrophyRow(
                labelEmoji = when (difficulty) {
                    Difficulty.EASY -> "🦖"
                    Difficulty.MEDIUM -> "🦍"
                    Difficulty.HARD -> "🐲"
                },
                capturedPieces = aiCaptured
            )
        }

        // 2. Thẻ của Bé Đậu
        val isKidTurn = !gameState.status.isGameOver && gameState.turn == playerColor
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isKidTurn) Color(0xFF1E3A2F) else CardSurface
                )
                .border(
                    width = if (isKidTurn) 3.dp else 1.5.dp,
                    color = if (isKidTurn) GoldAccent else CardBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (playerColor == PieceColor.WHITE) Color.White else Color(0xFF0F172A))
                            .border(2.5.dp, GoldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👑",
                            fontSize = 24.sp
                        )
                    }
                    Column {
                        Text(
                            text = "BÉ ĐẬU",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isKidTurn) "👉 Tới lượt Đậu đi cờ!" else "⏳ Đậu đợi mình tí nha!",
                            color = if (isKidTurn) GoldAccent else Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Hiển thị quân cờ đại diện màu phe của bé Đậu
                Canvas(modifier = Modifier.size(38.dp)) {
                    drawChessPiece(
                        piece = Piece(PieceType.KNIGHT, playerColor),
                        topLeft = Offset.Zero,
                        squareSize = size.width
                    )
                }
            }

            // Khay chiến lợi phẩm các quân cờ bé Đậu đã bắt được của Quái Thú
            VisualTrophyRow(
                labelEmoji = "🏆",
                capturedPieces = playerCaptured
            )
        }
    }
}

@Composable
private fun VisualTrophyRow(
    labelEmoji: String,
    capturedPieces: List<Piece>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.65f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = labelEmoji,
            fontSize = 16.sp
        )
        if (capturedPieces.isEmpty()) {
            Text(
                text = "• • •",
                color = Color(0xFF475569),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = capturedPieces.joinToString(separator = "") { it.unicodeSymbol },
                color = GoldAccent,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChessBoardView(
    gameState: GameState,
    difficulty: Difficulty,
    isBoardFlipped: Boolean,
    is3DViewMode: Boolean = true,
    focusZone: FocusZone,
    cursorPos: Position,
    selectedPiecePos: Position?,
    movablePiecePositions: List<Position>,
    validTargetMoves: List<ChessMove>,
    previewMoves: List<ChessMove>,
    hintMove: ChessMove?,
    animatingMove: ChessMove?,
    moveAnimProgress: Float,
    captureBurstPos: Position?,
    captureBurstProgress: Float,
    onSquareClick: (Position) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "cursorPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val kingInCheckPos = remember(gameState) {
        if (gameState.status == GameStatus.CHECK || gameState.status == GameStatus.CHECKMATE) {
            ChessEngine.findKingPosition(gameState.board, gameState.turn)
        } else null
    }

    val validTargetMap = remember(validTargetMoves) {
        validTargetMoves.associateBy { it.to }
    }
    val previewTargetSet = remember(previewMoves) {
        previewMoves.map { it.to }.toSet()
    }

    var draggedPiece by remember { mutableStateOf<Piece?>(null) }
    var draggedFromPos by remember { mutableStateOf<Position?>(null) }
    var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }

    val boardBoxModifier = if (is3DViewMode) {
        Modifier
            .aspectRatio(1f)
            .fillMaxHeight()
            .shadow(24.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F172A))
    } else {
        Modifier
            .aspectRatio(1f)
            .fillMaxHeight()
            .shadow(20.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(WoodFrameOuter)
            .border(3.5.dp, GoldDeep, RoundedCornerShape(20.dp))
            .padding(16.dp)
    }

    Box(
        modifier = boardBoxModifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (is3DViewMode) 24.dp else 10.dp))
                .pointerInput(is3DViewMode, isBoardFlipped, gameState, focusZone, selectedPiecePos, movablePiecePositions) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startOffset = down.position

                        fun findSquareAtOffset(offset: Offset): Position? {
                            return if (is3DViewMode) {
                                val layout = IsometricBoard3DRenderer.computePerspectiveLayout(size)
                                IsometricBoard3DRenderer.findSquareAtPoint(offset, layout, isBoardFlipped)
                            } else {
                                val sqSize = size.width / 8f
                                val col = (offset.x / sqSize).toInt()
                                val row = (offset.y / sqSize).toInt()
                                if (col in 0..7 && row in 0..7) {
                                    val bRow = if (isBoardFlipped) 7 - row else row
                                    val bCol = if (isBoardFlipped) 7 - col else col
                                    Position(bRow, bCol)
                                } else null
                            }
                        }

                        val startSquare = findSquareAtOffset(startOffset)
                        var isDragging = false
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (isDragging) {
                                    val endSquare = findSquareAtOffset(change.position)
                                    if (endSquare != null && draggedFromPos != null) {
                                        if (endSquare != draggedFromPos) {
                                            onSquareClick(endSquare)
                                        }
                                    }
                                    draggedPiece = null
                                    draggedFromPos = null
                                    dragCurrentOffset = null
                                } else {
                                    if (startSquare != null) {
                                        onSquareClick(startSquare)
                                    }
                                }
                                change.consume()
                                break
                            } else {
                                val dist = (change.position - startOffset).getDistance()
                                if (!isDragging && dist > touchSlop) {
                                    if (startSquare != null) {
                                        val p = gameState.pieceAt(startSquare)
                                        val isEligible = (startSquare in movablePiecePositions) || (startSquare == selectedPiecePos)
                                        if (isEligible && p != null && p.color == gameState.turn) {
                                            isDragging = true
                                            draggedPiece = p
                                            draggedFromPos = startSquare
                                            if (selectedPiecePos != startSquare) {
                                                onSquareClick(startSquare)
                                            }
                                        }
                                    }
                                }
                                if (isDragging) {
                                    dragCurrentOffset = change.position
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            if (is3DViewMode) {
                val layout = IsometricBoard3DRenderer.computePerspectiveLayout(size)

                // 1. VẼ TOÀN BỘ BÀN CỜ 3D TABLETOP ISOMETRIC
                val levelLabel = when (difficulty) {
                    Difficulty.EASY -> "BEGINNER LEVEL"
                    Difficulty.MEDIUM -> "INTERMEDIATE"
                    Difficulty.HARD -> "MASTER LEVEL"
                }
                val moveStatusLabel = "${gameState.fullMoveNumber}. ${if (gameState.turn == PieceColor.WHITE) "White's move" else "Black's move"}"

                with(IsometricBoard3DRenderer) {
                    drawIsometricTabletopBoard(
                        layout = layout,
                        board = gameState.board,
                        isBoardFlipped = isBoardFlipped,
                        selectedPos = selectedPiecePos,
                        validTargetMoves = validTargetMoves,
                        lastMove = gameState.lastMove,
                        hintMove = hintMove,
                        kingInCheckPos = kingInCheckPos,
                        pulseAlpha = pulseAlpha,
                        animatingMove = animatingMove,
                        animProgress = moveAnimProgress,
                        levelLabel = levelLabel,
                        moveStatusLabel = moveStatusLabel,
                        textMeasurer = textMeasurer
                    )
                }

                // 2. MŨI TÊN DẪN HƯỚNG TRỰC QUAN 3D
                if (focusZone == FocusZone.MOVE_SELECTION && selectedPiecePos != null) {
                    if (cursorPos != selectedPiecePos && validTargetMap.containsKey(cursorPos)) {
                        val fromSR = if (isBoardFlipped) 7 - selectedPiecePos.row else selectedPiecePos.row
                        val fromSC = if (isBoardFlipped) 7 - selectedPiecePos.col else selectedPiecePos.col
                        val toSR = if (isBoardFlipped) 7 - cursorPos.row else cursorPos.row
                        val toSC = if (isBoardFlipped) 7 - cursorPos.col else cursorPos.col
                        val qFrom = layout.getQuad(fromSR, fromSC)
                        val qTo = layout.getQuad(toSR, toSC)
                        val midDepth = (qFrom.depthScale + qTo.depthScale) * 0.5f
                        drawMoveGuideArrow3D(qFrom.center, qTo.center, midDepth, EmeraldValid, pulseAlpha)
                    }
                } else if (hintMove != null) {
                    val fromSR = if (isBoardFlipped) 7 - hintMove.from.row else hintMove.from.row
                    val fromSC = if (isBoardFlipped) 7 - hintMove.from.col else hintMove.from.col
                    val toSR = if (isBoardFlipped) 7 - hintMove.to.row else hintMove.to.row
                    val toSC = if (isBoardFlipped) 7 - hintMove.to.col else hintMove.to.col
                    val qFrom = layout.getQuad(fromSR, fromSC)
                    val qTo = layout.getQuad(toSR, toSC)
                    val midDepth = (qFrom.depthScale + qTo.depthScale) * 0.5f
                    drawMoveGuideArrow3D(qFrom.center, qTo.center, midDepth, CyanFocus, pulseAlpha)
                }

                // 3. HIỆU ỨNG TUNG SAO & PHÁO GIẤY KHI ĂN QUÂN TRONG KHÔNG GIAN 3D
                if (captureBurstPos != null && captureBurstProgress < 0.99f) {
                    val burstSR = if (isBoardFlipped) 7 - captureBurstPos.row else captureBurstPos.row
                    val burstSC = if (isBoardFlipped) 7 - captureBurstPos.col else captureBurstPos.col
                    val qBurst = layout.getQuad(burstSR, burstSC)
                    drawCaptureStarburst3D(qBurst.center, qBurst.depthScale, captureBurstProgress)
                }

                // 4. KHUNG CON TRỎ ĐIỀU HƯỚNG TV 4K TRÊN MẶT PHẲNG 3D
                if (focusZone == FocusZone.PIECE_SELECTION || focusZone == FocusZone.MOVE_SELECTION) {
                    val cursorSR = if (isBoardFlipped) 7 - cursorPos.row else cursorPos.row
                    val cursorSC = if (isBoardFlipped) 7 - cursorPos.col else cursorPos.col
                    val cursorQuad = layout.getQuad(cursorSR, cursorSC)

                    val cursorColor = if (focusZone == FocusZone.MOVE_SELECTION) {
                        when {
                            cursorPos == selectedPiecePos -> CrimsonDanger
                            validTargetMap.containsKey(cursorPos) -> EmeraldValid
                            else -> Color(0xFF94A3B8)
                        }
                    } else {
                        GoldAccent
                    }

                    drawTvCursorFrame3D(
                        quad = cursorQuad,
                        color = cursorColor,
                        pulseAlpha = pulseAlpha
                    )
                }
            } else {
                val sqSize = size.width / 8f

            // 1. VẼ CÁC Ô BÀN CỜ GỖ HOÀNG GIA 3D (BEVEL & WOOD GRAIN)
            for (screenRow in 0..7) {
                for (screenCol in 0..7) {
                    val boardRow = if (isBoardFlipped) 7 - screenRow else screenRow
                    val boardCol = if (isBoardFlipped) 7 - screenCol else screenCol
                    val pos = Position(boardRow, boardCol)
                    val topLeft = Offset(screenCol * sqSize, screenRow * sqSize)

                    val isLight = (boardRow + boardCol) % 2 == 0

                    // Sử dụng hàm drawRichWoodSquare tạo vân gỗ và viền vát 3D thật hơn
                    drawRichWoodSquare(topLeft, sqSize, isLight)

                    // Tô sáng ô nước đi trước
                    val lastMove = gameState.lastMove
                    if (lastMove != null && (pos == lastMove.from || pos == lastMove.to)) {
                        drawRect(
                            color = Color(0xFFFACC15).copy(alpha = 0.38f),
                            topLeft = topLeft,
                            size = Size(sqSize, sqSize)
                        )
                    }

                    // Tô sáng ô mách nước
                    if (hintMove != null && (pos == hintMove.from || pos == hintMove.to)) {
                        drawRect(
                            color = CyanFocus.copy(alpha = 0.45f * pulseAlpha),
                            topLeft = topLeft,
                            size = Size(sqSize, sqSize)
                        )
                    }

                    // Tô sáng ô quân đang chọn
                    if (pos == selectedPiecePos) {
                        drawRect(
                            color = EmeraldValid.copy(alpha = 0.45f),
                            topLeft = topLeft,
                            size = Size(sqSize, sqSize)
                        )
                    }

                    // Báo động đỏ ô Vua bị chiếu
                    if (pos == kingInCheckPos) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CrimsonDanger.copy(alpha = 0.85f),
                                    CrimsonDanger.copy(alpha = 0.25f)
                                ),
                                center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f),
                                radius = sqSize * 0.65f
                            ),
                            topLeft = topLeft,
                            size = Size(sqSize, sqSize)
                        )
                    }

                    // Vòng tròn gợi ý các quân cờ đi được
                    if (focusZone == FocusZone.PIECE_SELECTION && pos in movablePiecePositions && pos != cursorPos) {
                        drawCircle(
                            color = GoldAccent.copy(alpha = 0.38f),
                            radius = sqSize * 0.42f,
                            center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f),
                            style = Stroke(width = sqSize * 0.04f)
                        )
                    }

                    // 2. VẼ QUÂN CỜ TĨNH
                    // Nếu quân cờ đang trong animation bay/trượt tới ô đích, tạm ẩn quân cờ tĩnh tại ô đích
                    val isMovingTarget = (animatingMove != null && moveAnimProgress < 0.99f && pos == animatingMove.to)
                    val isCastleRookTarget = if (animatingMove != null && moveAnimProgress < 0.99f && (animatingMove.isKingsideCastle || animatingMove.isQueensideCastle)) {
                        val rookRank = animatingMove.from.row
                        val rookToCol = if (animatingMove.isKingsideCastle) 5 else 3
                        pos.row == rookRank && pos.col == rookToCol
                    } else false

                    val isBeingDragged = (pos == draggedFromPos && draggedPiece != null)
                    if (!isMovingTarget && !isCastleRookTarget && !isBeingDragged) {
                        val piece = gameState.board[boardRow][boardCol]
                        if (piece != null) {
                            drawChessPiece(
                                piece = piece,
                                topLeft = topLeft,
                                squareSize = sqSize,
                                isSelected = (pos == selectedPiecePos)
                            )
                        }
                    }

                    // Điểm chấm xem trước nước đi khi đang trỏ vào quân cờ
                    if (focusZone == FocusZone.PIECE_SELECTION && pos in previewTargetSet) {
                        val isCapturePreview = gameState.pieceAt(pos) != null
                        if (isCapturePreview) {
                            drawCircle(
                                color = GoldAccent.copy(alpha = 0.48f),
                                radius = sqSize * 0.42f,
                                center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f),
                                style = Stroke(width = sqSize * 0.055f)
                            )
                        } else {
                            drawCircle(
                                color = GoldAccent.copy(alpha = 0.45f),
                                radius = sqSize * 0.14f,
                                center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f)
                            )
                        }
                    }

                    // Mục tiêu hợp lệ của quân đang chọn
                    val validMove = validTargetMap[pos]
                    if (focusZone == FocusZone.MOVE_SELECTION && validMove != null) {
                        val isCapture = validMove.capturedPiece != null || validMove.isEnPassant
                        val isCastle = validMove.isKingsideCastle || validMove.isQueensideCastle
                        val center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f)
                        if (isCastle) {
                            // Ô NHẬP THÀNH: Viền kép màu vàng hoàng gia & biểu tượng khiên bảo vệ 🛡️
                            drawCircle(
                                color = GoldAccent.copy(alpha = 0.95f),
                                radius = sqSize * 0.38f,
                                center = center,
                                style = Stroke(width = sqSize * 0.06f)
                            )
                            drawCircle(
                                color = CyanFocus.copy(alpha = 0.30f),
                                radius = sqSize * 0.28f,
                                center = center
                            )
                            val shieldStyle = TextStyle(
                                fontSize = (sqSize * 0.30f).toSp(),
                                textAlign = TextAlign.Center
                            )
                            val shieldLayout = textMeasurer.measure("🛡️", shieldStyle)
                            drawText(
                                textLayoutResult = shieldLayout,
                                topLeft = Offset(center.x - shieldLayout.size.width / 2f, center.y - shieldLayout.size.height / 2f)
                            )
                        } else if (isCapture) {
                            drawCircle(
                                color = CrimsonDanger.copy(alpha = 0.88f),
                                radius = sqSize * 0.43f,
                                center = center,
                                style = Stroke(width = sqSize * 0.08f)
                            )
                        } else {
                            drawCircle(
                                color = EmeraldValid.copy(alpha = 0.92f),
                                radius = sqSize * 0.18f,
                                center = center
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.88f),
                                radius = sqSize * 0.18f,
                                center = center,
                                style = Stroke(width = sqSize * 0.035f)
                            )
                        }
                    }

                    // Tọa độ mép bàn cờ (1..8, a..h): Kích thước lớn, nét đen đậm siêu rõ (FontWeight.Black) kèm bóng tương phản cao
                    val coordColor = if (isLight) Color(0xFF2B1304) else Color(0xFFFFF2D1)
                    val coordShadowColor = if (isLight) Color(0xFFFFF7E8).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.75f)
                    val shadowOffset = if (isLight) Offset(-1f, -1f) else Offset(1.5f, 1.5f)
                    val coordFontSize = (sqSize * 0.24f).toSp()

                    if (screenCol == 0) {
                        val rankStr = (8 - boardRow).toString()
                        val textStyle = TextStyle(
                            color = coordColor,
                            fontSize = coordFontSize,
                            fontWeight = FontWeight.Black
                        )
                        val shadowStyle = textStyle.copy(color = coordShadowColor)
                        val rankLayout = textMeasurer.measure(text = rankStr, style = textStyle)
                        val shadowLayout = textMeasurer.measure(text = rankStr, style = shadowStyle)
                        val rankPos = Offset(topLeft.x + sqSize * 0.07f, topLeft.y + sqSize * 0.05f)
                        drawText(textLayoutResult = shadowLayout, topLeft = rankPos + shadowOffset)
                        drawText(textLayoutResult = rankLayout, topLeft = rankPos)
                    }
                    if (screenRow == 7) {
                        val fileStr = ('a' + boardCol).toString()
                        val textStyle = TextStyle(
                            color = coordColor,
                            fontSize = coordFontSize,
                            fontWeight = FontWeight.Black
                        )
                        val shadowStyle = textStyle.copy(color = coordShadowColor)
                        val fileLayout = textMeasurer.measure(text = fileStr, style = textStyle)
                        val shadowLayout = textMeasurer.measure(text = fileStr, style = shadowStyle)
                        val filePos = Offset(
                            topLeft.x + sqSize - fileLayout.size.width - sqSize * 0.07f,
                            topLeft.y + sqSize - fileLayout.size.height - sqSize * 0.05f
                        )
                        drawText(textLayoutResult = shadowLayout, topLeft = filePos + shadowOffset)
                        drawText(textLayoutResult = fileLayout, topLeft = filePos)
                    }
                }
            }

            // 3. MŨI TÊN DẪN HƯỚNG TRỰC QUAN (BEZIER GLOW ARROW)
            if (focusZone == FocusZone.MOVE_SELECTION && selectedPiecePos != null) {
                if (cursorPos != selectedPiecePos && validTargetMap.containsKey(cursorPos)) {
                    val fromScreenCol = if (isBoardFlipped) 7 - selectedPiecePos.col else selectedPiecePos.col
                    val fromScreenRow = if (isBoardFlipped) 7 - selectedPiecePos.row else selectedPiecePos.row
                    val toScreenCol = if (isBoardFlipped) 7 - cursorPos.col else cursorPos.col
                    val toScreenRow = if (isBoardFlipped) 7 - cursorPos.row else cursorPos.row
                    val fromCenter = Offset(fromScreenCol * sqSize + sqSize / 2f, fromScreenRow * sqSize + sqSize / 2f)
                    val toCenter = Offset(toScreenCol * sqSize + sqSize / 2f, toScreenRow * sqSize + sqSize / 2f)
                    drawMoveGuideArrow(fromCenter, toCenter, sqSize, EmeraldValid, pulseAlpha)
                }
            } else if (hintMove != null) {
                val fromScreenCol = if (isBoardFlipped) 7 - hintMove.from.col else hintMove.from.col
                val fromScreenRow = if (isBoardFlipped) 7 - hintMove.from.row else hintMove.from.row
                val toScreenCol = if (isBoardFlipped) 7 - hintMove.to.col else hintMove.to.col
                val toScreenRow = if (isBoardFlipped) 7 - hintMove.to.row else hintMove.to.row
                val fromCenter = Offset(fromScreenCol * sqSize + sqSize / 2f, fromScreenRow * sqSize + sqSize / 2f)
                val toCenter = Offset(toScreenCol * sqSize + sqSize / 2f, toScreenRow * sqSize + sqSize / 2f)
                drawMoveGuideArrow(fromCenter, toCenter, sqSize, CyanFocus, pulseAlpha)
            }

            // 4. VẼ QUÂN CỜ ĐANG BAY / TRƯỢT 60FPS (SLIDE / PARABOL JUMP / NHẬP THÀNH ĐỒNG THỜI)
            if (animatingMove != null && moveAnimProgress < 0.99f) {
                drawAnimatedMovingPiece(
                    move = animatingMove,
                    progress = moveAnimProgress,
                    isBoardFlipped = isBoardFlipped,
                    squareSize = sqSize
                )
            }

            // 5. HIỆU ỨNG TUNG SAO & PHÁO GIẤY KHI ĂN QUÂN (STARBURST PARTICLES)
            if (captureBurstPos != null && captureBurstProgress < 0.99f) {
                val burstScreenCol = if (isBoardFlipped) 7 - captureBurstPos.col else captureBurstPos.col
                val burstScreenRow = if (isBoardFlipped) 7 - captureBurstPos.row else captureBurstPos.row
                val burstCenter = Offset(burstScreenCol * sqSize + sqSize / 2f, burstScreenRow * sqSize + sqSize / 2f)
                drawCaptureStarburst(center = burstCenter, squareSize = sqSize, progress = captureBurstProgress)
            }

            // 6. KHUNG CON TRỎ ĐIỀU HƯỚNG TRÊN TV 4K
            if (focusZone == FocusZone.PIECE_SELECTION || focusZone == FocusZone.MOVE_SELECTION) {
                val cursorScreenRow = if (isBoardFlipped) 7 - cursorPos.row else cursorPos.row
                val cursorScreenCol = if (isBoardFlipped) 7 - cursorPos.col else cursorPos.col
                val cursorTopLeft = Offset(cursorScreenCol * sqSize, cursorScreenRow * sqSize)

                val cursorMainColor = if (focusZone == FocusZone.MOVE_SELECTION) {
                    when {
                        cursorPos == selectedPiecePos -> CrimsonDanger
                        validTargetMap.containsKey(cursorPos) -> EmeraldValid
                        else -> Color(0xFF94A3B8)
                    }
                } else {
                    GoldAccent
                }

                drawTvCursorFrame(
                    topLeft = cursorTopLeft,
                    squareSize = sqSize,
                    color = cursorMainColor,
                    pulseAlpha = pulseAlpha
                )
            }
        }

        // 7. VẼ QUÂN CỜ ĐANG ĐƯỢC KÉO-THẢ NỔI TRÊN BÀN CỜ (DRAGGED PIECE FLOATING)
        if (draggedPiece != null && dragCurrentOffset != null) {
            val floatSquareSize = (size.width / 8f) * 1.18f
            val dragCenter = dragCurrentOffset!!
            val floatTopLeft = Offset(dragCenter.x - floatSquareSize / 2f, dragCenter.y - floatSquareSize * 0.7f)

            // Bóng đổ mềm dưới ngón tay
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = floatSquareSize * 0.42f,
                center = dragCenter
            )

            // Quân cờ nổi bật theo vị trí ngón tay
            drawChessPiece(
                piece = draggedPiece!!,
                topLeft = floatTopLeft,
                squareSize = floatSquareSize,
                isSelected = true
            )
        }
    }
}
}

private fun DrawScope.drawTvCursorFrame3D(
    quad: IsometricBoard3DRenderer.QuadSquare3D,
    color: Color,
    pulseAlpha: Float
) {
    val qPath = quad.toPath()
    val strokeW = (5.5f * quad.depthScale).coerceAtLeast(3.5f)

    // 1. Quầng sáng tỏa nhẹ bên trong ô
    drawPath(
        path = qPath,
        color = color.copy(alpha = 0.20f * pulseAlpha),
        style = Fill
    )

    // 2. Viền đen bóng đổ nền để tương phản trên nền gỗ/đá
    drawPath(
        path = qPath,
        color = Color.Black.copy(alpha = 0.65f),
        style = Stroke(width = strokeW * 1.35f, join = StrokeJoin.Round)
    )

    // 3. Viền chính sáng màu nổi bật
    drawPath(
        path = qPath,
        color = color.copy(alpha = pulseAlpha),
        style = Stroke(width = strokeW, join = StrokeJoin.Round)
    )

    // 4. Các góc ngàm (Corner brackets) màu trắng sáng phản chiếu
    val bracketColor = Color.White.copy(alpha = pulseAlpha)
    val bracketLen = 0.26f
    val bracketStroke = strokeW * 0.6f

    // Góc p0 (Top-Left)
    drawLine(bracketColor, quad.p0, Offset(quad.p0.x + (quad.p1.x - quad.p0.x) * bracketLen, quad.p0.y + (quad.p1.y - quad.p0.y) * bracketLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, quad.p0, Offset(quad.p0.x + (quad.p3.x - quad.p0.x) * bracketLen, quad.p0.y + (quad.p3.y - quad.p0.y) * bracketLen), bracketStroke, StrokeCap.Round)

    // Góc p1 (Top-Right)
    drawLine(bracketColor, quad.p1, Offset(quad.p1.x + (quad.p0.x - quad.p1.x) * bracketLen, quad.p1.y + (quad.p0.y - quad.p1.y) * bracketLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, quad.p1, Offset(quad.p1.x + (quad.p2.x - quad.p1.x) * bracketLen, quad.p1.y + (quad.p2.y - quad.p1.y) * bracketLen), bracketStroke, StrokeCap.Round)

    // Góc p2 (Bottom-Right)
    drawLine(bracketColor, quad.p2, Offset(quad.p2.x + (quad.p1.x - quad.p2.x) * bracketLen, quad.p2.y + (quad.p1.y - quad.p2.y) * bracketLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, quad.p2, Offset(quad.p2.x + (quad.p3.x - quad.p2.x) * bracketLen, quad.p2.y + (quad.p3.y - quad.p2.y) * bracketLen), bracketStroke, StrokeCap.Round)

    // Góc p3 (Bottom-Left)
    drawLine(bracketColor, quad.p3, Offset(quad.p3.x + (quad.p0.x - quad.p3.x) * bracketLen, quad.p3.y + (quad.p0.y - quad.p3.y) * bracketLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, quad.p3, Offset(quad.p3.x + (quad.p2.x - quad.p3.x) * bracketLen, quad.p3.y + (quad.p2.y - quad.p3.y) * bracketLen), bracketStroke, StrokeCap.Round)
}

private fun DrawScope.drawTvCursorFrame(
    topLeft: Offset,
    squareSize: Float,
    color: Color,
    pulseAlpha: Float
) {
    val pad = squareSize * 0.04f
    val strokeW = (squareSize * 0.08f).coerceAtLeast(4f)
    val cornerLen = squareSize * 0.26f

    drawRoundRect(
        color = color.copy(alpha = 0.24f * pulseAlpha),
        topLeft = Offset(topLeft.x + pad, topLeft.y + pad),
        size = Size(squareSize - pad * 2, squareSize - pad * 2),
        cornerRadius = CornerRadius(8f, 8f)
    )

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.65f),
        topLeft = Offset(topLeft.x + pad, topLeft.y + pad),
        size = Size(squareSize - pad * 2, squareSize - pad * 2),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = strokeW * 1.35f)
    )

    drawRoundRect(
        color = color.copy(alpha = pulseAlpha),
        topLeft = Offset(topLeft.x + pad, topLeft.y + pad),
        size = Size(squareSize - pad * 2, squareSize - pad * 2),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = strokeW)
    )

    val x0 = topLeft.x + pad
    val y0 = topLeft.y + pad
    val x1 = topLeft.x + squareSize - pad
    val y1 = topLeft.y + squareSize - pad

    val bracketColor = Color.White.copy(alpha = pulseAlpha)
    val bracketStroke = strokeW * 0.55f

    drawLine(bracketColor, Offset(x0, y0), Offset(x0 + cornerLen, y0), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x0, y0), Offset(x0, y0 + cornerLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x1, y0), Offset(x1 - cornerLen, y0), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x1, y0), Offset(x1, y0 + cornerLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x0, y1), Offset(x0 + cornerLen, y1), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x0, y1), Offset(x0, y1 - cornerLen), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x1, y1), Offset(x1 - cornerLen, y1), bracketStroke, StrokeCap.Round)
    drawLine(bracketColor, Offset(x1, y1), Offset(x1, y1 - cornerLen), bracketStroke, StrokeCap.Round)
}

@Composable
private fun MoveHistoryPanel(
    modifier: Modifier = Modifier,
    gameState: GameState
) {
    val turnPairs = remember(gameState.moveHistory) {
        gameState.moveHistory.chunked(2)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(gameState.moveHistory.size) {
        if (turnPairs.isNotEmpty()) {
            listState.animateScrollToItem(turnPairs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(1.5.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(10.dp)
    ) {
        // Thanh tiêu đề phía trên
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📜 NƯỚC CỜ ĐÃ ĐI",
                color = GoldAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${(gameState.moveHistory.size + 1) / 2} lượt",
                color = CyanFocus,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (gameState.moveHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có nước đi nào.\nBé Đậu đi nước mở màn nhé! ♟️",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(turnPairs) { turnIndex, movesInTurn ->
                    val whiteMove = movesInTurn.getOrNull(0)
                    val blackMove = movesInTurn.getOrNull(1)
                    val lastMove = gameState.lastMove

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cột số thứ tự
                        Text(
                            text = "${turnIndex + 1}.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(22.dp)
                        )

                        // Cột nước đi Trắng
                        if (whiteMove != null) {
                            val isLastWhite = (whiteMove == lastMove)
                            val isCapture = whiteMove.capturedPiece != null || whiteMove.isEnPassant
                            val isCheck = whiteMove.notation.contains("+") || whiteMove.notation.contains("#")
                            val text = "${whiteMove.piece.type.unicodeWhite} ${whiteMove.from.toAlgebraic()}→${whiteMove.to.toAlgebraic()}${if (isCapture) " ⚔️" else ""}${if (isCheck) "+" else ""}"

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isLastWhite) Color(0xFFFACC15).copy(alpha = 0.22f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = text,
                                    color = if (isLastWhite) GoldAccent else Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = if (isLastWhite) FontWeight.ExtraBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Cột nước đi Đen
                        if (blackMove != null) {
                            val isLastBlack = (blackMove == lastMove)
                            val isCapture = blackMove.capturedPiece != null || blackMove.isEnPassant
                            val isCheck = blackMove.notation.contains("+") || blackMove.notation.contains("#")
                            val text = "${blackMove.piece.type.unicodeBlack} ${blackMove.from.toAlgebraic()}→${blackMove.to.toAlgebraic()}${if (isCapture) " ⚔️" else ""}${if (isCheck) "+" else ""}"

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isLastBlack) Color(0xFF38BDF8).copy(alpha = 0.22f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = text,
                                    color = if (isLastBlack) CyanFocus else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = if (isLastBlack) FontWeight.ExtraBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private data class VisualTileSpec(
    val emoji: String,
    val shortTitle: String,
    val accentColor: Color
)

@Composable
private fun KidsVisualControlPanel(
    modifier: Modifier,
    focusZone: FocusZone,
    selectedMenuIndex: Int,
    difficulty: Difficulty,
    playerColor: PieceColor,
    soundEnabled: Boolean,
    canUndo: Boolean,
    isLandscapeCompact: Boolean = false,
    onMenuItemClick: (Int) -> Unit
) {
    val isMenuFocused = focusZone == FocusZone.SIDE_MENU

    val monsterEmoji = when (difficulty) {
        Difficulty.EASY -> "🦖"
        Difficulty.MEDIUM -> "🦍"
        Difficulty.HARD -> "🐲"
    }

    val tiles = listOf(
        VisualTileSpec("▶️", "Chơi tiếp", Color(0xFF10B981)),
        VisualTileSpec("↩️", "Đi lại", if (canUndo) Color(0xFFF97316) else Color(0xFF475569)),
        VisualTileSpec("💡", "Mách nước", Color(0xFFFACC15)),
        VisualTileSpec(monsterEmoji, difficulty.monsterName, Color(0xFFA855F7)),
        VisualTileSpec("🌟", "Ván mới", Color(0xFF06B6D4)),
        VisualTileSpec(if (playerColor == PieceColor.WHITE) "⚪" else "⚫", "Đổi quân", Color(0xFF3B82F6)),
        VisualTileSpec(if (soundEnabled) "🔊" else "🔇", if (soundEnabled) "Bật tiếng" else "Tắt tiếng", Color(0xFFEC4899)),
        VisualTileSpec("🏠", "Thoát TV", Color(0xFFEF4444))
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isMenuFocused) Color(0xFF1E293B) else CardSurface)
            .border(
                width = if (isMenuFocused) 2.5.dp else 1.5.dp,
                color = if (isMenuFocused) GoldAccent else CardBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎮 NÚT BẤM",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (isMenuFocused) "🔊 Tự đọc tên" else "BACK ➔ Mở",
                color = if (isMenuFocused) EmeraldValid else Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val totalRows = if (isLandscapeCompact) 2 else 4
        val totalCols = if (isLandscapeCompact) 4 else 2

        // Lưới Nút Hình Vẽ Thu Gọn (2x4 trên TV hoặc 4x2 trên điện thoại ngang)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0 until totalCols) {
                        val idx = row * totalCols + col
                        val tile = tiles[idx]
                        val isSelected = isMenuFocused && selectedMenuIndex == idx

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(
                                            listOf(tile.accentColor, tile.accentColor.copy(alpha = 0.75f))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                tile.accentColor.copy(alpha = 0.20f),
                                                Color(0xFF0F172A)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.2.dp,
                                    color = if (isSelected) Color.White else tile.accentColor.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onMenuItemClick(idx) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (idx == 3) {
                                    val iconRes = when (difficulty) {
                                        Difficulty.EASY -> R.drawable.mascot_dino
                                        Difficulty.MEDIUM -> R.drawable.mascot_kingkong
                                        Difficulty.HARD -> R.drawable.mascot_godzilla
                                    }
                                    Image(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = tile.shortTitle,
                                        modifier = Modifier.size(24.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        text = tile.emoji,
                                        fontSize = 20.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tile.shortTitle,
                                    color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionDialogOverlay(
    playerColor: PieceColor,
    selectedIndex: Int,
    onSelectPiece: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .border(3.dp, GoldAccent, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "👑✨ LÍNH TỐT BIẾN HÌNH! ✨👑",
                color = GoldAccent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PieceType.PROMOTION_OPTIONS.forEachIndexed { index, pieceType ->
                    val isSelected = index == selectedIndex
                    val piece = Piece(pieceType, playerColor)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) Color(0xFF283852) else Color(0xFF0F172A))
                            .border(
                                width = if (isSelected) 3.5.dp else 1.5.dp,
                                color = if (isSelected) GoldAccent else CardBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectPiece(index) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Canvas(modifier = Modifier.size(52.dp)) {
                            drawChessPiece(
                                piece = piece,
                                topLeft = Offset.Zero,
                                squareSize = size.width,
                                isSelected = isSelected
                            )
                        }
                        Text(
                            text = pieceType.kidFriendlyName,
                            color = if (isSelected) GoldAccent else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameOverDialogOverlay(
    gameState: GameState,
    playerColor: PieceColor,
    difficulty: Difficulty,
    selectedAction: Int,
    onSelectAction: (Int) -> Unit
) {
    val playerWon = gameState.status == GameStatus.CHECKMATE && gameState.turn != playerColor
    val bigEmoji = when {
        playerWon -> "🏆🎉👑"
        gameState.status == GameStatus.CHECKMATE -> "🦖💪✨"
        else -> "🤝🌟"
    }
    val title = when {
        playerWon -> "HOAN HÔ! BÉ ĐẬU CHIẾN THẮNG!"
        gameState.status == GameStatus.CHECKMATE -> "${difficulty.monsterName.uppercase()} THẮNG!"
        else -> "HÒA CỜ VUI VẺ!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center
    ) {
        // Mưa sao và pháo giấy lấp lánh khi bé Đậu chiến thắng
        if (playerWon) {
            val celebrationTransition = rememberInfiniteTransition(label = "victoryCelebration")
            val celebrationPhase by celebrationTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "celebrationPhase"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawVictoryCelebrationParticles(size, celebrationPhase)
            }
        }

        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 480.dp)
                .border(3.dp, GoldAccent, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = bigEmoji,
                    fontSize = 44.sp
                )
                Text(
                    text = title,
                    color = GoldAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val buttons = listOf("🌟 Chơi Ván Mới", "👀 Xem Bàn Cờ")
                    buttons.forEachIndexed { idx, label ->
                        val isFocused = selectedAction == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isFocused) Brush.horizontalGradient(listOf(GoldAccent, GoldDeep))
                                    else Brush.horizontalGradient(listOf(Color(0xFF0F172A), Color(0xFF0F172A)))
                                )
                                .border(
                                    width = if (isFocused) 3.dp else 1.5.dp,
                                    color = if (isFocused) Color.White else CardBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectAction(idx) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isFocused) Color(0xFF0F172A) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
