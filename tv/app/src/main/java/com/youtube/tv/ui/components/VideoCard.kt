package com.youtube.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.youtube.tv.data.Video
import com.youtube.tv.ui.theme.YtDim
import com.youtube.tv.ui.theme.YtPanel
import com.youtube.tv.ui.theme.YtRed
import com.youtube.tv.ui.theme.YtText

/**
 * Chieu rong the video. Man TV 1080p rong 960dp (1920px / density 2.0); tru le
 * 48dp hai ben va khe 18dp thi 200dp cho ra ~4 the mot hang — dung mat do cua
 * app YouTube tren TV. De 300dp thi chi vua 2.8 the, nhin qua thua.
 */
val CardWidth = 200.dp

/** App YouTube tren TV bo goc the rat nhe; 12dp nhin tron qua so voi ban goc. */
private val CardRadius = 6.dp

@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressFraction: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(CardWidth)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(CardRadius)),
            // Phong to nhe khi focus — dau hieu vi tri con tro quen thuoc tren TV
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = YtPanel,
                focusedContainerColor = YtPanel,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(3.dp, YtText),
                    shape = RoundedCornerShape(CardRadius),
                ),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                video.durationLabel?.let { label ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                            containerColor = Color.Black.copy(alpha = 0.82f),
                        ),
                    ) {
                        Text(
                            text = label,
                            color = YtText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                // Thanh do dai da xem, giong YouTube
                if (progressFraction != null && progressFraction > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.28f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(YtRed),
                        )
                    }
                }
            }
        }

        Text(
            text = video.title,
            color = if (focused) YtText else YtText.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        video.channelTitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = YtDim,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
