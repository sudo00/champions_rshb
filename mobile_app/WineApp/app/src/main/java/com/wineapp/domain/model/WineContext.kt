package com.wineapp.domain.model

data class WineContext(
    val wineId: String,
    val wineName: String,
    val region: String? = null,
    val variety: String? = null,
    val vintage: Int? = null,
    val rating: Float? = null,
    val style: String? = null
)
