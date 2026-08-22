package com.youtube.tv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.youtube.tv.ui.components.CenteredMessage
import com.youtube.tv.ui.theme.YtDim
import com.youtube.tv.ui.theme.YtPanel
import com.youtube.tv.ui.theme.YtText
import com.youtube.tv.vm.SearchState

@Composable
fun SearchScreen(
    state: SearchState,
    progress: Map<String, Int>,
    onQueryChange: (String) -> Unit,
    onPlay: (List<com.youtube.tv.data.Video>, Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                colors = SurfaceDefaults.colors(containerColor = YtPanel),
                modifier = Modifier.width(660.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Text("⌕", color = YtDim, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(14.dp))
                    // BasicTextField: ban phim mem cua Android TV se tu hien khi o nay
                    // nhan focus, nen khong can tu ve ban phim tren man hinh
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onQueryChange(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = YtText, fontSize = 20.sp),
                        cursorBrush = SolidColor(YtText),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            // O text an phim ◀ de di chuyen con tro chu, nen neu
                            // khong chan thi khong ra duoc nav rail. Tren TV
                            // khong ai sua chu bang D-pad, nen ◀ tra focus ve rail.
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionLeft
                                ) {
                                    focusManager.moveFocus(FocusDirection.Left)
                                    true
                                } else {
                                    false
                                }
                            },
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Tìm video trong YouTube…",
                                    color = YtDim,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
        }

        when (state) {
            SearchState.Idle -> CenteredMessage(
                title = "Nhập tên video cần tìm",
                detail = "Chỉ tìm trong các video đã được thêm vào YouTube. Không phân biệt dấu và " +
                    "chữ hoa — gõ “bai hat” vẫn tìm ra “Bài Hát”.",
            )

            SearchState.Loading -> CenteredMessage(title = "Đang tìm…")

            is SearchState.Error -> CenteredMessage(
                title = "Không tìm được",
                detail = state.message,
            )

            is SearchState.Ready ->
                if (state.results.isEmpty()) {
                    CenteredMessage(
                        title = "Không có kết quả cho “${state.query}”",
                        detail = "Thử từ khoá ngắn hơn, hoặc thêm video này vào YouTube ở trang quản trị.",
                    )
                } else {
                    Column {
                        Text(
                            text = "${state.results.size} kết quả",
                            color = YtDim,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                        )
                        VideoGrid(
                            videos = state.results,
                            progress = progress,
                            onPlay = { i -> onPlay(state.results, i) },
                        )
                    }
                }
        }
    }
}
