package com.androidtim.jsapi.bridge.util

object UrlUtils {

    /**
     * Returns `true` if `url` matches `pattern`.
     */
    fun verifyUrl(
        url: String,
        pattern: String,
        patternType: PatternType,
    ): Boolean {
        if (url.isEmpty() || pattern.isEmpty()) {
            // Invalid url or pattern
            return false
        }
        return when (patternType) {
            PatternType.GLOB -> url.matches(createRegexFromGlob(pattern).toRegex())
            PatternType.REGEXP -> url.matches(pattern.toRegex())
        }
    }

    /**
     * Returns `true` if `url` matches at least one of `patterns`.
     */
    fun verifyUrl(
        url: String,
        patterns: List<String>,
        patternType: PatternType,
    ): Boolean {
        if (url.isEmpty() || patterns.isEmpty()) {
            // Invalid url or pattern
            return false
        }
        for (pattern in patterns) {
            if (verifyUrl(url, pattern, patternType)) {
                return true
            }
        }
        return false
    }

    private fun createRegexFromGlob(glob: String): String {
        val sb = StringBuilder("^")
        for (element in glob) {
            when (element) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                '.' -> sb.append("\\.")
                '\\' -> sb.append("\\\\")
                else -> sb.append(element)
            }
        }
        sb.append('$')
        return sb.toString()
    }

}
