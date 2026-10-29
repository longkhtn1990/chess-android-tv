package com.harrylong.tvchess.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.harrylong.tvchess.model.BotMood
import com.harrylong.tvchess.model.Difficulty
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object MonsterMascotRenderer {

    /**
     * Vẽ nhân vật Quái Thú Chibi đáng yêu (Khủng Long Nhí, King Kong Con, Godzilla Nhí)
     * với đầy đủ cử chỉ sống động: gãi đầu suy nghĩ, vỗ tay khen bé, khóc nhè khi mất quân, ngạc nhiên...
     * Có hào quang sân khấu 3D mượt mà 60fps trên TV 4K.
     */
    fun DrawScope.drawMonsterMascot(
        difficulty: Difficulty,
        mood: BotMood,
        breathPhase: Float,   // 0f..1f chuyển động nhún nhảy liên tục
        actionPhase: Float,   // 0f..1f chuyển động tay (gãi đầu, vỗ tay, rơi nước mắt)
        isBlinking: Boolean,
        customBitmap: ImageBitmap? = null
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val bounceY = sin(breathPhase * 2f * PI.toFloat()) * (h * 0.022f)
        val cy = h * 0.54f + bounceY
        val r = minOf(w, h) * 0.34f

        // Vòng hào quang sân khấu phát sáng mềm mại 3D dưới chân bạn Quái Thú
        val stageGlowRadius = r * 1.35f
        val stageGlowBrush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.32f),
                Color(0xFF818CF8).copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = Offset(cx, h * 0.85f),
            radius = stageGlowRadius
        )
        drawOval(
            brush = stageGlowBrush,
            topLeft = Offset(cx - stageGlowRadius, h * 0.85f - stageGlowRadius * 0.28f),
            size = Size(stageGlowRadius * 2f, stageGlowRadius * 0.56f)
        )

        // Bóng đổ dưới chân nhân vật
        drawOval(
            color = Color.Black.copy(alpha = 0.38f),
            topLeft = Offset(cx - r * 0.85f, h * 0.84f),
            size = Size(r * 1.7f, r * 0.28f)
        )

        if (customBitmap != null) {
            drawCustomMascotSprite(
                bitmap = customBitmap,
                cx = cx,
                cy = cy,
                w = w,
                h = h,
                r = r,
                mood = mood,
                breathPhase = breathPhase,
                actionPhase = actionPhase
            )
            drawMoodEffects(cx, cy, r, mood, actionPhase)
            return
        }

        when (difficulty) {
            Difficulty.EASY -> drawBabyDino(cx, cy, r, mood, actionPhase, isBlinking)
            Difficulty.MEDIUM -> drawChibiKingKong(cx, cy, r, mood, actionPhase, isBlinking)
            Difficulty.HARD -> drawChibiGodzilla(cx, cy, r, mood, actionPhase, isBlinking)
        }

        // Hiệu ứng cảm xúc bay xung quanh đầu (bong bóng suy nghĩ, ngôi sao khen ngợi, nốt nhạc, giọt nước mắt)
        drawMoodEffects(cx, cy, r, mood, actionPhase)
    }

    private fun DrawScope.drawCustomMascotSprite(
        bitmap: ImageBitmap,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        r: Float,
        mood: BotMood,
        breathPhase: Float,
        actionPhase: Float
    ) {
        val wave = sin(actionPhase * 2f * PI.toFloat())
        val breath = sin(breathPhase * 2f * PI.toFloat())

        val tiltDeg = when (mood) {
            BotMood.THINKING -> -6f + wave * 4f
            BotMood.CLAPPING, BotMood.PROUD_LAUGH -> wave * 6f
            BotMood.CRYING -> -4f + sin(actionPhase * 4f * PI.toFloat()) * 3f
            BotMood.SURPRISED -> 4f
            else -> breath * 2.5f
        }
        val scaleX = when (mood) {
            BotMood.SURPRISED -> 1.06f
            BotMood.CLAPPING -> 1.0f + wave * 0.04f
            else -> 1.0f + breath * 0.02f
        }
        val scaleY = when (mood) {
            BotMood.SURPRISED -> 1.06f
            BotMood.CLAPPING -> 1.0f - wave * 0.04f
            else -> 1.0f - breath * 0.02f
        }
        val extraOffsetY = when (mood) {
            BotMood.SURPRISED -> -abs(wave) * (h * 0.035f)
            BotMood.CLAPPING, BotMood.PROUD_LAUGH -> -abs(wave) * (h * 0.04f)
            else -> 0f
        }

        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val targetH = (h * 0.88f).coerceAtMost(w * 0.92f / aspect)
        val targetW = targetH * aspect
        val dstLeft = (cx - targetW * 0.5f).roundToInt()
        val dstTop = (cy - targetH * 0.52f + extraOffsetY).roundToInt()

        withTransform({
            rotate(degrees = tiltDeg, pivot = Offset(cx, cy + targetH * 0.2f))
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset(cx, cy + targetH * 0.25f))
        }) {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(dstLeft, dstTop),
                dstSize = IntSize(targetW.roundToInt(), targetH.roundToInt())
            )
        }

        // Hiệu ứng giọt nước mắt tuôn rơi trực tiếp từ mắt nhân vật khi CRYING
        if (mood == BotMood.CRYING) {
            val eyeX = cx - targetW * 0.16f
            val eyeY = cy - targetH * 0.18f
            val dropOffset = actionPhase * (r * 0.45f)
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = r * 0.08f,
                center = Offset(eyeX - r * 0.06f, eyeY + r * 0.10f + dropOffset)
            )
            drawCircle(
                color = Color(0xFF7DD3FC),
                radius = r * 0.065f,
                center = Offset(eyeX + r * 0.08f, eyeY + r * 0.22f + dropOffset * 0.8f)
            )
        }
    }

    private fun DrawScope.drawBabyDino(
        cx: Float,
        cy: Float,
        r: Float,
        mood: BotMood,
        actionPhase: Float,
        isBlinking: Boolean
    ) {
        val bodyGreen = Color(0xFF34D399)
        val darkGreen = Color(0xFF059669)
        val bellyCream = Color(0xFFFEF3C7)
        val spikeOrange = Color(0xFFF59E0B)
        val outline = Color(0xFF064E3B)
        val strokeW = (r * 0.06f).coerceAtLeast(2.5f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Đuôi khủng long vẫy qua lại
        val tailWag = sin(actionPhase * 2f * PI.toFloat()) * (r * 0.14f)
        val tailPath = Path().apply {
            moveTo(cx + r * 0.65f, cy + r * 0.45f)
            cubicTo(
                cx + r * 1.25f, cy + r * 0.40f + tailWag,
                cx + r * 1.35f, cy + r * 0.05f + tailWag,
                cx + r * 1.05f, cy - r * 0.05f + tailWag
            )
            lineTo(cx + r * 0.55f, cy + r * 0.20f)
            close()
        }
        drawPath(tailPath, color = bodyGreen, style = Fill)
        drawPath(tailPath, color = outline, style = stroke)

        // Gai lưng khủng long màu cam dễ thương
        for (i in 0..2) {
            val sy = cy - r * (0.85f - i * 0.28f)
            val sx = cx + r * (0.35f + i * 0.18f)
            val spike = Path().apply {
                moveTo(sx - r * 0.12f, sy)
                lineTo(sx + r * 0.16f, sy - r * 0.18f)
                lineTo(sx + r * 0.08f, sy + r * 0.14f)
                close()
            }
            drawPath(spike, color = spikeOrange, style = Fill)
            drawPath(spike, color = outline, style = stroke)
        }

        // Hai chân tròn mũm mĩm
        drawOval(
            color = darkGreen,
            topLeft = Offset(cx - r * 0.68f, cy + r * 0.55f),
            size = Size(r * 0.48f, r * 0.36f)
        )
        drawOval(
            color = darkGreen,
            topLeft = Offset(cx + r * 0.20f, cy + r * 0.55f),
            size = Size(r * 0.48f, r * 0.36f)
        )

        // Thân và đầu khủng long Chibi tròn trịa
        drawRoundRect(
            color = bodyGreen,
            topLeft = Offset(cx - r * 0.82f, cy - r * 0.92f),
            size = Size(r * 1.64f, r * 1.64f),
            cornerRadius = CornerRadius(r * 0.78f, r * 0.78f)
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(cx - r * 0.82f, cy - r * 0.92f),
            size = Size(r * 1.64f, r * 1.64f),
            cornerRadius = CornerRadius(r * 0.78f, r * 0.78f),
            style = stroke
        )

        // Bụng kem có vạch ngang
        drawOval(
            color = bellyCream,
            topLeft = Offset(cx - r * 0.52f, cy + r * 0.02f),
            size = Size(r * 1.04f, r * 0.65f)
        )
        drawLine(
            color = spikeOrange.copy(alpha = 0.6f),
            start = Offset(cx - r * 0.34f, cy + r * 0.24f),
            end = Offset(cx + r * 0.34f, cy + r * 0.24f),
            strokeWidth = strokeW * 0.7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = spikeOrange.copy(alpha = 0.6f),
            start = Offset(cx - r * 0.28f, cy + r * 0.44f),
            end = Offset(cx + r * 0.28f, cy + r * 0.44f),
            strokeWidth = strokeW * 0.7f,
            cap = StrokeCap.Round
        )

        // Sừng nhỏ đỉnh đầu
        val topSpike = Path().apply {
            moveTo(cx - r * 0.16f, cy - r * 0.88f)
            lineTo(cx, cy - r * 1.18f)
            lineTo(cx + r * 0.16f, cy - r * 0.88f)
            close()
        }
        drawPath(topSpike, color = spikeOrange, style = Fill)
        drawPath(topSpike, color = outline, style = stroke)

        // Vẽ khuôn mặt biểu cảm (Mắt, Má hồng, Miệng)
        drawExpressiveFace(
            cx = cx,
            cy = cy - r * 0.26f,
            r = r,
            mood = mood,
            actionPhase = actionPhase,
            isBlinking = isBlinking,
            toothColor = Color.White
        )

        // Vẽ hai cánh tay hành động (Gãi đầu, Vỗ tay, Dụi mắt khóc, Vẫy chào)
        drawAnimatedArms(
            cx = cx,
            cy = cy,
            r = r,
            mood = mood,
            actionPhase = actionPhase,
            armColor = bodyGreen,
            outlineColor = outline,
            stroke = stroke
        )
    }

    private fun DrawScope.drawChibiKingKong(
        cx: Float,
        cy: Float,
        r: Float,
        mood: BotMood,
        actionPhase: Float,
        isBlinking: Boolean
    ) {
        val furBrown = Color(0xFF78350F)
        val furLight = Color(0xFF92400E)
        val skinPeach = Color(0xFFFDE68A)
        val crownGold = Color(0xFFFACC15)
        val outline = Color(0xFF271206)
        val strokeW = (r * 0.06f).coerceAtLeast(2.5f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Hai tai tròn hai bên đầu King Kong
        for (sign in intArrayOf(-1, 1)) {
            val earCenter = Offset(cx + sign * r * 0.84f, cy - r * 0.36f)
            drawCircle(color = furBrown, radius = r * 0.24f, center = earCenter)
            drawCircle(color = outline, radius = r * 0.24f, center = earCenter, style = stroke)
            drawCircle(color = skinPeach, radius = r * 0.13f, center = earCenter)
        }

        // Hai chân khỏe khoắn
        drawOval(
            color = furBrown,
            topLeft = Offset(cx - r * 0.72f, cy + r * 0.52f),
            size = Size(r * 0.52f, r * 0.38f)
        )
        drawOval(
            color = furBrown,
            topLeft = Offset(cx + r * 0.20f, cy + r * 0.52f),
            size = Size(r * 0.52f, r * 0.38f)
        )

        // Thân và đầu King Kong Chibi
        drawRoundRect(
            color = furBrown,
            topLeft = Offset(cx - r * 0.86f, cy - r * 0.90f),
            size = Size(r * 1.72f, r * 1.62f),
            cornerRadius = CornerRadius(r * 0.75f, r * 0.75f)
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(cx - r * 0.86f, cy - r * 0.90f),
            size = Size(r * 1.72f, r * 1.62f),
            cornerRadius = CornerRadius(r * 0.75f, r * 0.75f),
            style = stroke
        )

        // Ngực và bụng màu sáng
        drawOval(
            color = furLight,
            topLeft = Offset(cx - r * 0.56f, cy + r * 0.02f),
            size = Size(r * 1.12f, r * 0.62f)
        )

        // Cà vạt đỏ nhỏ siêu cưng của King Kong
        val tiePath = Path().apply {
            moveTo(cx - r * 0.10f, cy + r * 0.02f)
            lineTo(cx + r * 0.10f, cy + r * 0.02f)
            lineTo(cx + r * 0.15f, cy + r * 0.34f)
            lineTo(cx, cy + r * 0.46f)
            lineTo(cx - r * 0.15f, cy + r * 0.34f)
            close()
        }
        drawPath(tiePath, color = Color(0xFFEF4444), style = Fill)
        drawPath(tiePath, color = outline, style = stroke)

        // Khuôn mặt sáng màu của King Kong
        drawRoundRect(
            color = skinPeach,
            topLeft = Offset(cx - r * 0.64f, cy - r * 0.62f),
            size = Size(r * 1.28f, r * 0.68f),
            cornerRadius = CornerRadius(r * 0.34f, r * 0.34f)
        )

        // Chỏm tóc/vương miện nhỏ trên đỉnh đầu King Kong
        val crown = Path().apply {
            moveTo(cx - r * 0.32f, cy - r * 0.86f)
            lineTo(cx - r * 0.24f, cy - r * 1.16f)
            lineTo(cx - r * 0.08f, cy - r * 0.98f)
            lineTo(cx, cy - r * 1.22f)
            lineTo(cx + r * 0.08f, cy - r * 0.98f)
            lineTo(cx + r * 0.24f, cy - r * 1.16f)
            lineTo(cx + r * 0.32f, cy - r * 0.86f)
            close()
        }
        drawPath(crown, color = crownGold, style = Fill)
        drawPath(crown, color = outline, style = stroke)

        drawExpressiveFace(
            cx = cx,
            cy = cy - r * 0.26f,
            r = r,
            mood = mood,
            actionPhase = actionPhase,
            isBlinking = isBlinking,
            toothColor = Color.White
        )

        drawAnimatedArms(
            cx = cx,
            cy = cy,
            r = r,
            mood = mood,
            actionPhase = actionPhase,
            armColor = furBrown,
            outlineColor = outline,
            stroke = stroke
        )
    }

    private fun DrawScope.drawChibiGodzilla(
        cx: Float,
        cy: Float,
        r: Float,
        mood: BotMood,
        actionPhase: Float,
        isBlinking: Boolean
    ) {
        val zillaBlue = Color(0xFF2563EB)
        val zillaDeep = Color(0xFF1D4ED8)
        val bellyCyan = Color(0xFFBAE6FD)
        val dorsalNeon = Color(0xFF38BDF8)
        val outline = Color(0xFF0F172A)
        val strokeW = (r * 0.06f).coerceAtLeast(2.5f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Đuôi Godzilla phát sáng
        val tailWag = cos(actionPhase * 2f * PI.toFloat()) * (r * 0.15f)
        val tailPath = Path().apply {
            moveTo(cx + r * 0.65f, cy + r * 0.45f)
            cubicTo(
                cx + r * 1.32f, cy + r * 0.38f + tailWag,
                cx + r * 1.40f, cy - r * 0.05f + tailWag,
                cx + r * 1.05f, cy - r * 0.15f + tailWag
            )
            lineTo(cx + r * 0.55f, cy + r * 0.18f)
            close()
        }
        drawPath(tailPath, color = zillaDeep, style = Fill)
        drawPath(tailPath, color = outline, style = stroke)

        // Vây lưng năng lượng nguyên tử xanh dương phát sáng của Godzilla Nhí
        for (i in 0..3) {
            val sy = cy - r * (0.90f - i * 0.26f)
            val sx = cx + r * (0.34f + i * 0.15f)
            val fin = Path().apply {
                moveTo(sx - r * 0.10f, sy)
                lineTo(sx + r * 0.24f, sy - r * 0.14f)
                lineTo(sx + r * 0.16f, sy + r * 0.02f)
                lineTo(sx + r * 0.28f, sy + r * 0.08f)
                lineTo(sx + r * 0.05f, sy + r * 0.16f)
                close()
            }
            drawPath(fin, color = dorsalNeon, style = Fill)
            drawPath(fin, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = strokeW * 0.6f))
        }

        // Chân Godzilla
        drawOval(
            color = zillaDeep,
            topLeft = Offset(cx - r * 0.70f, cy + r * 0.54f),
            size = Size(r * 0.50f, r * 0.36f)
        )
        drawOval(
            color = zillaDeep,
            topLeft = Offset(cx + r * 0.20f, cy + r * 0.54f),
            size = Size(r * 0.50f, r * 0.36f)
        )

        // Thân và đầu Godzilla Nhí
        drawRoundRect(
            color = zillaBlue,
            topLeft = Offset(cx - r * 0.84f, cy - r * 0.92f),
            size = Size(r * 1.68f, r * 1.64f),
            cornerRadius = CornerRadius(r * 0.72f, r * 0.72f)
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(cx - r * 0.84f, cy - r * 0.92f),
            size = Size(r * 1.68f, r * 1.64f),
            cornerRadius = CornerRadius(r * 0.72f, r * 0.72f),
            style = stroke
        )

        // Bụng giáp xanh băng
        drawOval(
            color = bellyCyan,
            topLeft = Offset(cx - r * 0.52f, cy + r * 0.04f),
            size = Size(r * 1.04f, r * 0.62f)
        )

        // Vây đỉnh đầu phát sáng
        val headCrest = Path().apply {
            moveTo(cx - r * 0.22f, cy - r * 0.88f)
            lineTo(cx - r * 0.12f, cy - r * 1.12f)
            lineTo(cx, cy - r * 1.26f)
            lineTo(cx + r * 0.12f, cy - r * 1.12f)
            lineTo(cx + r * 0.22f, cy - r * 0.88f)
            close()
        }
        drawPath(headCrest, color = dorsalNeon, style = Fill)
        drawPath(headCrest, color = outline, style = stroke)

        drawExpressiveFace(
            cx = cx,
            cy = cy - r * 0.26f,
            r = r,
            mood = mood,
            actionPhase = actionPhase,
            isBlinking = isBlinking,
            toothColor = Color.White
        )

        drawAnimatedArms(
            cx = cx,
            cy = cy,
            r = r,
            mood = mood,
            actionPhase = actionPhase,
            armColor = zillaBlue,
            outlineColor = outline,
            stroke = stroke
        )
    }

    private fun DrawScope.drawExpressiveFace(
        cx: Float,
        cy: Float,
        r: Float,
        mood: BotMood,
        actionPhase: Float,
        isBlinking: Boolean,
        toothColor: Color
    ) {
        val eyeOffsetX = r * 0.34f
        val eyeY = cy - r * 0.08f
        val eyeRadius = r * 0.16f
        val darkColor = Color(0xFF0F172A)
        val strokeW = (r * 0.055f).coerceAtLeast(2.5f)

        // Hai má hồng phúng phính dễ thương
        drawOval(
            color = Color(0xFFF43F5E).copy(alpha = 0.48f),
            topLeft = Offset(cx - r * 0.62f, cy + r * 0.04f),
            size = Size(r * 0.26f, r * 0.15f)
        )
        drawOval(
            color = Color(0xFFF43F5E).copy(alpha = 0.48f),
            topLeft = Offset(cx + r * 0.36f, cy + r * 0.04f),
            size = Size(r * 0.26f, r * 0.15f)
        )

        // 1. Vẽ Mắt theo từng trạng thái cảm xúc
        for (sign in intArrayOf(-1, 1)) {
            val ex = cx + sign * eyeOffsetX
            when {
                mood == BotMood.CRYING -> {
                    // Mắt nhíu lại khóc nhè (> <) kèm hai dòng nước mắt tuôn rơi
                    drawLine(
                        color = darkColor,
                        start = Offset(ex - eyeRadius * 0.7f, eyeY - eyeRadius * 0.4f),
                        end = Offset(ex + sign * (-eyeRadius * 0.5f), eyeY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = darkColor,
                        start = Offset(ex - eyeRadius * 0.7f, eyeY + eyeRadius * 0.4f),
                        end = Offset(ex + sign * (-eyeRadius * 0.5f), eyeY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Suối nước mắt xanh chảy xuống má
                    val tearDropOffset = (actionPhase * r * 0.35f)
                    drawRoundRect(
                        color = Color(0xFF38BDF8),
                        topLeft = Offset(ex - r * 0.06f, eyeY + r * 0.05f),
                        size = Size(r * 0.12f, r * 0.38f),
                        cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
                    )
                    drawCircle(
                        color = Color(0xFF7DD3FC),
                        radius = r * 0.07f,
                        center = Offset(ex + sign * r * 0.08f, eyeY + r * 0.22f + tearDropOffset)
                    )
                }

                mood == BotMood.CLAPPING || mood == BotMood.PROUD_LAUGH || isBlinking -> {
                    // Mắt cười tít hình vòng cung (^ ^)
                    drawArc(
                        color = darkColor,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(ex - eyeRadius, eyeY - eyeRadius * 0.6f),
                        size = Size(eyeRadius * 2f, eyeRadius * 1.4f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                mood == BotMood.SURPRISED -> {
                    // Mắt tròn xoe to đùng ngạc nhiên
                    drawCircle(color = Color.White, radius = eyeRadius * 1.18f, center = Offset(ex, eyeY))
                    drawCircle(
                        color = darkColor,
                        radius = eyeRadius * 1.18f,
                        center = Offset(ex, eyeY),
                        style = Stroke(width = strokeW * 0.8f)
                    )
                    drawCircle(color = darkColor, radius = eyeRadius * 0.55f, center = Offset(ex, eyeY))
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.24f,
                        center = Offset(ex - eyeRadius * 0.2f, eyeY - eyeRadius * 0.2f)
                    )
                }

                else -> {
                    // Mắt to tròn long lanh (khi THINKING thì con ngươi liếc lên trên)
                    val lookUpY = if (mood == BotMood.THINKING) -eyeRadius * 0.28f else 0f
                    val lookSideX = if (mood == BotMood.THINKING) eyeRadius * 0.18f else 0f
                    drawCircle(color = Color.White, radius = eyeRadius, center = Offset(ex, eyeY))
                    drawCircle(
                        color = darkColor,
                        radius = eyeRadius,
                        center = Offset(ex, eyeY),
                        style = Stroke(width = strokeW * 0.75f)
                    )
                    drawCircle(
                        color = darkColor,
                        radius = eyeRadius * 0.62f,
                        center = Offset(ex + lookSideX, eyeY + lookUpY)
                    )
                    // Đốm sáng long lanh trong mắt
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.26f,
                        center = Offset(ex - eyeRadius * 0.22f + lookSideX, eyeY - eyeRadius * 0.24f + lookUpY)
                    )
                }
            }
        }

        // 2. Vẽ Miệng theo từng trạng thái cảm xúc
        val mouthY = cy + r * 0.14f
        when (mood) {
            BotMood.SURPRISED -> {
                // Miệng chữ O ngạc nhiên
                drawOval(
                    color = Color(0xFF991B1B),
                    topLeft = Offset(cx - r * 0.14f, mouthY - r * 0.06f),
                    size = Size(r * 0.28f, r * 0.30f)
                )
                drawOval(
                    color = darkColor,
                    topLeft = Offset(cx - r * 0.14f, mouthY - r * 0.06f),
                    size = Size(r * 0.28f, r * 0.30f),
                    style = Stroke(width = strokeW * 0.75f)
                )
            }

            BotMood.CRYING -> {
                // Miệng mếu máo khóc nhè
                drawArc(
                    color = darkColor,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.24f, mouthY + r * 0.02f),
                    size = Size(r * 0.48f, r * 0.26f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }

            BotMood.THINKING -> {
                // Miệng nghiêng suy nghĩ "hừm..."
                drawLine(
                    color = darkColor,
                    start = Offset(cx - r * 0.16f, mouthY + r * 0.08f),
                    end = Offset(cx + r * 0.18f, mouthY + r * 0.02f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            else -> {
                // Miệng cười tươi rói có chiếc răng nanh nhỏ đáng yêu
                val smilePath = Path().apply {
                    moveTo(cx - r * 0.26f, mouthY)
                    cubicTo(
                        cx - r * 0.16f, mouthY + r * 0.28f,
                        cx + r * 0.16f, mouthY + r * 0.28f,
                        cx + r * 0.26f, mouthY
                    )
                    close()
                }
                drawPath(smilePath, color = Color(0xFFEF4444), style = Fill)
                drawPath(smilePath, color = darkColor, style = Stroke(width = strokeW * 0.8f))

                // Chiếc răng nanh nhỏ trắng tinh
                val fang = Path().apply {
                    moveTo(cx + r * 0.06f, mouthY + r * 0.01f)
                    lineTo(cx + r * 0.13f, mouthY + r * 0.12f)
                    lineTo(cx + r * 0.19f, mouthY + r * 0.01f)
                    close()
                }
                drawPath(fang, color = toothColor, style = Fill)
            }
        }
    }

    private fun DrawScope.drawAnimatedArms(
        cx: Float,
        cy: Float,
        r: Float,
        mood: BotMood,
        actionPhase: Float,
        armColor: Color,
        outlineColor: Color,
        stroke: Stroke
    ) {
        val wave = sin(actionPhase * 2f * PI.toFloat())

        when (mood) {
            BotMood.THINKING -> {
                // Tay trái chống hờ trước ngực
                drawArmCapsule(
                    start = Offset(cx - r * 0.75f, cy + r * 0.12f),
                    end = Offset(cx - r * 0.35f, cy + r * 0.26f),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
                // Tay phải ĐƯA LÊN GÃI ĐẦU liên tục
                val scratchOffset = wave * r * 0.12f
                drawArmCapsule(
                    start = Offset(cx + r * 0.72f, cy - r * 0.05f),
                    end = Offset(cx + r * 0.42f + scratchOffset, cy - r * 0.82f),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
            }

            BotMood.CLAPPING -> {
                // Hai tay VỖ TAY nhịp nhàng trước ngực khen bé
                val clapGap = (abs(wave) * r * 0.28f) + r * 0.06f
                drawArmCapsule(
                    start = Offset(cx - r * 0.72f, cy + r * 0.15f),
                    end = Offset(cx - clapGap, cy + r * 0.12f),
                    radius = r * 0.16f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
                drawArmCapsule(
                    start = Offset(cx + r * 0.72f, cy + r * 0.15f),
                    end = Offset(cx + clapGap, cy + r * 0.12f),
                    radius = r * 0.16f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
                // Tia vỗ tay phát sáng ở giữa
                if (clapGap < r * 0.18f) {
                    for (angleDeg in intArrayOf(-60, -25, 25, 60)) {
                        val rad = angleDeg * PI.toFloat() / 180f
                        drawLine(
                            color = Color(0xFFFACC15),
                            start = Offset(cx + sin(rad) * r * 0.16f, cy + r * 0.04f - cos(rad) * r * 0.16f),
                            end = Offset(cx + sin(rad) * r * 0.32f, cy + r * 0.04f - cos(rad) * r * 0.32f),
                            strokeWidth = stroke.width * 0.85f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            BotMood.CRYING -> {
                // Hai tay đưa lên dụi mắt khóc nhè
                val rubY = wave * r * 0.06f
                drawArmCapsule(
                    start = Offset(cx - r * 0.76f, cy + r * 0.08f),
                    end = Offset(cx - r * 0.42f, cy - r * 0.22f + rubY),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
                drawArmCapsule(
                    start = Offset(cx + r * 0.76f, cy + r * 0.08f),
                    end = Offset(cx + r * 0.42f, cy - r * 0.22f - rubY),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
            }

            BotMood.SURPRISED -> {
                // Hai tay giơ cao hai bên má ngạc nhiên
                drawArmCapsule(
                    start = Offset(cx - r * 0.78f, cy + r * 0.05f),
                    end = Offset(cx - r * 0.88f, cy - r * 0.42f),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
                drawArmCapsule(
                    start = Offset(cx + r * 0.78f, cy + r * 0.05f),
                    end = Offset(cx + r * 0.88f, cy - r * 0.42f),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
            }

            else -> {
                // Tay trái vẫy chào bé liên tục
                val waveY = wave * r * 0.18f
                drawArmCapsule(
                    start = Offset(cx - r * 0.76f, cy + r * 0.10f),
                    end = Offset(cx - r * 1.02f, cy - r * 0.25f + waveY),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
                drawArmCapsule(
                    start = Offset(cx + r * 0.76f, cy + r * 0.12f),
                    end = Offset(cx + r * 0.92f, cy + r * 0.32f),
                    radius = r * 0.15f,
                    armColor = armColor,
                    outlineColor = outlineColor,
                    stroke = stroke
                )
            }
        }
    }

    private fun DrawScope.drawArmCapsule(
        start: Offset,
        end: Offset,
        radius: Float,
        armColor: Color,
        outlineColor: Color,
        stroke: Stroke
    ) {
        drawLine(
            color = outlineColor,
            start = start,
            end = end,
            strokeWidth = radius * 2.1f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = armColor,
            start = start,
            end = end,
            strokeWidth = radius * 1.65f,
            cap = StrokeCap.Round
        )
    }

    private fun DrawScope.drawMoodEffects(
        cx: Float,
        cy: Float,
        r: Float,
        mood: BotMood,
        actionPhase: Float
    ) {
        val floatOffset = sin(actionPhase * 2f * PI.toFloat()) * r * 0.08f
        when (mood) {
            BotMood.THINKING -> {
                // Bong bóng suy nghĩ chuyển động mượt mà lơ lửng trên đầu
                val bubbles = listOf(
                    Triple(Offset(cx - r * 0.58f, cy - r * 0.85f + floatOffset * 0.6f), r * 0.08f, Color.White.copy(alpha = 0.88f)),
                    Triple(Offset(cx - r * 0.76f, cy - r * 1.04f + floatOffset * 0.9f), r * 0.13f, Color.White.copy(alpha = 0.92f)),
                    Triple(Offset(cx - r * 0.96f, cy - r * 1.25f + floatOffset), r * 0.20f, Color(0xFFFDE047))
                )
                for ((center, radius, color) in bubbles) {
                    drawCircle(color = color, radius = radius, center = center)
                    drawCircle(color = Color(0xFF0F172A), radius = radius, center = center, style = Stroke(width = r * 0.03f))
                    // Đốm sáng phản chiếu 3D trên bong bóng
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = radius * 0.28f,
                        center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f)
                    )
                }
            }

            BotMood.CLAPPING, BotMood.PROUD_LAUGH -> {
                // Ngôi sao 5 cánh lấp lánh và Nốt nhạc vui tươi bay lên
                val starColor = Color(0xFFFDE047)
                val starRot = actionPhase * 360f

                // Ngôi sao bên trái
                drawStar(
                    center = Offset(cx - r * 0.92f, cy - r * 0.78f + floatOffset),
                    outerR = r * 0.14f,
                    innerR = r * 0.06f,
                    color = starColor,
                    rotationAngle = starRot
                )
                // Ngôi sao bên phải
                drawStar(
                    center = Offset(cx + r * 0.92f, cy - r * 0.82f - floatOffset),
                    outerR = r * 0.16f,
                    innerR = r * 0.07f,
                    color = Color(0xFFF59E0B),
                    rotationAngle = -starRot
                )

                // Nốt nhạc vui tươi bay lên (♪ và ♫)
                drawMusicalNote(
                    center = Offset(cx - r * 0.40f, cy - r * 1.15f + floatOffset * 1.2f),
                    size = r * 0.20f,
                    color = Color(0xFF38BDF8)
                )
                drawMusicalNote(
                    center = Offset(cx + r * 0.52f, cy - r * 1.18f - floatOffset * 1.2f),
                    size = r * 0.22f,
                    color = Color(0xFFEC4899)
                )
            }

            BotMood.SURPRISED -> {
                // Tia chớp cảm thán trên đầu
                val alertColor = Color(0xFFFACC15)
                drawLine(
                    color = alertColor,
                    start = Offset(cx - r * 0.92f, cy - r * 0.85f),
                    end = Offset(cx - r * 0.74f, cy - r * 0.68f),
                    strokeWidth = r * 0.08f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = alertColor,
                    start = Offset(cx + r * 0.92f, cy - r * 0.85f),
                    end = Offset(cx + r * 0.74f, cy - r * 0.68f),
                    strokeWidth = r * 0.08f,
                    cap = StrokeCap.Round
                )
            }

            else -> {}
        }
    }

    private fun DrawScope.drawStar(
        center: Offset,
        outerR: Float,
        innerR: Float,
        color: Color,
        rotationAngle: Float
    ) {
        val path = Path()
        val angleStep = (PI / 5.0).toFloat()
        val startAngle = (rotationAngle * PI / 180f).toFloat() - (PI / 2.0).toFloat()

        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outerR else innerR
            val a = startAngle + i * angleStep
            val x = center.x + cos(a) * radius
            val y = center.y + sin(a) * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = color, style = Fill)
        drawPath(path, color = Color(0xFF0F172A), style = Stroke(width = outerR * 0.18f, join = StrokeJoin.Round))
    }

    private fun DrawScope.drawMusicalNote(
        center: Offset,
        size: Float,
        color: Color
    ) {
        // Nốt nhạc đơn: Oval đầu nốt + Thân nốt thẳng + Cờ nốt cong
        val headRadius = size * 0.32f
        drawOval(
            color = color,
            topLeft = Offset(center.x - headRadius * 1.1f, center.y + size * 0.1f),
            size = Size(headRadius * 2.2f, headRadius * 1.6f)
        )
        // Thân nốt
        val stemX = center.x + headRadius * 0.9f
        drawLine(
            color = color,
            start = Offset(stemX, center.y + size * 0.6f),
            end = Offset(stemX, center.y - size * 0.8f),
            strokeWidth = size * 0.18f,
            cap = StrokeCap.Round
        )
        // Cờ nốt cong
        val flagPath = Path().apply {
            moveTo(stemX, center.y - size * 0.8f)
            cubicTo(
                stemX + size * 0.5f, center.y - size * 0.7f,
                stemX + size * 0.6f, center.y - size * 0.2f,
                stemX + size * 0.3f, center.y - size * 0.1f
            )
        }
        drawPath(flagPath, color = color, style = Stroke(width = size * 0.18f, cap = StrokeCap.Round))
    }

    private fun abs(v: Float): Float = if (v < 0f) -v else v
}
