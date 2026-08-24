package com.youtube.tv.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Doi loi mang thanh cau nguoi dung TV hieu duoc.
 *
 * OkHttp nem nguyen van tieng Anh kieu
 * `Unable to resolve host "abc.supabase.co": No address associated with hostname`
 * — dai, tieng Anh, va khong noi len phai lam gi.
 */
fun friendlyMessage(e: Throwable): String = when (e) {
    is UnknownHostException ->
        "Không phân giải được địa chỉ server. Kiểm tra TV đã kết nối mạng chưa."

    is SocketTimeoutException ->
        "Server không phản hồi. Mạng có thể đang chậm."

    is YouTubeApi.NotConfigured -> e.message ?: "Chưa cấu hình Supabase."

    is IOException -> e.message ?: "Không kết nối được server."

    else -> e.message ?: "Có lỗi không rõ."
}
