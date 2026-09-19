package com.wineapp.domain.repository

import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.WineContext

interface SommelierRepository {
    suspend fun sendMessage(
        messages: List<SommelierMessage>,
        wineContext: WineContext?
    ): Result<SommelierMessage>
}
