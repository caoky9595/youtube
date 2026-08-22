package com.youtube.tv.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.youtube.tv.ui.theme.YtDim
import com.youtube.tv.ui.theme.YtRed
import com.youtube.tv.ui.theme.YtText
import kotlinx.coroutines.delay

private const val SEEK_STEP_SECONDS = 10f
private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val COVER_LINGER_MS = 900L

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

                    Key.DirectionUp, Key.DirectionDown, Key.Info -> { touch(); true }

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
            visible = controlsVisible,
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
            )
        }
    }

    // Nut Back cua remote: thoat trinh phat
    androidx.activity.compose.BackHandler { onBack() }
}

@Composable
private fun Controls(
    video: Video,
    playing: Boolean,
    currentSeconds: Int,
    durationSeconds: Int,
    hasNext: Boolean,
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

private fun clock(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
