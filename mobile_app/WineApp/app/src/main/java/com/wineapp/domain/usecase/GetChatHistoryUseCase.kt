package com.wineapp.domain.usecase

import com.wineapp.domain.model.ChatHistoryItem
import com.wineapp.domain.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatHistoryUseCase @Inject constructor(
    private val repository: ScanHistoryRepository
) {
    operator fun invoke(): Flow<List<ChatHistoryItem>> {
        return repository.getChatsHistory()
    }
}
