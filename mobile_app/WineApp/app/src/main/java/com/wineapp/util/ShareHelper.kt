package com.wineapp.util

import android.content.Context
import android.content.Intent
import com.wineapp.domain.model.SommelierMessage
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.sommelier.ChatMessage

object ShareHelper {

    fun shareWine(context: Context, wine: Wine) {
        val text = buildWineText(wine)
        launchShare(context, text)
    }

    fun shareConversation(
        context: Context,
        wine: Wine,
        messages: List<SommelierMessage>
    ) {
        val text = buildConversationText(wine, messages)
        launchShare(context, text)
    }

    fun shareConversationFromChat(
        context: Context,
        wineName: String,
        messages: List<ChatMessage>
    ) {
        val text = buildConversationTextFromChat(wineName, messages)
        launchShare(context, text)
    }

    private fun launchShare(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun buildWineText(wine: Wine): String {
        return buildString {
            appendLine("Вино: ${wine.name} (${wine.vintage ?: "н.v."})")
            wine.winery?.let { appendLine("Винодельня: $it") }
            appendLine("Рейтинг: ${wine.rating?.let { formatRating(it, decimals = 1) } ?: "—"} (${wine.reviewsCount} отзывов)")
            wine.region?.let { appendLine("Регион: $it") }
            wine.country?.let { appendLine("Страна: $it") }
            wine.variety?.let { appendLine("Сорт: $it") }
            wine.style?.let { appendLine("Стиль: $it") }
            wine.alcoholPercentage?.let { appendLine("Крепость: ${it}%") }
            wine.price?.let { appendLine("Цена: ${it} ${wine.currency ?: ""}") }
            wine.description?.let { appendLine("\n$it") }
            if (wine.foodPairing.isNotEmpty()) {
                appendLine("\nГармонирует с: ${wine.foodPairing.joinToString(", ")}")
            }
        }.trim()
    }

    private fun buildConversationText(
        wine: Wine,
        messages: List<SommelierMessage>
    ): String {
        return buildString {
            appendLine("Вино: ${wine.name} (${wine.vintage ?: "н.v."})")
            wine.region?.let { appendLine("Регион: $it") }
            appendLine()
            appendLine("--- Беседа с ИИ-сомелье ---")
            appendLine()
            for (msg in messages) {
                val role = if (msg.role == "user") "Вы" else "Сомелье"
                appendLine("$role: ${msg.content}")
                appendLine()
            }
            appendLine("--- Конец беседы ---")
        }.trim()
    }

    private fun buildConversationTextFromChat(
        wineName: String,
        messages: List<ChatMessage>
    ): String {
        return buildString {
            appendLine("Вино: $wineName")
            appendLine()
            appendLine("--- Беседа с ИИ-сомелье ---")
            appendLine()
            for (msg in messages) {
                val role = if (msg.isUser) "Вы" else "Сомелье"
                appendLine("$role: ${msg.text}")
                appendLine()
            }
            appendLine("--- Конец беседы ---")
        }.trim()
    }
}
