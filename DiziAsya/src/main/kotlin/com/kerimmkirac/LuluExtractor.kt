package com.kerimmkirac

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities

open class LuluuExtractor : ExtractorApi() {
    override var name = "LuluStream"
    override var mainUrl = "https://luluvdoo.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("DiziAsya", " LuluStream: Processing URL: $url")
        
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
                "Referer" to (referer ?: mainUrl),
                "Origin" to mainUrl,
                "Connection" to "keep-alive",
                "Upgrade-Insecure-Requests" to "1"
            )

            Log.d("DiziAsya", " LuluStream: Making request to $url")
            val response = app.get(url, headers = headers)
            Log.d("DiziAsya", " LuluStream: Response status: ${response.code}")
            
            val document = response.document
            
            
            val scripts = document.select("script")
            Log.d("DiziAsya", " LuluStream: Found ${scripts.size} script tags")
            
            var jwPlayerScript: String? = null
            for (script in scripts) {
                val scriptData = script.data()
                if (scriptData.contains("jwplayer") || scriptData.contains("sources") || scriptData.contains("file:")) {
                    jwPlayerScript = scriptData
                    Log.d("DiziAsya", " LuluStream: Found jwplayer script")
                    break
                }
            }
            
            if (jwPlayerScript != null) {
                Log.d("DiziAsya", " LuluStream: Script content preview: ${jwPlayerScript.take(200)}")
                
                
                val patterns = listOf(
                    Regex("""sources:\s*\[\s*\{\s*file:\s*["']([^"']+)["']"""),
                    Regex("""file:\s*["']([^"']+)["']"""),
                    Regex("""src:\s*["']([^"']+)["']"""),
                    Regex("""url:\s*["']([^"']+)["']""")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(jwPlayerScript)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                        Log.d("DiziAsya", " LuluStream: Found video URL: $videoUrl")
                        
                        callback.invoke(
                            newExtractorLink(
                                name = name,
                                source = name,
                                url = videoUrl,
                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                                this.headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
                                    "Referer" to mainUrl,
                                    "Origin" to mainUrl
                                )
                            }
                        )
                        Log.d("DiziAsya", " LuluStream: Successfully extracted video link")
                        return
                    }
                }
                
                Log.d("DiziAsya", " LuluStream: No video URL found in jwplayer script")
            }
            
            
            Log.d("DiziAsya", " LuluStream: Trying fallback method...")
            val filecode = url.substringAfterLast("/")
            Log.d("DiziAsya", " LuluStream: File code: $filecode")
            
            val postUrl = "$mainUrl/dl"
            val postData = mapOf(
                "op" to "embed",
                "file_code" to filecode,
                "auto" to "1",
                "referer" to (referer ?: mainUrl)
            )
            
            Log.d("DiziAsya", " LuluStream: Making POST request to $postUrl")
            val postResponse = app.post(postUrl, headers = headers, data = postData)
            Log.d("DiziAsya", " LuluStream: POST response status: ${postResponse.code}")
            
            val postDocument = postResponse.document
            val postScripts = postDocument.select("script")
            
            for (script in postScripts) {
                val scriptData = script.data()
                if (scriptData.contains("vplayer") || scriptData.contains("file:")) {
                    Log.d("DiziAsya", " LuluStream: Found vplayer script in POST response")
                    val match = Regex("""file:\s*["']([^"']+)["']""").find(scriptData)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                        Log.d("DiziAsya", " LuluStream: Found fallback video URL: $videoUrl")
                        
                        callback.invoke(
                            newExtractorLink(
                                name,
                                name,
                                url = videoUrl,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                            }
                        )
                        Log.d("DiziAsya", " LuluStream: Successfully extracted via fallback method")
                        return
                    }
                }
            }
            
            Log.d("DiziAsya", " LuluStream: No video found in any method")
            
        } catch (e: Exception) {
            Log.e("DiziAsya", " LuluStream extraction error: ${e.message}", e)
            Log.e("DiziAsya", " LuluStream stack trace: ${e.stackTraceToString()}")
        }
    }
}