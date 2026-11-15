package com.nyxora.music.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SongItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val codecLabel: String = "32-BIT FLOAT • 160kbps OPUS 48kHz"
)

object InnertubeRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun searchSongs(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongItem>()
        val bodyJson = JSONObject().apply {
            put("query", query)
            put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D") 
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240717.01.00")
                    put("hl", "en")
                    put("gl", "IN")
                })
            })
        }

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Origin", "https://music.youtube.com")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: return@withContext emptyList()
                val shelfRegex = Regex(""""videoId""":"([a-zA-Z0-9_-]{11})"""")
                val seen = mutableSetOf<String>()
                
                // Fallback direct regex extraction for speed and reliability
                val basicMatch = Regex(""videoId":"([^"]{11})","title":\{"runs":\[\{"text":"([^"]+)"").findAll(raw)
                for (m in basicMatch) {
                    val vid = m.groupValues[1]
                    val title = m.groupValues[2]
                    if (seen.add(vid)) {
                        results.add(SongItem(
                            videoId = vid,
                            title = title,
                            artist = "YouTube Music Audio",
                            artworkUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                        ))
                    }
                }
            }
        }
        results
    }

    suspend fun resolveStreamUrl(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.60.19")
                    put("hl", "en")
                })
            })
        }

        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.60.19")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            http.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: return@use null)
                val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return@use null

                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val mime = fmt.optString("mimeType", "")
                    val url = fmt.optString("url", "")
                    val itag = fmt.optInt("itag", 0)

                    if (url.isNotEmpty() && mime.startsWith("audio/") && itag == 251) {
                        return@use Pair(url, "32-BIT FLOAT • 160kbps OPUS 48kHz")
                    }
                }
                
                // Fallback to highest available if 251 not found
                if (formats.length() > 0) {
                   return@use Pair(formats.getJSONObject(0).optString("url"), "Fallback Stream")
                }
                null
            }
        }.getOrNull()
    }
}
