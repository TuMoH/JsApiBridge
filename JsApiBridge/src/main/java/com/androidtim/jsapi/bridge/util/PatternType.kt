package com.androidtim.jsapi.bridge.util

enum class PatternType {
    /**
     * Glob pattern. May be a wildcard pattern such as `*.google.*`.
     */
    GLOB,

    /**
     * Typical RegExp.
     */
    REGEXP,
}
