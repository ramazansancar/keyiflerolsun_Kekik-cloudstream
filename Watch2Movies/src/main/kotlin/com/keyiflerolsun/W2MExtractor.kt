// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.ExtractorLinkType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

open class W2MExtractor(override val mainUrl: String, private val context: Context) : ExtractorApi() {
    override val name = "W2MExtractor"
    override val requiresReferer = true

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            val webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: return null
                        val headers = request.requestHeaders

                        if (requestUrl.endsWith(".m3u8")) {
                            callback.invoke(
                                ExtractorLink(
                                    source = name,
                                    name = name,
                                    url = requestUrl,
                                    referer = headers["Referer"] ?: mainUrl,
                                    quality = Qualities.Unknown.value,
                                    type = ExtractorLinkType.M3U8,
                                    headers = headers
                                )
                            )
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

                loadUrl(url)
            }

            // Ensure WebView is cleaned up after use
            Runtime.getRuntime().addShutdownHook(Thread { webView.destroy() })
        }
    }
}