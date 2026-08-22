package com.youtube.tv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

class YouTubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: YouTubeApp
            private set

        private const val PREFS = "youtube"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_TV_TOKEN = "tv_token"

        private val prefs: SharedPreferences
            get() = instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * Dinh danh ban cai nay, sinh mot lan roi giu mai. Server dung no de
         * biet ma ghep dang cho la cua may nao.
         */
        val installId: String by lazy {
            prefs.getString(KEY_INSTALL_ID, null)
                ?: UUID.randomUUID().toString().also {
                    // commit = true: ghi dong bo. Mac dinh cua edit {} la apply(),
                    // ghi bat dong bo — process bi kill ngay sau do la mat luon,
                    // lan mo sau app sinh install_id khac va coi nhu may la moi.
                    prefs.edit(commit = true) { putString(KEY_INSTALL_ID, it) }
                }
        }

        /** Ten hien ben trang admin. Lay model may cho de nhan ra. */
        val deviceName: String
            get() = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Android TV" }

        /** Token cap sau khi ghep. Null = chua ghep. */
        var tvToken: String?
            get() = prefs.getString(KEY_TV_TOKEN, null)
            // commit = true vi ly do nhu installId: mat lan ghi nay la TV mat
            // ghep, phai nhap ma lai. Ghi rat it va rat nho, dong bo khong sao.
            set(value) = prefs.edit(commit = true) {
                if (value == null) remove(KEY_TV_TOKEN) else putString(KEY_TV_TOKEN, value)
            }
    }
}
