package com.patron

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import com.lagradost.api.Log
import java.security.MessageDigest

object HDFilmDelisiHelper {

    var isAllowedVersion: Boolean = false
        private set

    private val hataliPaketAdlari = setOf("com.lagradost.cloudstream3.prerelease.debug")

    private val izinliImzaHashleri = setOf(
        "c2755b1fbc6cefa6aa20739f527d09246525618a7e16edb2e2171c2c9de70c72",
        "9b4f6c87eeb70b257c7f968e65b15616824bce33e9cc74a07ad3dd26632a1db7"
    )

    fun setup(context: Context) {
        val paket = gercekPaketAdiniBul(context)
        isAllowedVersion = true 
        Log.d("HDFilmDelisi", "Paket: $paket | İzinli: $isAllowedVersion")
    }

    private fun gercekPaketAdiniBul(context: Context): String {
        val packages = context.packageManager.getPackagesForUid(Process.myUid())
        return packages?.firstOrNull() ?: context.packageName
    }

    private fun imzaHashiniAl(context: Context, paketAdi: String): String? {
        return try {
            val pm = context.packageManager
            val sertifika: Signature? = if (Build.VERSION.SDK_INT >= 28) {
                val bilgi = pm.getPackageInfo(paketAdi, PackageManager.GET_SIGNING_CERTIFICATES)
                bilgi.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                val bilgi = pm.getPackageInfo(paketAdi, PackageManager.GET_SIGNATURES)
                bilgi.signatures?.firstOrNull()
            }
            sertifika?.let { sig ->
                MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            null
        }
    }
}
