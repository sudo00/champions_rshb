package com.wineapp.data.mock

import com.wineapp.domain.model.ScanResult
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine

object MockDataProvider {

    val wines = listOf(
        Wine(
            id = "1",
            name = "Chateau Margaux",
            vintage = 2018,
            rating = 4.7f,
            reviewsCount = 2341,
            price = 450.0,
            currency = "$",
            region = "Bordeaux",
            country = "France",
            variety = "Cabernet Sauvignon",
            style = "Dry Red",
            alcoholPercentage = 13.5f,
            imageUrl = "https://images.unsplash.com/photo-1586370434639-0fe43b2d32e6?w=400",
            description = "A majestic wine with deep ruby color, aromas of blackcurrant, violet, and cedar. Silky tannins lead to an exceptionally long finish.",
            foodPairing = listOf("Lamb", "Aged Cheese", "Beef", "Dark Chocolate"),
            winery = "Chateau Margaux"
        ),
        Wine(
            id = "2",
            name = "Opus One",
            vintage = 2019,
            rating = 4.8f,
            reviewsCount = 1876,
            price = 380.0,
            currency = "$",
            region = "Napa Valley",
            country = "USA",
            variety = "Merlot Blend",
            style = "Dry Red",
            alcoholPercentage = 14.5f,
            imageUrl = "https://images.unsplash.com/photo-1566995541428-f2246c17cda1?w=400",
            description = "Elegant and complex with notes of dark cherry, vanilla, and tobacco. Beautifully balanced with refined tannins.",
            foodPairing = listOf("Steak", "Mushroom Risotto", "Aged Parmesan"),
            winery = "Opus One Winery"
        ),
        Wine(
            id = "3",
            name = "Cloudy Bay Sauvignon Blanc",
            vintage = 2022,
            rating = 4.3f,
            reviewsCount = 3102,
            price = 25.0,
            currency = "$",
            region = "Marlborough",
            country = "New Zealand",
            variety = "Sauvignon Blanc",
            style = "Dry White",
            alcoholPercentage = 13.0f,
            imageUrl = "https://images.unsplash.com/photo-1474722883778-792e7990302f?w=400",
            description = "Vibrant and refreshing with intense aromas of passion fruit, lime, and fresh-cut grass. Crisp acidity on the palate.",
            foodPairing = listOf("Seafood", "Goat Cheese", "Salads", "Sushi"),
            winery = "Cloudy Bay"
        ),
        Wine(
            id = "4",
            name = "Penfolds Grange",
            vintage = 2017,
            rating = 4.9f,
            reviewsCount = 987,
            price = 750.0,
            currency = "$",
            region = "South Australia",
            country = "Australia",
            variety = "Shiraz",
            style = "Dry Red",
            alcoholPercentage = 14.5f,
            imageUrl = "https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?w=400",
            description = "Australia's most celebrated wine. Dense and powerful with layers of dark fruit, spice, and chocolate.",
            foodPairing = listOf("BBQ Ribs", "Venison", "Dark Chocolate Desserts"),
            winery = "Penfolds"
        ),
        Wine(
            id = "5",
            name = "Moët & Chandon Brut Impérial",
            vintage = null,
            rating = 4.2f,
            reviewsCount = 5432,
            price = 45.0,
            currency = "$",
            region = "Champagne",
            country = "France",
            variety = "Chardonnay/Pinot Noir",
            style = "Sparkling",
            alcoholPercentage = 12.0f,
            imageUrl = "https://images.unsplash.com/photo-1592841200221-a6898f307baa?w=400",
            description = "The world's most loved champagne. Bright fruit flavors, elegant mousse, and a crisp finish.",
            foodPairing = listOf("Oysters", "Sushi", "Fruit Desserts", "Celebrations"),
            winery = "Moët & Chandon"
        ),
        Wine(
            id = "6",
            name = "Barolo Riserva DOCG",
            vintage = 2016,
            rating = 4.6f,
            reviewsCount = 743,
            price = 120.0,
            currency = "$",
            region = "Piedmont",
            country = "Italy",
            variety = "Nebbiolo",
            style = "Dry Red",
            alcoholPercentage = 14.0f,
            imageUrl = "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=400",
            description = "The king of wines. Tar and roses aromas with firm tannins and exceptional aging potential.",
            foodPairing = listOf("Truffle Pasta", "Braised Beef", "Aged Hard Cheese"),
            winery = "Marchesi di Barolo"
        )
    )

    fun mockScanResult(): ScanResult {
        val main = wines.random()
        val others = wines.filter { it.id != main.id }.shuffled().take(3)
        return ScanResult(
            wine = main,
            confidence = 0.87f + Math.random().toFloat() * 0.1f,
            matches = listOf(main) + others
        )
    }

    fun mockSearchResult(query: String, page: Int = 1): SearchResult {
        val filtered = wines.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.region?.contains(query, ignoreCase = true) == true ||
            it.variety?.contains(query, ignoreCase = true) == true ||
            it.winery?.contains(query, ignoreCase = true) == true
        }.ifEmpty { wines }

        return SearchResult(
            wines = filtered,
            totalCount = filtered.size,
            page = page,
            hasMore = false
        )
    }

    fun mockWineDetail(id: String): Wine =
        wines.find { it.id == id } ?: wines.random()
}
