package com.harrylong.tvchess.ui.learn

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harrylong.tvchess.audio.TvSoundManager
import com.harrylong.tvchess.learn.Lesson
import com.harrylong.tvchess.learn.LessonRepository
import com.harrylong.tvchess.storage.GameStorage
import com.harrylong.tvchess.ui.DeviceProfile
import com.harrylong.tvchess.ui.LocalDeviceProfile

// Bảng màu cho Học cờ
private val BgDarkPrimary = Color(0xFF0F172A)
private val BgDarkSecondary = Color(0xFF1E293B)
private val CardSurface = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val GoldAccent = Color(0xFFFACC15)
private val GoldDeep = Color(0xFFEAB308)
private val CyanFocus = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)

/**
 * Màn hình Bản đồ & Danh sách bài học cờ vua (Learn Hub Screen).
 * Hỗ trợ D-Pad điều hướng TV và cảm ứng điện thoại (dọc & ngang).
 */
@Composable
fun LearnHubScreen(
    onBack: () -> Unit,
    onSelectLesson: (String) -> Unit
) {
    val context = LocalContext.current
    val deviceProfile = LocalDeviceProfile.current

    // Nạp tiến độ số sao của bé Đậu
    val lessonProgress = remember { GameStorage.getLessonProgress(context) }
    val lessons = remember { LessonRepository.lessons }

    val totalStarsEarned = remember(lessonProgress) {
        lessons.sumOf { lessonProgress[it.id] ?: 0 }
    }
    val maxPossibleStars = lessons.size * 3

    val backFocusRequester = remember { FocusRequester() }
    val firstLessonFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        onBack()
    }

    LaunchedEffect(Unit) {
        TvSoundManager.speakRaw(
            context,
            com.harrylong.tvchess.R.raw.voice_welcome_dau_2,
            "Chào bé Đậu dễ thương! Hôm nay mình cùng học cờ vua thiệt vui nha!"
        )
        // Tự động focus vào bài học đầu tiên chưa hoàn thành (hoặc bài 1)
        try {
            firstLessonFocusRequester.requestFocus()
        } catch (_: Throwable) {
            backFocusRequester.requestFocus()
        }
    }

    val columns = when (deviceProfile) {
        DeviceProfile.PHONE -> 3
        DeviceProfile.TABLET -> 3
        DeviceProfile.TV -> 4
    }

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
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HEADER BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Nút quay lại
                var isBackFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isBackFocused) CyanFocus.copy(alpha = 0.25f) else CardSurface)
                        .border(
                            width = if (isBackFocused) 2.5.dp else 1.dp,
                            color = if (isBackFocused) CyanFocus else CardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .focusRequester(backFocusRequester)
                        .onFocusChanged { isBackFocused = it.isFocused }
                        .focusable()
                        .clickable {
                            TvSoundManager.playSelect()
                            onBack()
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.ButtonA)
                            ) {
                                TvSoundManager.playSelect()
                                onBack()
                                true
                            } else false
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "⬅️", fontSize = 18.sp)
                    Text(
                        text = "Trang chủ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Tiêu đề giữa
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🎓", fontSize = 26.sp)
                        Text(
                            text = "HỌC CỜ CÙNG BÉ ĐẬU",
                            fontSize = if (deviceProfile == DeviceProfile.PHONE) 18.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldAccent
                        )
                    }
                    Text(
                        text = "12 bài học bổ ích từ cơ bản đến kiện tướng nhí",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Huy hiệu tổng số sao đạt được
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(GoldDeep.copy(alpha = 0.2f))
                        .border(1.5.dp, GoldAccent, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "⭐", fontSize = 18.sp)
                        Text(
                            text = "$totalStarsEarned / $maxPossibleStars",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldAccent
                        )
                    }
                }
            }

            // GRID DANH SÁCH 12 BÀI HỌC
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(lessons) { index, lesson ->
                    val stars = lessonProgress[lesson.id] ?: 0

                    // Mở khóa bài học theo tuần tự:
                    // Bài 1 luôn mở. Bài N mở khi bài N-1 đã có ít nhất 1 sao.
                    val isUnlocked = index == 0 || (lessonProgress[lessons[index - 1].id] ?: 0) > 0

                    LessonCard(
                        lesson = lesson,
                        stars = stars,
                        isUnlocked = isUnlocked,
                        modifier = if (index == 0) Modifier.focusRequester(firstLessonFocusRequester) else Modifier,
                        onClick = {
                            if (isUnlocked) {
                                TvSoundManager.playSelect()
                                onSelectLesson(lesson.id)
                            } else {
                                TvSoundManager.playNavTick()
                                Toast.makeText(
                                    context,
                                    "Bé Đậu hãy hoàn thành bài trước để mở khóa nha! ⭐",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị một bài học trong LearnHub.
 */
@Composable
private fun LessonCard(
    lesson: Lesson,
    stars: Int,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isFocused -> CyanFocus
        stars == 3 -> GoldAccent
        stars > 0 -> EmeraldSuccess
        isUnlocked -> CardBorder
        else -> Color(0xFF263346)
    }

    val borderWidth = if (isFocused) 3.dp else if (stars > 0) 1.8.dp else 1.dp
    val alpha = if (isUnlocked) 1f else 0.45f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) CardSurface.copy(alpha = 0.95f) else CardSurface.copy(alpha = alpha))
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.ButtonA || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header thẻ: Số thứ tự + Icon Khóa hoặc Sao
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thứ tự bài
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isUnlocked) GoldAccent.copy(alpha = 0.2f) else Color(0xFF334155))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Bài ${lesson.orderNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) GoldAccent else Color(0xFF94A3B8)
                    )
                }

                // Trạng thái: Khóa hoặc 3 sao
                if (!isUnlocked) {
                    Text(text = "🔒", fontSize = 16.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (s in 1..3) {
                            Text(
                                text = if (s <= stars) "⭐" else "☆",
                                fontSize = 13.sp,
                                color = if (s <= stars) GoldAccent else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Emoji biểu tượng lớn
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text(text = lesson.emoji, fontSize = 32.sp)
            }

            // Tiêu đề bài học
            Text(
                text = lesson.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) Color.White else Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Mô tả tóm tắt
            Text(
                text = lesson.subtitle,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
