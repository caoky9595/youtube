package com.youtube.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.youtube.tv.ui.theme.YtRed
import com.youtube.tv.ui.theme.YtText

/**
 * Badge phat kieu YouTube: hinh chu nhat bo goc ti le ~1.42:1 voi tam giac
 * trang o giua. Ve bang Canvas de net o moi mat do man hinh, thay vi keo theo
 * mot file anh cho tung dpi.
 */
@Composable
fun PlayBadge(height: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = height * 1.42f, height = height)) {
        val w = size.width
        val h = size.height

        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(Offset.Zero, Size(w, h)),
                        cornerRadius = CornerRadius(h * 0.28f),
                    ),
                )
            },
            color = YtRed,
        )

        // Tam giac trang, chieu cao ~44% badge, can giua quang hoc (hoi lech phai)
        val th = h * 0.44f
        val tw = th * 0.86f
        val cx = w / 2f + tw * 0.08f
        val cy = h / 2f
        drawPath(
            path = Path().apply {
                moveTo(cx - tw / 2f, cy - th / 2f)
                lineTo(cx - tw / 2f, cy + th / 2f)
                lineTo(cx + tw / 2f, cy)
                close()
            },
            color = Color.White,
        )
    }
}

/** Badge + wordmark, dat o dinh thanh dieu huong. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, showText: Boolean = true) {
    Row(modifier = modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayBadge(height = 22.dp)
        if (showText) {
            Spacer(Modifier.width(7.dp))
            Text(
                text = "YouTube",
                color = YtText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
            )
        }
    }
}

/**
 * Icon "ket noi" kieu bieu tuong cast: mot khung man hinh voi hai cung song.
 * Tu ve vi material-icons-core khong co Link/Cast (bo core chi co ~48 icon).
 */
@Composable
fun CastIcon(size: Dp = 21.dp, tint: Color = YtText, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.09f
        val cap = androidx.compose.ui.graphics.StrokeCap.Round

        // Khung man hinh: ve 3 canh, chua goc duoi-trai cho song di ra
        val left = s * 0.16f
        val top = s * 0.22f
        val right = s * 0.90f
        val bottom = s * 0.78f
        drawLine(tint, Offset(left, top), Offset(right, top), stroke, cap)
        drawLine(tint, Offset(right, top), Offset(right, bottom), stroke, cap)
        drawLine(tint, Offset(right, bottom), Offset(s * 0.52f, bottom), stroke, cap)

        // Hai cung song + diem phat, o goc duoi-trai
        val originX = left * 0.9f
        val originY = bottom
        drawCircle(tint, radius = stroke * 0.75f, center = Offset(originX, originY))
        for (factor in listOf(0.30f, 0.52f)) {
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(originX - s * factor, originY - s * factor),
                size = Size(s * factor * 2f, s * factor * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = cap),
            )
        }
    }
}
