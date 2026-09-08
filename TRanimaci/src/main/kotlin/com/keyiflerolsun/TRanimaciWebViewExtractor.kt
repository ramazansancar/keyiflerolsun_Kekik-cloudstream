package com.keyiflerolsun

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope

class TRanimaciWebViewExtractor(private val context: Context) : ExtractorApi() {
    override val name = "TRanimaci Özel"
    override val mainUrl = "https://tranimaci.com"
    override val requiresReferer = true

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""

                        if (reqUrl.contains(".mp4") || reqUrl.contains(".m3u8")) {
                            val isM3u8 = reqUrl.contains(".m3u8")
                            val type = if (isM3u8) com.lagradost.cloudstream3.utils.INFER_TYPE else ExtractorLinkType.VIDEO
                            val qualityStr = if (reqUrl.contains("1080p")) Qualities.P1080.value else Qualities.P720.value
                            
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                callback.invoke(
                                    com.lagradost.cloudstream3.utils.newExtractorLink(
                                        source = "TRanimaciWebView",
                                        name = "TRanimeci",
                                        url = reqUrl,
                                        type = type
                                    ) {
                                        this.quality = qualityStr
                                        this.headers = mapOf("Referer" to mainUrl)
                                    }
                                )
                            }
                        }

                        // Block ads to speed up page load
                        if (reqUrl.contains("adsterra") || reqUrl.contains("pop-adsterra") || reqUrl.contains("mammothsubway") || reqUrl.contains("zoologyfibre") || reqUrl.contains("spendsdetachment")) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

                loadUrl(url)
            }
        }

        // Wait for up to 15 seconds to allow the page to decrypt and fetch the video source
        delay(15_000)
        
        withContext(Dispatchers.Main) {
            try {
                webView?.destroy()
                webView = null
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
