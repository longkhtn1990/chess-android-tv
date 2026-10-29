package com.harrylong.tvchess.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harrylong.tvchess.R
import com.harrylong.tvchess.audio.BgmPlayer
import com.harrylong.tvchess.audio.TvSoundManager
import com.harrylong.tvchess.input.GamepadAction
import com.harrylong.tvchess.input.GamepadEventBus
import com.harrylong.tvchess.model.Difficulty
import com.harrylong.tvchess.model.PieceColor
import com.harrylong.tvchess.storage.GameStorage
import com.harrylong.tvchess.ui.game.GameViewModel
import com.harrylong.tvchess.ui.learn.LearnHubScreen
import com.harrylong.tvchess.ui.learn.LessonScreen

/**
 * Các màn hình điều hướng trong ứng dụng "Đậu Học Cờ Vua".
 */
enum class AppScreen {
    HOME,
    PLAY_SETUP,
    GAME,
    LEARN_HUB,
    LESSON
}

// Bảng màu giao diện chuẩn cho màn hình TV 16:9 và điện thoại
private val BgDarkPrimary = Color(0xFF0F172A)
private val BgDarkSecondary = Color(0xFF1E293B)
private val CardSurface = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val GoldAccent = Color(0xFFFACC15)
private val GoldDeep = Color(0xFFEAB308)
private val CyanFocus = Color(0xFF38BDF8)
private val EmeraldValid = Color(0xFF10B981)

/**
 * Điều hướng trung tâm toàn ứng dụng (State-based Navigation).
 * MainActivity thiết lập AppRoot làm composable gốc.
 */
@Composable
fun AppRoot(
    onExitApp: () -> Unit = {}
) {
    ProvideDeviceProfile {
        var screenStack by remember { mutableStateOf(listOf(AppScreen.HOME)) }
        var selectedLessonId by remember { mutableStateOf("board_intro") }
        val currentScreen = screenStack.last()

        val context = LocalContext.current
        val gameViewModel: GameViewModel = viewModel()

        // Khởi tạo cài đặt Nhạc nền (BGM) từ GameStorage
        LaunchedEffect(Unit) {
            BgmPlayer.setEnabled(GameStorage.isMusicEnabled(context))
            BgmPlayer.setVolume(GameStorage.getMusicVolume(context))
        }

        // Tự động chuyển nhạc nền thư giãn theo từng màn hình & từng đối thủ quái thú
        LaunchedEffect(currentScreen, if (currentScreen == AppScreen.GAME) gameViewModel.difficulty else null) {
            val trackResId = when (currentScreen) {
                AppScreen.HOME, AppScreen.PLAY_SETUP -> R.raw.bgm_home
                AppScreen.LEARN_HUB, AppScreen.LESSON -> R.raw.bgm_lesson
                AppScreen.GAME -> when (gameViewModel.difficulty) {
                    Difficulty.EASY -> R.raw.bgm_dino
                    Difficulty.MEDIUM -> R.raw.bgm_kingkong
                    Difficulty.HARD -> R.raw.bgm_godzilla
                }
            }
            BgmPlayer.play(context, trackResId)
        }

        fun navigateTo(screen: AppScreen) {
            screenStack = screenStack + screen
        }

        fun popBack(): Boolean {
            return if (screenStack.size > 1) {
                screenStack = screenStack.dropLast(1)
                true
            } else {
                false
            }
        }

        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    onPlayWithBot = { navigateTo(AppScreen.PLAY_SETUP) },
                    onLearnChess = { navigateTo(AppScreen.LEARN_HUB) },
                    onResumeGame = {
                        gameViewModel.loadSavedSession()
                        navigateTo(AppScreen.GAME)
                    },
                    onExitApp = onExitApp
                )
            }
            AppScreen.PLAY_SETUP -> {
                PlaySetupScreen(
                    onStartGame = { color, diff ->
                        gameViewModel.startNewGame(color, diff)
                        navigateTo(AppScreen.GAME)
                    },
                    onBack = { popBack() }
                )
            }
            AppScreen.GAME -> {
                TvChessScreen(
                    viewModel = gameViewModel,
                    onExitApp = { popBack() }
                )
            }
            AppScreen.LEARN_HUB -> {
                LearnHubScreen(
                    onBack = { popBack() },
                    onSelectLesson = { lessonId ->
                        selectedLessonId = lessonId
                        navigateTo(AppScreen.LESSON)
                    }
                )
            }
            AppScreen.LESSON -> {
                LessonScreen(
                    lessonId = selectedLessonId,
                    onBack = { popBack() },
                    onGraduate = {
                        gameViewModel.startNewGame(PieceColor.WHITE, Difficulty.EASY)
                        screenStack = listOf(AppScreen.HOME, AppScreen.GAME)
                    },
                    onNextLesson = { nextId ->
                        selectedLessonId = nextId
                    }
                )
            }
        }
    }
}

// =============================================================================
// 1. MÀN HÌNH TRANG CHỦ (HOME SCREEN)
// =============================================================================

@Composable
fun HomeScreen(
    onPlayWithBot: () -> Unit,
    onLearnChess: () -> Unit,
    onResumeGame: () -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val hasSession = remember { GameStorage.hasSavedSession(context) }

    // Danh sách các nút lựa chọn trên Trang chủ
    data class HomeOption(
        val id: String,
        val icon: String,
        val title: String,
        val subtitle: String,
        val color: Color,
        val action: () -> Unit
    )

    val deviceProfile = LocalDeviceProfile.current
    val configuration = LocalConfiguration.current
    val isPhone = deviceProfile == DeviceProfile.PHONE
    val isShortScreen = configuration.screenHeightDp < 500
    val isGamepadConnected by GamepadEventBus.isGamepadConnected.collectAsState()

    val options = remember(hasSession) {
        val list = mutableListOf<HomeOption>()
        if (hasSession) {
            list.add(
                HomeOption(
                    id = "resume",
                    icon = "▶️",
                    title = "Chơi tiếp ván dở",
                    subtitle = "Tiếp tục ván cờ bé đang chơi dở",
                    color = EmeraldValid,
                    action = onResumeGame
                )
            )
        }
        list.add(
            HomeOption(
                id = "play",
                icon = "🎮",
                title = "Chơi với máy",
                subtitle = "Đấu cờ cùng Khủng Long, King Kong & Godzilla",
                color = GoldAccent,
                action = onPlayWithBot
            )
        )
        list.add(
            HomeOption(
                id = "learn",
                icon = "📚",
                title = "Học cờ vui vẻ",
                subtitle = "Khám phá cách đi của từng quân cờ & giải câu đố",
                color = CyanFocus,
                action = onLearnChess
            )
        )
        list.toList()
    }

    var focusedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    fun executeOption(index: Int) {
        val opt = options.getOrNull(index) ?: return
        TvSoundManager.playSelect()
        opt.action()
    }

    fun handleUp() {
        if (focusedIndex > 0) {
            focusedIndex--
            TvSoundManager.playNavTick()
        }
    }

    fun handleDown() {
        if (focusedIndex < options.size - 1) {
            focusedIndex++
            TvSoundManager.playNavTick()
        }
    }

    // Lắng nghe GamepadEventBus
    val currentOnGamepadAction by rememberUpdatedState { action: GamepadAction ->
        when (action) {
            is GamepadAction.Navigate -> {
                when (action.direction) {
                    com.harrylong.tvchess.model.DPadDirection.UP -> handleUp()
                    com.harrylong.tvchess.model.DPadDirection.DOWN -> handleDown()
                    else -> {}
                }
            }
            is GamepadAction.PrimaryConfirmA -> executeOption(focusedIndex)
            is GamepadAction.SecondaryCancelB -> onExitApp()
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        GamepadEventBus.actions.collect { action ->
            currentOnGamepadAction(action)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        TvSoundManager.speakWelcomeDau(context)
    }

    BackHandler(enabled = true) {
        onExitApp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BgDarkSecondary, BgDarkPrimary),
                    radius = 1600f
                )
            )
            .safeDrawingPadding()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> {
                        handleUp()
                        true
                    }
                    Key.DirectionDown, Key.S -> {
                        handleDown()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.ButtonA -> {
                        executeOption(focusedIndex)
                        true
                    }
                    Key.Back, Key.Escape, Key.ButtonB -> {
                        onExitApp()
                        true
                    }
                    else -> false
                }
            }
            .padding(
                horizontal = if (isPhone || isShortScreen) 20.dp else 32.dp,
                vertical = if (isPhone || isShortScreen) 8.dp else 24.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isPhone || isShortScreen) 0.8f else 0.85f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 8.dp else 18.dp)
        ) {
            // Tiêu đề ứng dụng sinh động thân thiện với trẻ em
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "👑", fontSize = if (isPhone || isShortScreen) 22.sp else 36.sp)
                    Text(
                        text = "ĐẬU HỌC CỜ VUA",
                        fontSize = if (isPhone || isShortScreen) 22.sp else 38.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldAccent
                    )
                    Text(text = "⭐", fontSize = if (isPhone || isShortScreen) 22.sp else 36.sp)
                }
                Text(
                    text = "Trí tuệ • Vui nhộn • Cùng các bạn Quái Thú đáng yêu",
                    fontSize = if (isPhone || isShortScreen) 11.sp else 16.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(if (isPhone || isShortScreen) 2.dp else 8.dp))

            // Danh sách các nút bấm lớn
            Column(
                modifier = Modifier.fillMaxWidth(if (isPhone || isShortScreen) 0.75f else 0.72f),
                verticalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 8.dp else 14.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isFocused = focusedIndex == index
                    val borderBrush = if (isFocused) {
                        Brush.horizontalGradient(listOf(GoldAccent, CyanFocus))
                    } else {
                        Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isFocused) Color(0xFF2A374F) else CardSurface)
                            .border(
                                width = if (isFocused) 3.dp else 1.5.dp,
                                brush = borderBrush,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                focusedIndex = index
                                executeOption(index)
                            }
                            .padding(
                                horizontal = if (isPhone || isShortScreen) 14.dp else 24.dp,
                                vertical = if (isPhone || isShortScreen) 8.dp else 18.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 12.dp else 20.dp)
                    ) {
                        // Icon tròn nổi bật
                        Box(
                            modifier = Modifier
                                .size(if (isPhone || isShortScreen) 40.dp else 56.dp)
                                .clip(CircleShape)
                                .background(option.color.copy(alpha = 0.2f))
                                .border(2.dp, option.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = option.icon, fontSize = if (isPhone || isShortScreen) 20.sp else 28.sp)
                        }

                        // Nội dung chữ
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = option.title,
                                fontSize = if (isPhone || isShortScreen) 16.sp else 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) GoldAccent else Color.White
                            )
                            Text(
                                text = option.subtitle,
                                fontSize = if (isPhone || isShortScreen) 11.sp else 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // Mũi tên chỉ thị khi focus
                        if (isFocused) {
                            Text(
                                text = "👉",
                                fontSize = if (isPhone || isShortScreen) 18.sp else 24.sp,
                                modifier = Modifier.scale(1.2f)
                            )
                        }
                    }
                }
            }

            // Gợi ý điều khiển remote/tay cầm hoặc chạm cảm ứng
            Text(
                text = if (isGamepadConnected || deviceProfile == DeviceProfile.TV) {
                    "Dùng ⬆️ ⬇️ để chọn • Bấm OK / Nút A để vào • Bấm BACK để thoát"
                } else {
                    "Chạm vào mục để chọn • Bấm BACK để thoát"
                },
                fontSize = if (isPhone || isShortScreen) 10.sp else 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }

        // Góc nhỏ dưới màn hình hiển thị version và tác giả
        Text(
            text = "v2.0 • Harry (longkhtn1990@gmail.com)",
            fontSize = if (isPhone || isShortScreen) 9.sp else 11.sp,
            color = Color(0xFF64748B),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = if (isPhone || isShortScreen) 16.dp else 24.dp, bottom = 6.dp)
        )
    }
}

// =============================================================================
// 2. MÀN HÌNH CHỌN ĐỐI THỦ & MÀU QUÂN (PLAY SETUP SCREEN)
// =============================================================================

@Composable
fun PlaySetupScreen(
    onStartGame: (PieceColor, Difficulty) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val deviceProfile = LocalDeviceProfile.current
    val configuration = LocalConfiguration.current
    val isPhone = deviceProfile == DeviceProfile.PHONE
    val isShortScreen = configuration.screenHeightDp < 500

    var selectedDifficulty by remember { mutableStateOf(Difficulty.EASY) }
    var selectedColor by remember { mutableStateOf(PieceColor.WHITE) }

    // 0 = chọn quái thú (0..2), 1 = chọn màu quân (0..1), 2 = nút hành động (0..1)
    var focusRow by remember { mutableIntStateOf(0) }
    var focusCol by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }

    val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

    fun handleConfirm() {
        when (focusRow) {
            0 -> {
                selectedDifficulty = difficulties[focusCol.coerceIn(0, 2)]
                TvSoundManager.playSelect()
            }
            1 -> {
                selectedColor = if (focusCol == 0) PieceColor.WHITE else PieceColor.BLACK
                TvSoundManager.playSelect()
            }
            2 -> {
                if (focusCol == 0) {
                    TvSoundManager.playSelect()
                    onStartGame(selectedColor, selectedDifficulty)
                } else {
                    TvSoundManager.playSelect()
                    onBack()
                }
            }
        }
    }

    fun handleNavigate(dx: Int, dy: Int) {
        if (dy != 0) {
            val nextRow = (focusRow + dy).coerceIn(0, 2)
            if (nextRow != focusRow) {
                focusRow = nextRow
                val maxCols = when (focusRow) {
                    0 -> 3
                    1 -> 2
                    2 -> 2
                    else -> 1
                }
                focusCol = focusCol.coerceIn(0, maxCols - 1)
                TvSoundManager.playNavTick()
            }
        }
        if (dx != 0) {
            val maxCols = when (focusRow) {
                0 -> 3
                1 -> 2
                2 -> 2
                else -> 1
            }
            val nextCol = (focusCol + dx).coerceIn(0, maxCols - 1)
            if (nextCol != focusCol) {
                focusCol = nextCol
                TvSoundManager.playNavTick()
                // Tự động đồng bộ chọn ngay khi di chuyển trên hàng quái thú hoặc màu quân
                if (focusRow == 0) {
                    val newDiff = difficulties[focusCol]
                    if (newDiff != selectedDifficulty) {
                        selectedDifficulty = newDiff
                        TvSoundManager.speakBotGreeting(context, newDiff)
                    }
                } else if (focusRow == 1) {
                    selectedColor = if (focusCol == 0) PieceColor.WHITE else PieceColor.BLACK
                }
            }
        }
    }

    // Lắng nghe GamepadEventBus
    val currentOnGamepadAction by rememberUpdatedState { action: GamepadAction ->
        when (action) {
            is GamepadAction.Navigate -> {
                when (action.direction) {
                    com.harrylong.tvchess.model.DPadDirection.UP -> handleNavigate(0, -1)
                    com.harrylong.tvchess.model.DPadDirection.DOWN -> handleNavigate(0, 1)
                    com.harrylong.tvchess.model.DPadDirection.LEFT -> handleNavigate(-1, 0)
                    com.harrylong.tvchess.model.DPadDirection.RIGHT -> handleNavigate(1, 0)
                }
            }
            is GamepadAction.PrimaryConfirmA -> handleConfirm()
            is GamepadAction.SecondaryCancelB -> onBack()
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        GamepadEventBus.actions.collect { action ->
            currentOnGamepadAction(action)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        TvSoundManager.speakBotGreeting(context, selectedDifficulty)
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BgDarkSecondary, BgDarkPrimary),
                    radius = 1600f
                )
            )
            .safeDrawingPadding()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.W -> {
                        handleNavigate(0, -1)
                        true
                    }
                    Key.DirectionDown, Key.S -> {
                        handleNavigate(0, 1)
                        true
                    }
                    Key.DirectionLeft, Key.A -> {
                        handleNavigate(-1, 0)
                        true
                    }
                    Key.DirectionRight, Key.D -> {
                        handleNavigate(1, 0)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.ButtonA -> {
                        handleConfirm()
                        true
                    }
                    Key.Back, Key.Escape, Key.ButtonB -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            }
            .padding(
                horizontal = if (isPhone || isShortScreen) 16.dp else 24.dp,
                vertical = if (isPhone || isShortScreen) 8.dp else 16.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isPhone || isShortScreen) 0.95f else 0.92f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 8.dp else 16.dp)
        ) {
            // Tiêu đề
            Text(
                text = "⚔️ CHỌN ĐỐI THỦ & MÀU QUÂN",
                fontSize = if (isPhone || isShortScreen) 18.sp else 28.sp,
                fontWeight = FontWeight.Black,
                color = GoldAccent
            )

            // 1. Ba thẻ Quái Thú (Khủng Long, King Kong, Godzilla)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 8.dp else 14.dp)
            ) {
                difficulties.forEachIndexed { index, diff ->
                    val isSelected = selectedDifficulty == diff
                    val isFocused = focusRow == 0 && focusCol == index
                    val winCount = remember(diff) { GameStorage.getWinCount(context, diff) }

                    val mascotRes = when (diff) {
                        Difficulty.EASY -> R.drawable.mascot_dino
                        Difficulty.MEDIUM -> R.drawable.mascot_kingkong
                        Difficulty.HARD -> R.drawable.mascot_godzilla
                    }

                    val borderColor = when {
                        isFocused -> CyanFocus
                        isSelected -> GoldAccent
                        else -> CardBorder
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFF223554) else CardSurface)
                            .border(
                                width = if (isFocused || isSelected) 3.dp else 1.5.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                focusRow = 0
                                focusCol = index
                                if (selectedDifficulty != diff) {
                                    selectedDifficulty = diff
                                    TvSoundManager.speakBotGreeting(context, diff)
                                } else {
                                    TvSoundManager.playSelect()
                                }
                            }
                            .padding(if (isPhone || isShortScreen) 8.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 3.dp else 6.dp)
                    ) {
                        // Ảnh quái thú
                        Image(
                            painter = painterResource(id = mascotRes),
                            contentDescription = diff.monsterName,
                            modifier = Modifier
                                .size(if (isPhone || isShortScreen) 48.dp else 90.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )

                        Text(
                            text = diff.monsterName,
                            fontSize = if (isPhone || isShortScreen) 14.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) GoldAccent else Color.White
                        )

                        Text(
                            text = diff.subtitle,
                            fontSize = if (isPhone || isShortScreen) 10.sp else 12.sp,
                            color = Color(0xFF94A3B8)
                        )

                        // Huy hiệu số sao & số trận thắng
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "⭐".repeat(diff.starCount), fontSize = if (isPhone || isShortScreen) 10.sp else 12.sp)
                            if (winCount > 0) {
                                Text(
                                    text = "• Thắng $winCount 🏆",
                                    fontSize = if (isPhone || isShortScreen) 9.sp else 11.sp,
                                    color = EmeraldValid,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Chọn Màu Quân (Trắng / Đen)
            Row(
                modifier = Modifier.fillMaxWidth(if (isPhone || isShortScreen) 0.7f else 0.65f),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 10.dp else 16.dp)
            ) {
                listOf(
                    PieceColor.WHITE to "♔ Cầm quân Trắng (Đi trước)",
                    PieceColor.BLACK to "♚ Cầm quân Đen (Đi sau)"
                ).forEachIndexed { index, (color, label) ->
                    val isSelected = selectedColor == color
                    val isFocused = focusRow == 1 && focusCol == index

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0xFF2A3A5A) else CardSurface)
                            .border(
                                width = if (isFocused) 3.dp else if (isSelected) 2.5.dp else 1.dp,
                                color = if (isFocused) CyanFocus else if (isSelected) GoldAccent else CardBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                focusRow = 1
                                focusCol = index
                                selectedColor = color
                                TvSoundManager.playSelect()
                            }
                            .padding(vertical = if (isPhone || isShortScreen) 8.dp else 12.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = if (isPhone || isShortScreen) 13.sp else 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) GoldAccent else Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 3. Nút Vào Chơi & Quay Lại
            Row(
                modifier = Modifier.fillMaxWidth(if (isPhone || isShortScreen) 0.6f else 0.55f),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone || isShortScreen) 10.dp else 16.dp)
            ) {
                // Nút Vào Chơi
                val isStartFocused = focusRow == 2 && focusCol == 0
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (isStartFocused) listOf(GoldAccent, GoldDeep) else listOf(GoldDeep, Color(0xFFCA8A04))
                            )
                        )
                        .border(
                            width = if (isStartFocused) 3.dp else 1.dp,
                            color = if (isStartFocused) CyanFocus else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            focusRow = 2
                            focusCol = 0
                            TvSoundManager.playSelect()
                            onStartGame(selectedColor, selectedDifficulty)
                        }
                        .padding(vertical = if (isPhone || isShortScreen) 10.dp else 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚔️ Vào Chơi Ngay",
                        fontSize = if (isPhone || isShortScreen) 15.sp else 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E293B)
                    )
                }

                // Nút Quay Lại
                val isBackFocused = focusRow == 2 && focusCol == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardSurface)
                        .border(
                            width = if (isBackFocused) 3.dp else 1.5.dp,
                            color = if (isBackFocused) CyanFocus else CardBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            focusRow = 2
                            focusCol = 1
                            TvSoundManager.playSelect()
                            onBack()
                        }
                        .padding(vertical = if (isPhone || isShortScreen) 10.dp else 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⬅️ Quay Lại",
                        fontSize = if (isPhone || isShortScreen) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


