package com.androidtim.jsapisample

data class ArticleInfo(
    val feedback: Feedback? = null,
)

enum class Feedback {
    LIKED, DISLIKED
}
