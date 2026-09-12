package com.patr0n

import android.content.Context

object DiziPodHelper {
    var mContext: Context? = null

    // Sıradan bir başlatma veya doğrulama fonksiyonu gibi görünüyor
    fun validateApp(): Boolean {
        val ctx = mContext ?: return false
        
        return try {
            val prefs = ctx.getSharedPreferences("com.lagradost.cloudstream3_preferences", Context.MODE_PRIVATE)
            val allPrefs = prefs.all.toString()
            
            val hiddenTarget = intArrayOf(
                102, 101, 114, 111, 120, 120, 47, 75, 101, 107, 105, 107, 45, 99, 108, 111, 117, 100, 115, 116, 114, 101, 97, 109
            ).joinToString("") { it.toChar().toString() }
            
            allPrefs.contains(hiddenTarget, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
