package com.webclass.plus.data.models

data class PreStartInfo(
    val type: String,
    val title: String,
    val restrictTerms: List<String> = emptyList(),
    val absoluteTerms: List<String> = emptyList(),
    val hasStart: Boolean = false,
    val hasEnd: Boolean = false
)
