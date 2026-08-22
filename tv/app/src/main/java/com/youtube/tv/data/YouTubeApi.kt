package com.youtube.tv.data

import com.youtube.tv.BuildConfig
import com.youtube.tv.YouTubeApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Goi thang PostgREST cua Supabase bang OkHttp.
 *
 * Khong dung Supabase Kotlin SDK: app TV chi can vai endpoint, ma bo SDK keo
 * theo Ktor + nhieu module — nang APK va them mot truc phien ban phai theo doi.
 *
 * Quyen truy cap di bang header X-YouTube-Token (tv_token, cap khi ghep may).
 * RLS ben Postgres doc header do, nen khong co token thi moi truy van tra ve
 * rong — khong phai loi.
 */
object YouTubeApi {

    class NotConfigured : IOException(
        "Chưa cấu hình Supabase. Thêm SUPABASE_URL và SUPABASE_ANON_KEY vào tv/local.properties rồi build lại.",
    )

    class NotPaired : IOException("Thiết bị chưa được ghép với kho video nào.")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val jsonMedia = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    /**
     * @param withToken gan tv_token vao header. Cac RPC ghep may (pair_request,
     *        pair_poll) chua co token nen goi voi false.
     */
    private fun request(
        path: String,
        body: String?,
        withToken: Boolean = true,
        prefer: String? = null,
    ): Request {
        if (!isConfigured) throw NotConfigured()
        return Request.Builder()
            .url("$baseUrl/rest/v1/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .apply {
                if (withToken) {
                    val token = YouTubeApp.tvToken ?: throw NotPaired()
                    header("X-YouTube-Token", token)
                }
                if (prefer != null) header("Prefer", prefer)
                if (body != null) post(body.toRequestBody(jsonMedia)) else get()
            }
            .build()
    }

    private suspend fun exec(req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) {
                // PostgREST tra loi dang {"message": "...", "hint": "..."}
                val msg = runCatching {
                    (json.parseToJsonElement(text) as JsonObject)["message"]
                        ?.let { (it as JsonPrimitive).content }
                }.getOrNull()
                throw IOException(msg ?: "Lỗi ${res.code} khi gọi Supabase")
            }
            text
        }
    }

    /* ---------------------------- ghep may ---------------------------- */

    /**
     * Xin ma ghep. Goi lai nhieu lan van tra ve dung ma cu neu con hieu luc, nen
     * nguoi dung dang doc ma tren TV khong bi doi ma giua duong.
     *
     * @param force xin ma ke ca khi thiet bi DA ghep — dung khi muon them mot
     *        may quan tri nua (dien thoai thu hai, hoac trinh duyet vua bi xoa
     *        du lieu). Ma do khi nhap se tra ve token cua dung kho dang dung,
     *        khong tao kho moi.
     */
    suspend fun pairRequest(force: Boolean = false): PairState {
        val body = buildJsonObject {
            put("p_install_id", JsonPrimitive(YouTubeApp.installId))
            put("p_device_name", JsonPrimitive(YouTubeApp.deviceName))
            put("p_force_code", JsonPrimitive(force))
        }
        return json.decodeFromString(
            exec(request("rpc/pair_request", body.toString(), withToken = false)),
        )
    }

    /**
     * Trang thai cua dung mot ma. Dung khi hien ma de them may quan tri: luc do
     * thiet bi da ghep roi nen khong the dua vao truong "paired" de biet xong.
     */
    suspend fun pairCodeStatus(code: String): CodeStatus {
        val body = buildJsonObject { put("p_code", JsonPrimitive(code)) }
        return json.decodeFromString(
            exec(request("rpc/pair_code_status", body.toString(), withToken = false)),
        )
    }

    /**
     * Hoi xem da duoc ghep chua. Cung dung luc mo app de kiem tra token con
     * hieu luc — admin co the da ngat thiet bi nay tu xa.
     */
    suspend fun pairPoll(): PairState {
        val body = buildJsonObject { put("p_install_id", JsonPrimitive(YouTubeApp.installId)) }
        return json.decodeFromString(
            exec(request("rpc/pair_poll", body.toString(), withToken = false)),
        )
    }

    /* ---------------------------- du lieu ----------------------------- */

    suspend fun home(recentLimit: Int = 20): HomeData {
        val body = buildJsonObject { put("p_recent_limit", JsonPrimitive(recentLimit)) }
        return json.decodeFromString(exec(request("rpc/tv_home", body.toString())))
    }

    suspend fun search(query: String, limit: Int = 50): List<Video> {
        val body = buildJsonObject {
            put("p_query", JsonPrimitive(query))
            put("p_limit", JsonPrimitive(limit))
        }
        return json.decodeFromString(exec(request("rpc/search_videos", body.toString())))
    }

    /**
     * Ghi vi tri dang xem. Loi o day khong duoc lam sap trinh phat, nen bo qua —
     * nhung phai nem lai CancellationException, khong thi viec huy coroutine bi
     * am tham chan lai.
     */
    suspend fun saveProgress(videoId: String, positionSeconds: Int, durationSeconds: Int?) {
        val body = buildJsonObject {
            put("p_video_id", JsonPrimitive(videoId))
            put("p_position_seconds", JsonPrimitive(positionSeconds))
            put("p_duration_seconds", durationSeconds?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as Int?))
        }
        try {
            exec(request("rpc/save_progress", body.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Mat mot lan ghi tien do khong dang de bao loi cho nguoi dung
        }
    }

    /**
     * Bao thoi luong that ve server. Video them bang cach dan duong dan lay
     * metadata qua oEmbed, ma oEmbed khong co thoi luong — trinh phat thi biet,
     * nen dien ho. Server chi nhan khi o do con trong, khong ghi de.
     */
    suspend fun reportDuration(videoId: String, durationSeconds: Int) {
        val body = buildJsonObject {
            put("p_video_id", JsonPrimitive(videoId))
            put("p_duration_seconds", JsonPrimitive(durationSeconds))
        }
        try {
            exec(request("rpc/report_duration", body.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Khong quan trong den muc phai bao loi cho nguoi dang xem
        }
    }

    /** Tien do khong lay duoc thi coi nhu chua xem gi — khong phai loi. */
    suspend fun progress(): Map<String, Int> {
        val path = "watch_progress?select=video_id,position_seconds"
        return try {
            json.decodeFromString<List<ProgressRow>>(exec(request(path, null)))
                .associate { it.videoId to it.positionSeconds }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
