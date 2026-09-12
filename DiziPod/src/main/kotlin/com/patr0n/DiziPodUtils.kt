package com.patr0n

import com.lagradost.cloudstream3.app

suspend fun extractM3u8FromDizipodPlayer(url: String, referer: String?): String? {
    val res = app.get(url, referer = referer)
    val html = res.text

    val packedRegex = Regex("""eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
    val packedMatch = packedRegex.find(html)

    var unpacked = html
    if (packedMatch != null) {
        val pStr = packedMatch.groupValues[1]
        val a = packedMatch.groupValues[2].toIntOrNull() ?: 1
        val kList = packedMatch.groupValues[4].split("|")

        val lookup = mutableMapOf<String, String>()
        for ((i, word) in kList.withIndex()) {
            val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            var key = ""
            var num = i
            if (num == 0) {
                key = "0"
            } else {
                while (num > 0) {
                    key = chars[num % a] + key
                    num /= a
                }
            }
            lookup[key] = word.ifEmpty { key }
        }

        val unescapedPStr = pStr.replace("\\'", "'").replace("\\\\", "\\")

        unpacked = Regex("""\b(\w+)\b""").replace(unescapedPStr) { m ->
            lookup[m.groupValues[1]] ?: m.groupValues[1]
        }
    }

    val cleanedHtml = unpacked.replace("\\\\/", "/").replace("\\/", "/")
    val m3u8Regex = Regex("""(https?://[^\s"\'<>]+?\.m3u8)""")
    return m3u8Regex.find(cleanedHtml)?.groupValues?.get(1)
}
