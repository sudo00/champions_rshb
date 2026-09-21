package com.wineapp.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFoodPairingList(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun toFoodPairingList(jsonString: String): List<String> {
        return json.decodeFromString(jsonString)
    }
}