package com.guruswarupa.launch.managers

import android.content.Context
import com.guruswarupa.launch.R
import java.security.MessageDigest
import java.security.SecureRandom

class RecoveryKeyManager(private val context: Context) {

    private val wordList: List<String> by lazy {
        context.resources.openRawResource(R.raw.wordlist).bufferedReader().readLines()
    }

    fun generateRecoveryPhrase(): List<String> {
        val random = SecureRandom()
        val phrase = mutableListOf<String>()
        for (i in 0 until 20) {
            phrase.add(wordList[random.nextInt(wordList.size)])
        }
        return phrase
    }

    fun hashPhrase(phrase: List<String>): String {
        return hashPhrase(phrase.joinToString(" "))
    }

    fun hashPhrase(phrase: String): String {
        val normalized = phrase.trim().lowercase().split("\\s+".toRegex()).joinToString(" ")
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPhrase(enteredPhrase: String, storedHash: String): Boolean {
        return hashPhrase(enteredPhrase) == storedHash
    }
}
