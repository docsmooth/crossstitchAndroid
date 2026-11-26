package com.docsmooth.thumbstitch.persistence

import android.content.res.AssetManager
import com.docsmooth.thumbstitch.domain.Pattern
import java.io.IOException

class BuiltInPatterns(private val assets: AssetManager) {
    fun loadHeart(): Pattern = load("patterns/heart.json")

    private fun load(path: String): Pattern {
        try {
            return assets.open(path).bufferedReader(Charsets.UTF_8).use(PatternParser::parse)
        } catch (e: IOException) {
            throw InvalidPatternException("Unable to read asset $path: ${e.message}", e)
        } catch (e: InvalidPatternException) {
            throw InvalidPatternException("Invalid asset $path: ${e.message}", e)
        }
    }
}
