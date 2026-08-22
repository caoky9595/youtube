package com.youtube.tv.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtube.tv.YouTubeApp
import com.youtube.tv.data.HomeData
import com.youtube.tv.data.Video
import com.youtube.tv.data.YouTubeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException

/** Cho nguoi dung go xong truoc khi goi mang. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Nhip goi pair_request trong luc hien ma: vua de biet admin da nhap chua, vua
 * de lay ma moi khi ma cu het han. Dem nguoi tren man hinh cung nhay theo nhip
 * nay, nen dung 2s cho no muot.
 */
private const val PAIR_POLL_MS = 2_500L

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val data: HomeData) : HomeState
    data class Error(val message: String) : HomeState
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Ready(val query: String, val results: List<Video>) : SearchState
    data class Error(val message: String) : SearchState
}

sealed interface PairPhase {
    /** Dang kiem tra token luu san con hieu luc khong. */
    data object Checking : PairPhase

    /** Dang xin ma tu server. */
    data object Requesting : PairPhase

    /**
     * @param forAdmin true = ma nay de THEM mot may quan tri nua (TV da ghep
     *        san roi), false = ma de ghep TV lan dau.
     */
    data class ShowingCode(
        val code: String,
        val secondsLeft: Int,
        val forAdmin: Boolean = false,
    ) : PairPhase

    /** Da ghep. Man Ket noi hien trang thai + nut xin ma cho may quan tri moi. */
    data object Paired : PairPhase

    /** Vua co mot may quan tri nhap ma xong. */
    data object AdminAdded : PairPhase

    data class Failed(val message: String) : PairPhase
}

/**
 * Mot ViewModel duy nhat cho ca app: chi co vai man hinh va chung dung chung
 * ban do tien do xem, nen tach nhieu VM chi lam kho dong bo.
 */
class MainViewModel : ViewModel() {

    private val _pair = MutableStateFlow<PairPhase>(PairPhase.Checking)
    val pair: StateFlow<PairPhase> = _pair.asStateFlow()

    /**
     * TV da ghep hay chua. Phai la state RIENG, khong duoc suy ra tu PairPhase:
     * khi dang hien ma de them may quan tri thi phase la ShowingCode, ma luc do
     * TV van dang ghep — suy ra tu phase se cho ket qua sai (hien dau nhac do,
     * va Trang chu bao "chua ket noi").
     */
    private val _paired = MutableStateFlow(YouTubeApp.tvToken != null)
    val paired: StateFlow<Boolean> = _paired.asStateFlow()

    private val _home = MutableStateFlow<HomeState>(HomeState.Loading)
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /** video_id -> so giay da xem. Dung ve thanh do tren the video. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    /** Hang doi phat: bam vao mot the se phat tiep cac video con lai trong hang do. */
    var queue: List<Video> = emptyList()
        private set
    var queueIndex: Int = 0
        private set

    private var searchJob: Job? = null
    private var pairJob: Job? = null

    init {
        checkPairing()
    }

    /* ---------------------------- ghep may ---------------------------- */

    /**
     * Goi khi mo app. Khong hien ma o day: nguoi dung phai tu chon "Kết nối"
     * trong menu. Chi kiem tra xem token dang co con hieu luc khong — admin co
     * the da ngat thiet bi nay tu xa.
     */
    fun checkPairing() {
        _pair.value = PairPhase.Checking
        viewModelScope.launch {
            try {
                val state = YouTubeApi.pairPoll()
                if (state.paired && state.tvToken != null) {
                    YouTubeApp.tvToken = state.tvToken
                    _paired.value = true
                    _pair.value = PairPhase.Paired
                    refresh()
                } else {
                    YouTubeApp.tvToken = null
                    _paired.value = false
                    _pair.value = PairPhase.Failed("Chưa kết nối với kho video nào")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pair.value = PairPhase.Failed(e.message ?: "Không kết nối được server")
            }
        }
    }

    /**
     * Nguoi dung chon "Kết nối" trong menu khi TV chua ghep: xin ma va hien len.
     *
     * Vong lap chi goi pair_request. Ham do da lam ca ba viec: tra ve dung ma cu
     * neu con hieu luc, tao ma moi khi ma cu het han (nen sau 15 phut la tu doi
     * ma), va bao paired = true ngay khi admin nhap xong.
     */
    fun startPairing() {
        if (pairJob?.isActive == true) return
        pairJob = viewModelScope.launch {
            _pair.value = PairPhase.Requesting
            while (true) {
                try {
                    val state = YouTubeApi.pairRequest()
                    if (state.paired && state.tvToken != null) {
                        finishPairing(state.tvToken)
                        return@launch
                    }
                    val code = state.code
                    if (code == null) {
                        _pair.value = PairPhase.Failed("Server không trả về mã")
                        return@launch
                    }
                    _pair.value = PairPhase.ShowingCode(code, secondsLeft(state.expiresAt))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _pair.value = PairPhase.Failed(e.message ?: "Không kết nối được server")
                    return@launch
                }
                delay(PAIR_POLL_MS)
            }
        }
    }

    /**
     * TV da ghep roi, nguoi dung muon them mot may quan tri nua (dien thoai thu
     * hai, hoac trinh duyet vua xoa du lieu).
     *
     * Khong the dua vao truong "paired" de biet da xong — no da true tu truoc.
     * Nen o day theo doi trang thai cua DUNG ma dang hien: bi nhap thi dung, het
     * han thi xin ma moi.
     */
    fun showCodeForAdmin() {
        if (pairJob?.isActive == true) return
        pairJob = viewModelScope.launch {
            _pair.value = PairPhase.Requesting
            var code: String? = null
            while (true) {
                try {
                    if (code == null) {
                        val state = YouTubeApi.pairRequest(force = true)
                        code = state.code
                        if (code == null) {
                            _pair.value = PairPhase.Failed("Server không trả về mã")
                            return@launch
                        }
                        _pair.value = PairPhase.ShowingCode(
                            code = code,
                            secondsLeft = secondsLeft(state.expiresAt),
                            forAdmin = true,
                        )
                    } else {
                        val status = YouTubeApi.pairCodeStatus(code)
                        when {
                            status.claimed -> {
                                _pair.value = PairPhase.AdminAdded
                                return@launch
                            }
                            // Het han hoac ma bi don di -> xin ma moi vong sau
                            status.expired || !status.exists -> code = null
                            else -> {
                                val current = _pair.value
                                if (current is PairPhase.ShowingCode) {
                                    _pair.value = current.copy(
                                        secondsLeft = (current.secondsLeft - 3).coerceAtLeast(0),
                                    )
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _pair.value = PairPhase.Failed(e.message ?: "Không kết nối được server")
                    return@launch
                }
                delay(PAIR_POLL_MS)
            }
        }
    }

    /**
     * Huy vong lap dang hien ma va dua trang thai ve dung thuc te. Goi khi roi
     * muc Ket noi, de lan sau vao lai khong thay ma cu con dung do.
     */
    fun dismissCode() {
        stopPairing()
        _pair.value = if (_paired.value) {
            PairPhase.Paired
        } else {
            PairPhase.Failed("Chưa kết nối với kho video nào")
        }
    }

    fun stopPairing() {
        pairJob?.cancel()
        pairJob = null
    }

    private fun finishPairing(token: String) {
        YouTubeApp.tvToken = token
        _paired.value = true
        _pair.value = PairPhase.Paired
        refresh()
    }

    /** Server tra expires_at dang ISO-8601. Loi parse thi coi nhu con 15 phut. */
    private fun secondsLeft(iso: String?): Int {
        val deadline = try {
            if (iso == null) System.currentTimeMillis() + 15 * 60_000L
            else Instant.parse(iso.replace(" ", "T")).toEpochMilli()
        } catch (_: DateTimeParseException) {
            System.currentTimeMillis() + 15 * 60_000L
        }
        return ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    }

    /* ----------------------------- du lieu ---------------------------- */

    fun refresh() {
        if (YouTubeApp.tvToken == null) return
        _home.value = HomeState.Loading
        viewModelScope.launch {
            try {
                _home.value = HomeState.Ready(YouTubeApi.home())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _home.value = HomeState.Error(e.message ?: "Không tải được danh sách")
            }
        }
        viewModelScope.launch { _progress.value = YouTubeApi.progress() }
    }

    fun search(query: String) {
        // Huy lan tim truoc: nguoi dung dang go, chi ket qua cuoi moi co nghia
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _search.value = SearchState.Idle
            return
        }
        _search.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            try {
                delay(SEARCH_DEBOUNCE_MS)
                _search.value = SearchState.Ready(q, YouTubeApi.search(q))
            } catch (e: CancellationException) {
                // Bi huy vi nguoi dung go tiep — day KHONG phai loi.
                // runCatching se bat ca CancellationException roi bao "loi" ra
                // man hinh, de len ket qua dung cua lan tim moi hon.
                throw e
            } catch (e: Exception) {
                _search.value = SearchState.Error(e.message ?: "Tìm kiếm thất bại")
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState.Idle
    }

    fun play(videos: List<Video>, index: Int) {
        queue = videos
        queueIndex = index.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    }

    fun advanceQueue(): Video? {
        if (queueIndex + 1 >= queue.size) return null
        queueIndex += 1
        return queue[queueIndex]
    }

    fun resumeSecondsFor(video: Video): Float {
        val saved = _progress.value[video.id] ?: return 0f
        val total = video.durationSeconds ?: return saved.toFloat()
        // Gan het video roi thi xem lai tu dau, dung nhay vao doan credits
        return if (saved >= total - 15) 0f else saved.toFloat()
    }

    /**
     * Trinh phat vua cho biet thoi luong that cua mot video ma kho chua co.
     * Ghi ve server roi tai lai trang chu de the video hien badge thoi luong.
     */
    fun reportDuration(video: Video, seconds: Int) {
        if (video.durationSeconds != null || seconds <= 0) return
        viewModelScope.launch {
            YouTubeApi.reportDuration(video.id, seconds)
            refresh()
        }
    }

    fun saveProgress(video: Video, seconds: Int, duration: Int?) {
        _progress.value = _progress.value + (video.id to seconds)
        viewModelScope.launch {
            YouTubeApi.saveProgress(video.id, seconds, duration ?: video.durationSeconds)
        }
    }
}
