package com.wineapp.domain.usecase

import android.util.Log
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.WineContext
import com.wineapp.domain.repository.SommelierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SendSommelierMessageUseCase @javax.inject.Inject constructor(
    private val repository: SommelierRepository
) {
    suspend operator fun invoke(
        messages: List<SommelierMessage>,
        wineContext: WineContext?
    ): Result<SommelierMessage> {
        Log.i("SendSommelierMessageUseCase", "Sending message to sommelier (${messages.size} messages)")
        return withContext(Dispatchers.IO) {
            repository.sendMessage(messages, wineContext)
        }
    }
}
