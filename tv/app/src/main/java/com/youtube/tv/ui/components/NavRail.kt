package com.youtube.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.youtube.tv.ui.theme.YtDim
import com.youtube.tv.ui.theme.YtHover
import com.youtube.tv.ui.theme.YtText

enum class NavDest(val label: String) {
    // Thu tu giong app YouTube tren TV: Tim kiem tren cung, roi Trang chu
    Search("Tìm kiếm"),
    Home("Trang chủ"),
    Connect("Kết nối"),
}

private val RAIL_COLLAPSED = 92.dp
private val RAIL_EXPANDED = 240.dp

/**
 * Thanh dieu huong ben trai. Gap lai thanh cot icon, tu mo rong ra kem nhan chu
 * khi nhan focus, va do bong sang phai — dung cach app YouTube tren TV lam.
 */
@Composable
fun NavRail(
    current: NavDest,
    onSelect: (NavDest) -> Unit,
    modifier: Modifier = Modifier,
    /** Hien dau nhac o muc Ket noi khi chua ghep voi kho nao. */
    highlightConnect: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        if (expanded) RAIL_EXPANDED else RAIL_COLLAPSED,
        label = "railWidth",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(
                // Khi mo rong: nen toi dan sang phai de tach khoi cac hang video
                if (expanded) {
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.Black.copy(alpha = 0.92f), Color.Transparent),
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .focusGroup()
            .padding(vertical = 30.dp, horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Wordmark(
            modifier = Modifier.padding(start = 6.dp, bottom = 26.dp),
            showText = expanded,
        )

        NavDest.entries.forEach { d ->
            RailItem(
                dest = d,
                selected = d == current,
                expanded = expanded,
                dot = d == NavDest.Connect && highlightConnect,
                onFocused = { expanded = true },
                onBlurred = { expanded = false },
                onClick = { onSelect(d) },
            )
        }
    }
}

@Composable
private fun RailItem(
    dest: NavDest,
    selected: Boolean,
    expanded: Boolean,
    dot: Boolean,
    onFocused: () -> Unit,
    onBlurred: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(46.dp)
            .then(if (expanded) Modifier.width(RAIL_EXPANDED - 44.dp) else Modifier.width(46.dp))
            // Theo focus tung muc, khong theo Column: hasFocus cua Column van true
            // khi con tro da sang hang video, lam rail ket o trang thai mo rong.
            .onFocusChanged { if (it.isFocused) onFocused() else onBlurred() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(23.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) YtHover else Color.Transparent,
            contentColor = if (selected) YtText else YtDim,
            focusedContainerColor = YtText,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (dest) {
                NavDest.Search -> Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = dest.label,
                    modifier = Modifier.size(21.dp),
                )

                NavDest.Home -> Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = dest.label,
                    modifier = Modifier.size(21.dp),
                )

                // Tu ve: material-icons-core khong co icon Link/Cast
                NavDest.Connect -> CastIcon(
                    size = 21.dp,
                    tint = androidx.tv.material3.LocalContentColor.current,
                )
            }

            if (dot) {
                Spacer(Modifier.width(5.dp))
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = com.youtube.tv.ui.theme.YtRed,
                    ),
                ) {}
            }

            if (expanded) {
                Spacer(Modifier.width(if (dot) 10.dp else 15.dp))
                Text(
                    text = dest.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
