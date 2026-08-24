package com.youtube.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val id: String,
    @SerialName("youtube_id") val youtubeId: String,
    val title: String,
    @SerialName("channel_title") val channelTitle: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    /** null khi vua them bang duong dan; dien vao sau lan phat dau tien. */
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
) {
    /** Thumbnail suy ra tu ID neu admin chua co anh — tranh o trong tren TV. */
    val thumbnail: String
        get() = thumbnailUrl ?: "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"

    val durationLabel: String?
        get() = durationSeconds?.takeIf { it > 0 }?.let { total ->
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
}

@Serializable
data class Shelf(
    val id: String,
    val title: String,
    val videos: List<Video> = emptyList(),
)

@Serializable
data class HomeData(
    val shelves: List<Shelf> = emptyList(),
)

/** Trang thai cua dung mot ma ghep (pair_code_status). */
@Serializable
data class CodeStatus(
    val exists: Boolean = false,
    val claimed: Boolean = false,
    val expired: Boolean = true,
)

/** Ket qua pair_request / pair_poll. */
@Serializable
data class PairState(
    val paired: Boolean = false,
    val code: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    /**
     * So giay con lai, do SERVER tinh. Dung cai nay chu dung tu lay expiresAt
     * tru gio cua may: dong ho TV lech la chuyen thuong.
     */
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("tv_token") val tvToken: String? = null,
    @SerialName("unknown_device") val unknownDevice: Boolean = false,
)

@Serializable
data class ProgressRow(
    @SerialName("video_id") val videoId: String,
    @SerialName("position_seconds") val positionSeconds: Int,
)
