package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class CloseLoad : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.filmmakinesi.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers2 = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )

        try {
            val response = app.get(url, referer = mainUrl, headers = headers2)
            val html = response.text 

            // 1. JS Deşifre Algoritmasını Dene
            var realUrl = decryptNative(html)

            // 2. Fallback Mekanizması: Eğer JS şifre çözücü başarısız olursa JSON-LD bloğundaki şifresiz contentUrl'i ara
            if (realUrl.isNullOrBlank()) {
                Log.w("Kekik_${this.name}", "Native deşifre başarısız, Fallback JSON-LD aranıyor...")
                val ldJsonMatch = """"contentUrl"\s*:\s*"([^"]+)"""".toRegex().find(html)
                realUrl = ldJsonMatch?.groupValues?.get(1)?.replace("\\/", "/")
            }

            if (!realUrl.isNullOrBlank() && realUrl.startsWith("http")) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = realUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        quality = Qualities.P1080.value
                        headers = mapOf(
                            "Referer" to "$mainUrl/",
                            "User-Agent" to headers2["User-Agent"]!!
                        )
                    }
                )
            } else {
                Log.e("Kekik_${this.name}", "Real URL bulunamadı veya deşifre edilemedi.")
            }

            processSubtitles(html, subtitleCallback)

        } catch (e: Exception) {
            Log.e("Kekik_${this.name}", "Hata: ${e.message}")
        }
    }

    private fun decryptNative(html: String): String? {
        try {
            // JS script bloğunu bul: dc_ fonksiyon çağrısı içeren
            val scriptBlockMatch = """<script[^>]*>(.*?dc_[a-zA-Z0-9_]+\(.*?</script>)""".toRegex(RegexOption.DOT_MATCHES_ALL).find(html)
            val scriptContent = scriptBlockMatch?.groupValues?.get(1) ?: return null

            // 1. Şifreli diziyi çıkar: dc_xxx([" ... "])
            val arrayMatch = """\(\[((?:"[^"]+",?\s*)+)\]\)""".toRegex().find(scriptContent)
            val parts = arrayMatch?.groupValues?.get(1)?.split(",")?.map {
                it.trim().trim('"').replace("\\/", "/")
            } ?: return null

            // 2. Fonksiyon gövdesini izole et
            val funcStartIdx = scriptContent.indexOf("function dc_")
            val funcEndIdx = scriptContent.indexOf("function d1x()", funcStartIdx)
                .takeIf { it != -1 } ?: scriptContent.length
            val functionBody = if (funcStartIdx != -1)
                scriptContent.substring(funcStartIdx, funcEndIdx)
            else scriptContent

            // 3. Tüm ROT shift değerlerini sırasıyla çıkar (site çift replace kullanıyor olabilir)
            val rotShifts = mutableListOf<Int>()
            val rotPattern = """o\s*-\s*base\s*\+\s*(\d+)""".toRegex()
            rotPattern.findAll(functionBody).forEach { m ->
                rotShifts.add(m.groupValues[1].toInt())
            }
            // Alternatif: charCodeAt(0) + N formatı
            if (rotShifts.isEmpty()) {
                val altPattern = """charCodeAt\(0\)\s*\+\s*(\d+)""".toRegex()
                altPattern.findAll(functionBody).forEach { m ->
                    rotShifts.add(m.groupValues[1].toInt())
                }
            }
            Log.d("Kekik_${this.name}", "ROT shifts: $rotShifts")

            // 4. XOR accumulator parametrelerini çıkar
            // Örnek: var acc = 47; ... acc = (acc + 19) % 256;
            val xorAccStart = """var\s+acc\s*=\s*(\d+)""".toRegex().find(functionBody)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val xorStep = """acc\s*=\s*\(acc\s*\+\s*(\d+)\)\s*%\s*256""".toRegex().find(functionBody)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 1
            Log.d("Kekik_${this.name}", "XOR acc_start=$xorAccStart, step=$xorStep")

            // 5. Operasyon sırasını belirle (atob, reverse, rot pozisyonlarına göre)
            data class Op(val pos: Int, val type: String)
            val operations = mutableListOf<Op>()

            var idx = functionBody.indexOf("atob(")
            while (idx >= 0) {
                operations.add(Op(idx, "atob"))
                idx = functionBody.indexOf("atob(", idx + 1)
            }
            idx = functionBody.indexOf(".reverse()")
            while (idx >= 0) {
                operations.add(Op(idx, "reverse"))
                idx = functionBody.indexOf(".reverse()", idx + 1)
            }
            idx = functionBody.indexOf(".replace(")
            var rotIdx = 0
            while (idx >= 0) {
                operations.add(Op(idx, "rot_$rotIdx"))
                idx = functionBody.indexOf(".replace(", idx + 1)
                rotIdx++
            }
            operations.sortBy { it.pos }
            Log.d("Kekik_${this.name}", "Operations: ${operations.map { it.type }}")

            // 6. İşlemleri uygula
            var result = parts.joinToString("")
            for (op in operations) {
                when {
                    op.type == "atob" -> {
                        var padded = result
                        while (padded.length % 4 != 0) padded += "="
                        result = String(Base64.decode(padded, Base64.NO_WRAP), Charsets.ISO_8859_1)
                    }
                    op.type == "reverse" -> {
                        result = result.reversed()
                    }
                    op.type.startsWith("rot_") -> {
                        val shiftIdx = op.type.removePrefix("rot_").toIntOrNull() ?: 0
                        val shift = if (shiftIdx < rotShifts.size) rotShifts[shiftIdx] else 13
                        val rot = StringBuilder()
                        for (c in result) {
                            when (c) {
                                in 'a'..'z' -> rot.append(((c.code - 97 + shift) % 26 + 97).toChar())
                                in 'A'..'Z' -> rot.append(((c.code - 65 + shift) % 26 + 65).toChar())
                                else -> rot.append(c)
                            }
                        }
                        result = rot.toString()
                    }
                }
            }

            // 7. Son adım: XOR accumulator unmix
            var acc = xorAccStart
            val unmix = StringBuilder()
            for (ch in result) {
                val b = ch.code
                acc = (acc + xorStep) % 256
                val plain = b xor acc
                acc = (acc + b) % 256
                unmix.append(plain.toChar())
            }

            val decoded = unmix.toString()
            Log.d("Kekik_${this.name}", "Decoded URL: $decoded")
            return decoded

        } catch (e: Exception) {
            Log.e("Kekik_Extractor", "Native Çözümleme Hatası: ${e.message}")
            return null
        }
    }


    private fun processSubtitles(html: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            // JWPlayer setup içindeki tracks: [...] JSON bloğu
            val tracksMatch = """tracks\s*:\s*(\[.*?\])""".toRegex(RegexOption.DOT_MATCHES_ALL).find(html)
            tracksMatch?.groupValues?.get(1)?.let { tracksJson ->
                
                val trackPattern = """\{[^}]*\}""".toRegex()
                val fileRegex = """"file"\s*:\s*"([^"]+)"""".toRegex()
                val labelRegex = """"label"\s*:\s*"([^"]+)"""".toRegex()

                trackPattern.findAll(tracksJson).forEach { match ->
                    val block = match.value
                    val file = fileRegex.find(block)?.groupValues?.get(1)?.replace("\\/", "/")
                    val label = labelRegex.find(block)?.groupValues?.get(1) ?: "Altyazı"

                    // file null değilse ve http ile başlıyorsa fırlat
                    if (!file.isNullOrBlank() && file.startsWith("http")) {
                        subtitleCallback.invoke(SubtitleFile(label, file))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Kekik_${this.name}", "Altyazı Çözümleme Hatası: ${e.message}")
        }
    }
}
