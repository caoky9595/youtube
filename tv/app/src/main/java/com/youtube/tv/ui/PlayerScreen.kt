package com.youtube.tv.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.youtube.tv.data.Video
import com.youtube.tv.ui.components.VideoCard
import com.youtube.tv.ui.theme.YtDim
import com.youtube.tv.ui.theme.YtPanel
import com.youtube.tv.ui.theme.YtRed
import com.youtube.tv.ui.theme.YtText
import kotlinx.coroutines.delay

private const val SEEK_STEP_SECONDS = 10f
private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val COVER_LINGER_MS = 900L

/** Con lai bao nhieu giay thi tu hien the "Sap phat" o goc man hinh. */
private const val UP_NEXT_PREVIEW_SECONDS = 8

/**
 * Trinh phat dung IFrame Player API cua YouTube trong WebView — day la cach
 * Google khuyen dung va la cach duy nhat hop dieu khoan de phat video YouTube
 * trong app rieng.
 *
 * Web UI cua IFrame bi tat (controls = 0) va WebView bi chan nhan focus, nen
 * toan bo dieu khien di qua lop overlay Compose ben tren, nhan phim tu remote.
 */
@Composable
fun PlayerScreen(
    video: Video,
    startSeconds: Float,
    hasNext: Boolean,
    onEnded: () -> Unit,
    onProgress: (seconds: Int, duration: Int?) -> Unit,
    /** Trinh phat biet thoi luong that; dung de dien vao kho neu con trong. */
    onDuration: (seconds: Int) -> Unit,
    onBack: () -> Unit,
    /** Nguyen ca hang dang phat, de hien bang "Video gợi ý". Rong hoac 1 phan
     * tu thi khong co gi de goi y, bang se khong mo duoc. */
    queue: List<Video> = emptyList(),
    queueIndex: Int = 0,
    /** Chon mot video khac trong queue tu bang goi y. */
    onJumpTo: (index: Int) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var currentSeconds by remember { mutableIntStateOf(startSeconds.toInt()) }
    var durationSeconds by remember { mutableIntStateOf(video.durationSeconds ?: 0) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Bang "Video gợi ý": chi co gi de mo khi hang dang phat co video khac
    // ngoai video hien tai.
    val canSuggest = queue.size > 1
    var suggestionsVisible by remember { mutableStateOf(false) }

    /**
     * Che kin khung video khi chua phat. IFrame player tu ve overlay rieng
     * (tieu de that, avatar kenh, grid "More videos") va overlay do mat gan mot
     * giay moi mo hoan toan — nen phai giu man che them mot lat sau khi phat
     * lai, khong thi grid do loe ra.
     */
    var covered by remember { mutableStateOf(true) }
    LaunchedEffect(playing, buffering, errorText) {
        if (!playing || buffering) {
            covered = true
        } else {
            delay(COVER_LINGER_MS)
            covered = false
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Tu an dieu khien sau mot lat khong bam gi — nhung chi khi dang phat.
    // Dang tam dung thi giu dieu khien tren man hinh.
    LaunchedEffect(controlsVisible, interactionTick, playing) {
        if (controlsVisible && playing) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    /**
     * Nap video moi vao DUNG player dang co san khi chuyen video (goi y, tu
     * phat tiep, "Trước/Sau" bang media key) — khong phai video dau tien.
     *
     * AndroidView.factory ben duoi chi chay MOT LAN duy nhat luc tao WebView,
     * va loadVideo() trong onReady() cung chi goi mot lan cho DUNG lan khoi
     * tao do. Thieu dong nay thi doi video se doi duoc tieu de/thumbnail tren
     * Controls (do la tham so video duoc ve lai binh thuong) nhung WebView vAn
     * dung nguyen video cu — nhin nhu bam gi cung khong an thua.
     */
    LaunchedEffect(video.id) {
        // KHONG tu gan currentSeconds/durationSeconds ve video moi o day: no
        // co the chay TRUOC DisposableEffect ben duoi luu tien do video CU (do
        // LaunchedEffect la coroutine, chay sau khi ap dung thay doi, nhung
        // thu tu chinh xac giua hai loai effect nay khong dang de danh cuoc voi
        // du lieu xem cua nguoi dung). De callback onCurrentSecond/
        // onVideoDuration cua player tu cap nhat, cham hon chua toi nua giay
        // nhung chac chan dung.
        player?.loadVideo(video.youtubeId, startSeconds)
    }

    // Luu tien do dinh ky, khong luu moi giay de do goi mang
    LaunchedEffect(video.id) {
        while (true) {
            delay(10_000)
            if (currentSeconds > 0) {
                onProgress(currentSeconds, durationSeconds.takeIf { it > 0 })
            }
        }
    }

    fun touch() {
        controlsVisible = true
        interactionTick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val p = player

                // Bang goi y dang mo: Trai/Phai/OK nhuong cho he thong focus tu
                // nhien xu ly (giong moi luoi the khac trong app, khong tu bat
                // o day) — chi bat rieng Len/Xuong de dong bang lai.
                if (suggestionsVisible) {
                    return@onKeyEvent when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            suggestionsVisible = false
                            touch() // khong thi thanh dieu khien co the van dang an tu truoc do
                            true
                        }
                        else -> false
                    }
                }

                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Spacebar -> {
                        if (playing) p?.pause() else p?.play()
                        touch()
                        true
                    }

                    Key.MediaPlay -> { p?.play(); touch(); true }
                    Key.MediaPause -> { p?.pause(); touch(); true }

                    Key.DirectionLeft, Key.MediaRewind -> {
                        p?.seekTo((currentSeconds - SEEK_STEP_SECONDS).coerceAtLeast(0f))
                        touch()
                        true
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        val cap = if (durationSeconds > 0) durationSeconds.toFloat() else Float.MAX_VALUE
                        p?.seekTo((currentSeconds + SEEK_STEP_SECONDS).coerceAtMost(cap))
                        touch()
                        true
                    }

                    Key.MediaNext -> { if (hasNext) onEnded(); true }
                    Key.MediaPrevious -> {
                        if (queueIndex > 0) onJumpTo(queueIndex - 1)
                        true
                    }

                    // Xuong: mo bang "Video gợi ý" liet ke ca hang dang phat, de
                    // chon video khac ma khong phai Quay lai roi do lai tu Trang
                    // chu. Remote D-pad thuong khong co phim "Next" rieng, day
                    // la duong thay the luon co san.
                    Key.DirectionDown -> {
                        if (canSuggest) {
                            suggestionsVisible = true
                            touch() // dam bao khi dong bang lai thi thanh dieu khien cung hien theo
                        } else {
                            touch()
                        }
                        true
                    }

                    Key.DirectionUp, Key.Info -> { touch(); true }

                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                YouTubePlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Neu khong chan, WebView se an het phim D-pad va overlay
                    // Compose khong bao gio nhan duoc su kien nao
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false

                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)

                    val options = IFramePlayerOptions.Builder(context)
                        .controls(0)   // tat UI web, dung overlay Compose thay the
                        .rel(0)
                        .ivLoadPolicy(3)
                        .ccLoadPolicy(0)
                        .build()

                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                player = youTubePlayer
                                youTubePlayer.loadVideo(video.youtubeId, startSeconds)
                            }

                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState,
                            ) {
                                buffering = state == PlayerConstants.PlayerState.BUFFERING
                                playing = state == PlayerConstants.PlayerState.PLAYING
                                if (state == PlayerConstants.PlayerState.ENDED) onEnded()
                            }

                            override fun onCurrentSecond(
                                youTubePlayer: YouTubePlayer,
                                second: Float,
                            ) {
                                currentSeconds = second.toInt()
                            }

                            override fun onVideoDuration(
                                youTubePlayer: YouTubePlayer,
                                duration: Float,
                            ) {
                                if (duration > 0) {
                                    durationSeconds = duration.toInt()
                                    onDuration(duration.toInt())
                                }
                            }

                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: PlayerConstants.PlayerError,
                            ) {
                                errorText = when (error) {
                                    PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
                                        "Video không còn tồn tại trên YouTube."
                                    PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST,
                                    PlayerConstants.PlayerError.HTML_5_PLAYER ->
                                        "Chủ kênh không cho phép phát video này ngoài YouTube."
                                    else -> "Không phát được video ($error)."
                                }
                            }
                        },
                        handleNetworkEvents = true,
                        playerOptions = options,
                    )
                }
            },
            onRelease = { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            },
        )

        // Luu tien do lan cuoi khi roi khoi trinh phat
        DisposableEffect(video.id) {
            onDispose {
                if (currentSeconds > 0) {
                    onProgress(currentSeconds, durationSeconds.takeIf { it > 0 })
                }
            }
        }

        errorText?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message, color = YtText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bấm Quay lại để chọn video khác.",
                        color = YtDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = covered && errorText == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Den dac, khong phai mo: alpha 0.97 van de lo tieu de va
                    // avatar kenh cua YouTube o phia sau.
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (buffering) "⋯" else "❚❚",
                        color = YtText,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = video.title,
                        color = YtText,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 18.dp, start = 64.dp, end = 64.dp),
                    )
                    Text(
                        text = if (buffering) "Đang tải…" else "Đã tạm dừng — bấm OK để phát tiếp",
                        color = YtDim,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !suggestionsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Controls(
                video = video,
                playing = playing,
                currentSeconds = currentSeconds,
                durationSeconds = durationSeconds,
                hasNext = hasNext,
                canSuggest = canSuggest,
            )
        }

        // Sap het video: tu hien truoc video ke tiep se phat, giong app YouTube
        // that. Chi xem, khong can bam gi — video van tu chay tiep nhu binh
        // thuong qua onEnded() o MainActivity.
        val remaining = durationSeconds - currentSeconds
        AnimatedVisibility(
            visible = hasNext && !suggestionsVisible && durationSeconds > 0 &&
                remaining in 1..UP_NEXT_PREVIEW_SECONDS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = if (controlsVisible) 150.dp else 32.dp),
        ) {
            queue.getOrNull(queueIndex + 1)?.let { next ->
                UpNextPreview(next, secondsLeft = remaining)
            }
        }

        AnimatedVisibility(
            visible = suggestionsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SuggestedPanel(
                queue = queue,
                currentIndex = queueIndex,
                onSelect = { index ->
                    suggestionsVisible = false
                    touch()
                    onJumpTo(index)
                },
            )
        }
    }

    // Nut Back cua remote: dong bang goi y neu dang mo, khong thi moi thoat han
    androidx.activity.compose.BackHandler {
        if (suggestionsVisible) {
            suggestionsVisible = false
            touch()
        } else {
            onBack()
        }
    }
}

@Composable
private fun Controls(
    video: Video,
    playing: Boolean,
    currentSeconds: Int,
    durationSeconds: Int,
    hasNext: Boolean,
    canSuggest: Boolean,
) {
    val fraction =
        if (durationSeconds > 0) currentSeconds.toFloat() / durationSeconds else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 48.dp, vertical = 26.dp),
    ) {
        Text(
            text = video.title,
            color = YtText,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        video.channelTitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = YtDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(YtRed, RoundedCornerShape(3.dp)),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${clock(currentSeconds)} / ${clock(durationSeconds)}",
                color = YtText,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint(if (playing) "OK: tạm dừng" else "OK: phát")
                Spacer(Modifier.width(18.dp))
                Hint("◀ ▶: tua 10 giây")
                if (canSuggest) {
                    Spacer(Modifier.width(18.dp))
                    Hint("▼: video gợi ý")
                }
                if (hasNext) {
                    Spacer(Modifier.width(18.dp))
                    Hint("Hết video: tự phát tiếp")
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, color = YtDim, style = MaterialTheme.typography.labelMedium)
}

/** The nho o goc man hinh, xem truoc video se tu phat tiep khi video nay het. */
@Composable
private fun UpNextPreview(next: Video, secondsLeft: Int) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            text = "Sắp phát sau ${secondsLeft}s",
            color = YtDim,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            coil3.compose.AsyncImage(
                model = next.thumbnail,
                contentDescription = next.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .background(YtPanel, RoundedCornerShape(4.dp)),
            )
            Text(
                text = next.title,
                color = YtText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/**
 * Bang chon video khac trong cung hang dang phat. Xep bat dau tu video KE
 * TIEP roi vong lai tu dau, khong lap lai video dang phat — de o dau danh sach
 * (va duoc focus san) luon la thao tac nguoi dung can nhat luc nay.
 */
@Composable
private fun SuggestedPanel(
    queue: List<Video>,
    currentIndex: Int,
    onSelect: (index: Int) -> Unit,
) {
    val order = remember(queue, currentIndex) {
        val n = queue.size
        (1 until n).map { (currentIndex + it) % n }
    }
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80) // cho LazyRow dung khung truoc khi xin focus
        runCatching { firstItem.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = "Video gợi ý",
            color = YtText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(order, key = { _, originalIndex -> queue[originalIndex].id }) { i, originalIndex ->
                VideoCard(
                    video = queue[originalIndex],
                    onClick = { onSelect(originalIndex) },
                    modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                )
            }
        }
        Text(
            text = "◀ ▶ chọn · OK phát · ▲ hoặc Quay lại đóng",
            color = YtDim,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 48.dp, top = 14.dp),
        )
    }
}

private fun clock(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
