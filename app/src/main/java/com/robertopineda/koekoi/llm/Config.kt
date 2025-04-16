package com.robertopineda.koekoi.llm

import android.util.Log
import com.robertopineda.koekoi.BuildConfig

object Config {
    val apiKey: String = BuildConfig.DEEPSEEK_API_KEY.also {
        if (it.isEmpty() || it == "\"\"") {
            Log.e("Config", "DeepSeek API Key is not set in local.properties / BuildConfig!")
        }
    }

    const val apiUrl = "https://api.deepseek.com/v1/chat/completions"
    const val apiModel = "deepseek-chat"
}