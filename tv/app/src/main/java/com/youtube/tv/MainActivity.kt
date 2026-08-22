package com.youtube.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtube.tv.data.Video
import com.youtube.tv.data.YouTubeApi
import com.youtube.tv.ui.HomeScreen
import com.youtube.tv.ui.PairScreen
import com.youtube.tv.ui.PlayerScreen
import com.youtube.tv.ui.SearchScreen
import com.youtube.tv.ui.components.CenteredMessage
import com.youtube.tv.ui.components.NavDest
import com.youtube.tv.ui.components.NavRail
import com.youtube.tv.ui.theme.YtBg
import com.youtube.tv.ui.theme.YouTubeTheme
import com.youtube.tv.vm.HomeState
import com.youtube.tv.vm.MainViewModel

class MainActivity : ComponentActivity() {

    /**
     * Ban phim roi (va emulator khi bat hw.keyboard) gui Esc la KEYCODE_ESCAPE,
     * khong phai KEYCODE_BACK, va Android khong tu quy doi. Doi thanh Back de
     * Esc dung duoc nhu nut Quay lai tren remote.
     *
     * Phai chan o dispatchKeyEvent, khong dung onKeyDown duoc: Compose dung Esc
     * cho viec thoat focus group nen no tieu thu phim nay truoc, va onKeyDown
     * cua Activity khong bao gio duoc goi.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_DOWN) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YouTubeTheme {
                Box(Modifier.fillMaxSize().background(YtBg)) {
                    if (YouTubeApi.isConfigured) {
                        YouTubeRoot()
                    } else {
                        CenteredMessage(
                            title = "Chưa cấu hình Supabase",
                            detail = "Thêm SUPABASE_URL và SUPABASE_ANON_KEY vào tv/local.properties " +
                                "rồi build lại APK. Xem docs/SETUP.md.",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dieu huong bang state thay vi navigation-compose: chi co vai man hinh va man
 * phat can giu nguyen hang doi khi tu chuyen video, nen quan ly tay ro rang hon.
 */
private sealed interface Screen {
    data object Browse : Screen
    data class Play(val video: Video) : Screen
}

@Composable
private fun YouTubeRoot(vm: MainViewModel = viewModel()) {
    val pairPhase by vm.pair.collectAsStateWithLifecycle()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val homeState by vm.home.collectAsStateWithLifecycle()
    val searchState by vm.search.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf<Screen>(Screen.Browse) }
    var dest by remember { mutableStateOf(NavDest.Home) }

    // Chua ghep va dang xem muc Ket noi -> xin ma. Roi khoi muc do thi dung vong
    // lap hoi server lai, khong de no chay nen vo ich.
    // Da ghep thi KHONG tu xin ma: man Ket noi hien trang thai va cho nguoi dung
    // chu dong bam nut neu muon them may quan tri.
    LaunchedEffect(dest, paired) {
        when {
            dest == NavDest.Connect && !paired -> vm.startPairing()
            // Roi muc Ket noi thi xoa ma dang hien, khong thi quay lai se thay
            // ma cu dung do trong khi vong lap da dung tu lau
            dest != NavDest.Connect -> vm.dismissCode()
            else -> vm.stopPairing()
        }
    }

    // Ghep xong thi tu chuyen sang trang chu
    LaunchedEffect(paired) {
        if (paired && dest == NavDest.Connect) dest = NavDest.Home
    }

    when (val s = screen) {
        is Screen.Play -> PlayerScreen(
            video = s.video,
            startSeconds = vm.resumeSecondsFor(s.video),
            hasNext = vm.queueIndex + 1 < vm.queue.size,
            onEnded = {
                val next = vm.advanceQueue()
                screen = if (next != null) Screen.Play(next) else Screen.Browse
            },
            onProgress = { seconds, duration -> vm.saveProgress(s.video, seconds, duration) },
            onDuration = { seconds -> vm.reportDuration(s.video, seconds) },
            onBack = { screen = Screen.Browse },
        )

        Screen.Browse -> Row(Modifier.fillMaxSize()) {
            // Dang o muc khac thi Back tra ve Trang chu. Khong co cai nay thi
            // Back dong ban phim mem roi lan sau la thoat han ra launcher.
            // O Trang chu thi khong chan: Back thoat app, dung nhu TV thuong lam.
            BackHandler(enabled = dest != NavDest.Home) {
                dest = NavDest.Home
                vm.clearSearch()
            }

            NavRail(
                current = dest,
                highlightConnect = !paired,
                onSelect = { picked ->
                    dest = picked
                    if (picked != NavDest.Search) vm.clearSearch()
                },
            )

            Box(Modifier.weight(1f)) {
                when (dest) {
                    NavDest.Connect -> PairScreen(
                        phase = pairPhase,
                        adminUrlHint = BuildConfig.ADMIN_URL.ifBlank { null },
                        onRequestAdminCode = vm::showCodeForAdmin,
                        onDone = { dest = NavDest.Home },
                    )

                    NavDest.Home -> when {
                        !paired -> CenteredMessage(
                            title = "Chưa kết nối",
                            detail = "Chọn “Kết nối” ở menu bên trái để lấy mã, rồi nhập mã đó vào " +
                                "trang quản trị. Sau khi kết nối, video sẽ hiện ở đây.",
                        )

                        else -> when (val h = homeState) {
                            HomeState.Loading -> CenteredMessage(title = "Đang tải…")

                            is HomeState.Error -> CenteredMessage(
                                title = "Không tải được danh sách",
                                detail = h.message,
                                actionLabel = "Thử lại",
                                onAction = vm::refresh,
                            )

                            is HomeState.Ready -> HomeScreen(
                                data = h.data,
                                progress = progress,
                                onPlay = { shelf, index ->
                                    vm.play(shelf.videos, index)
                                    screen = Screen.Play(shelf.videos[index])
                                },
                            )
                        }
                    }

                    NavDest.Search -> when {
                        !paired -> CenteredMessage(
                            title = "Chưa kết nối",
                            detail = "Chọn “Kết nối” ở menu bên trái trước đã.",
                        )

                        else -> SearchScreen(
                            state = searchState,
                            progress = progress,
                            onQueryChange = vm::search,
                            onPlay = { videos, index ->
                                vm.play(videos, index)
                                screen = Screen.Play(videos[index])
                            },
                        )
                    }
                }
            }
        }
    }
}
