package com.youtube.tv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.youtube.tv.data.HomeData
import com.youtube.tv.data.Shelf
import com.youtube.tv.data.Video
import com.youtube.tv.ui.components.CenteredMessage
import com.youtube.tv.ui.components.VideoCard
import com.youtube.tv.ui.theme.YtText

@Composable
fun HomeScreen(
    data: HomeData,
    progress: Map<String, Int>,
    onPlay: (shelf: Shelf, index: Int) -> Unit,
) {
    if (data.shelves.isEmpty()) {
        CenteredMessage(
            title = "Chưa có video nào",
            detail = "Mở trang quản trị YouTube, thêm vài video rồi quay lại đây và bấm nút quay lại " +
                "để tải lại danh sách.",
        )
        return
    }

    val firstRow = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Tren TV luon phai co san mot o dang focus, khong thi remote khong dieu khien duoc
        runCatching { firstRow.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 26.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(data.shelves, key = { _, s -> s.id }) { index, shelf ->
            ShelfRow(
                shelf = shelf,
                progress = progress,
                onPlay = { i -> onPlay(shelf, i) },
                modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShelfRow(
    shelf: Shelf,
    progress: Map<String, Int>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // App YouTube tren TV dung tieu de hang khiem ton: ~19sp, medium.
        // titleLarge (22sp) lam hang video bi tieu de lan at.
        Text(
            text = shelf.title,
            color = YtText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            // focusRestorer: quay lai hang nay thi tro ve dung the vua roi,
            // khong nhay ve dau hang
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(shelf.videos, key = { _, v -> v.id }) { i, video ->
                VideoCard(
                    video = video,
                    onClick = { onPlay(i) },
                    progressFraction = progressFraction(video, progress),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoGrid(
    videos: List<Video>,
    progress: Map<String, Int>,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Luoi tren TV kho dieu huong hon hang ngang, nen chia thanh nhieu hang 4 the
    val rows = remember(videos) { videos.chunked(4) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(rows.size) { rowIndex ->
            LazyRow(
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(rows[rowIndex], key = { _, v -> v.id }) { i, video ->
                    VideoCard(
                        video = video,
                        onClick = { onPlay(rowIndex * 4 + i) },
                        progressFraction = progressFraction(video, progress),
                    )
                }
            }
        }
    }
}

private fun progressFraction(video: Video, progress: Map<String, Int>): Float? {
    val seconds = progress[video.id] ?: return null
    val total = video.durationSeconds?.takeIf { it > 0 } ?: return null
    return seconds.toFloat() / total
}
