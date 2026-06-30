package com.danchi.app.data

import com.danchi.app.domain.DictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class RemoteEcdictDataSource(
    private val baseUrl: String = ""
) {
    suspend fun lookup(word: String): DictionaryEntry? {
        if (baseUrl.isBlank() || word.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(word.trim().lowercase(), "UTF-8")
                val endpoint = "${baseUrl.trimEnd('/')}/dictionary/$encoded"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return@withContext null
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                DictionaryJson.decode(JSONObject(body))
            }.getOrNull()
        }
    }
}
