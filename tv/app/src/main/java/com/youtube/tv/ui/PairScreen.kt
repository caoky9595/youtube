package com.youtube.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.youtube.tv.ui.components.CenteredMessage
import com.youtube.tv.ui.theme.YtDim
import com.youtube.tv.ui.theme.YtHover
import com.youtube.tv.ui.theme.YtText
import com.youtube.tv.vm.PairPhase

/**
 * Man hinh Ket noi.
 *
 * Chua ghep  -> hien ma 6 ky tu de nhap ben trang quan tri.
 * Da ghep    -> hien trang thai, kem nut xin ma cho MOT MAY QUAN TRI NUA
 *               (dien thoai thu hai, hoac trinh duyet vua xoa du lieu). Khong co
 *               duong nay thi may dau ghep xong la cac may khac bi khoa ngoai.
 *
 * Ma tu het han sau 15 phut; ViewModel xin ma moi khi het, nen man hinh nay de
 * mo bao lau cung duoc.
 */
@Composable
fun PairScreen(
    phase: PairPhase,
    adminUrlHint: String?,
    onRequestAdminCode: () -> Unit,
    onRetry: () -> Unit,
) {
    when (phase) {
        PairPhase.Checking, PairPhase.Requesting ->
            CenteredMessage(title = "Đang lấy mã kết nối…")

        is PairPhase.Failed -> CenteredMessage(
            title = "Chưa kết nối",
            detail = phase.message,
            actionLabel = if (phase.canRetry) "Thử lại" else null,
            onAction = if (phase.canRetry) onRetry else null,
        )

        PairPhase.Paired -> Connected(adminUrlHint, onRequestAdminCode)

        is PairPhase.ShowingCode -> CodeBoard(phase, adminUrlHint)
    }
}

@Composable
private fun Connected(adminUrlHint: String?, onRequestAdminCode: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Đã kết nối",
            color = YtText,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = adminUrlHint?.let { "TV này đang dùng kho video quản lý ở $it." }
                ?: "TV này đã được ghép với một kho video.",
            color = YtDim,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .widthIn(max = 760.dp),
        )

        Spacer(Modifier.height(36.dp))

        Button(onClick = onRequestAdminCode, modifier = Modifier.focusRequester(focus)) {
            Text("Lấy mã để thêm máy quản trị")
        }

        Text(
            text = "Dùng khi muốn quản lý từ một điện thoại hoặc máy tính khác.",
            color = YtDim.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .widthIn(max = 700.dp),
        )
    }
}

@Composable
private fun CodeBoard(phase: PairPhase.ShowingCode, adminUrlHint: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (phase.forAdmin) "Thêm máy quản trị" else "Kết nối với trang quản trị",
            color = YtText,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = adminUrlHint?.let { "Mở $it trên điện thoại hoặc máy tính, rồi nhập mã dưới đây" }
                ?: "Mở trang quản trị rồi nhập mã dưới đây",
            color = YtDim,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .widthIn(max = 760.dp),
        )

        Spacer(Modifier.height(40.dp))

        // Tach tung ky tu ra o rieng cho de doc tu xa
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            phase.code.forEach { ch ->
                Column(
                    modifier = Modifier
                        .background(YtHover, RoundedCornerShape(12.dp))
                        .width(84.dp)
                        .height(112.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = ch.toString(),
                        color = YtText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = if (phase.secondsLeft > 0) {
                "Mã đổi mới sau ${mmss(phase.secondsLeft)}"
            } else {
                "Đang đổi mã mới…"
            },
            color = YtDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (phase.forAdmin) {
                "Nhập xong là máy đó quản lý được kho này ngay."
            } else {
                "Nhập xong là TV tự chuyển sang trang chủ, không cần bấm gì thêm."
            },
            color = YtDim.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Mot ma dung duoc cho nhieu may quan tri (dien thoai va may tinh) cho
        // den khi het han, nen sau may dau tien van giu ma tren man hinh.
        if (phase.adminsAdded > 0) {
            Text(
                text = "Đã kết nối ${phase.adminsAdded} máy quản trị. " +
                    "Vẫn nhập được mã này trên máy khác cho tới khi đổi mã.",
                color = YtText,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 700.dp),
            )
        }
    }
}

private fun mmss(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
