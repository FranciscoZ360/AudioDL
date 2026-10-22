package com.audiodl.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            initOk = true
        } catch (e: Exception) {
            initError = e.message ?: e.toString()
            Log.e("AudioDL", "Error al inicializar yt-dlp/FFmpeg", e)
        }
    }

    companion object {
        @Volatile var initOk = false
        @Volatile var initError: String? = null
    }
}
