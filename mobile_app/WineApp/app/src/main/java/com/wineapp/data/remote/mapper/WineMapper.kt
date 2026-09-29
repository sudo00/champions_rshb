package com.wineapp.data.remote.mapper

import com.wineapp.data.remote.dto.WineDto
import com.wineapp.domain.model.Wine
import com.wineapp.domain.model.ratingOrDemo

object WineMapper {
    fun toDomain(dto: WineDto): Wine {
        return Wine(
            id = dto.id,
            name = dto.name,
            vintage = dto.vintage,
            rating = ratingOrDemo(dto.rating, dto.id),
            reviewsCount = dto.reviewsCount,
            price = dto.price,
            currency = dto.currency,
            region = dto.region?.name,
            country = dto.country?.name,
            variety = dto.variety?.name,
            style = dto.style?.name,
            alcoholPercentage = dto.alcoholPercentage,
            imageUrl = dto.imageUrl,
            description = dto.description,
            foodPairing = dto.foodPairing,
            winery = dto.winery?.name
        )
    }

    fun toDomainList(dtos: List<WineDto>): List<Wine> {
        return dtos.map { toDomain(it) }
    }
}