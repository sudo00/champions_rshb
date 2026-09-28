package com.wineapp.presentation.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wineapp.R
import com.wineapp.data.mock.MockDataProvider
import com.wineapp.domain.model.SearchResult
import com.wineapp.presentation.common.ui.BrandButton
import com.wineapp.presentation.common.ui.BrandSecondaryButton
import com.wineapp.presentation.common.ui.TransparentSystemBars
import com.wineapp.ui.theme.BrandCream100
import com.wineapp.ui.theme.BrandCream300
import com.wineapp.ui.theme.BrandCream50
import com.wineapp.ui.theme.BrandTextPrimary
import com.wineapp.ui.theme.BrandTextSecondary
import com.wineapp.ui.theme.Inter
import com.wineapp.ui.theme.Playfair
import com.wineapp.ui.theme.WineAppTheme

@Composable
fun SearchScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()

    LaunchedEffect(true) {
        viewModel.sendIntent(SearchIntent.Search(""))
    }

    SearchScreenContent(
        state = state,
        likedIds = likedIds,
        onSearch = { query -> viewModel.sendIntent(SearchIntent.Search(query)) },
        onLoadMore = { viewModel.sendIntent(SearchIntent.LoadMore) },
        onToggleFavorite = { wineId -> viewModel.sendIntent(SearchIntent.ToggleFavorite(wineId)) },
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToGallery = onNavigateToGallery,
        onNavigateToDetail = onNavigateToDetail
    )
}

@Composable
fun SearchScreenContent(
    state: SearchState,
    likedIds: Set<String> = emptySet(),
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onNavigateToScanner: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    TransparentSystemBars()

    // Автодогрузка при скролле к концу списка. value > 0 — только после
    // реального скролла пользователя, в покое страницы не подкачиваем.
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, max) ->
                if (max > 0 && value > 0 && value >= max - 800) onLoadMore()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandCream50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            HeroBlock(
                onNavigateToScanner = onNavigateToScanner,
                onNavigateToGallery = onNavigateToGallery
            )
            PopularBlock(
                state = state,
                query = "",
                likedIds = likedIds,
                onSearch = onSearch,
                onToggleFavorite = onToggleFavorite,
                onNavigateToDetail = onNavigateToDetail
            )
            // Место под висящий поверх нижний бар.
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun HeroBlock(
    onNavigateToScanner: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(770.dp)
            .clip(RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp))
            .background(BrandCream300)
            .clipToBounds()
    ) {
        // Декоративный эллипс Cream 100 (686x741, обрезан краями).
        Box(
            modifier = Modifier
                .size(width = 686.dp, height = 741.dp)
                .offset(y = (-162).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(BrandCream100)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок.
            Text(
                stringResource(R.string.home_title),
                fontFamily = Playfair,
                fontWeight = FontWeight.SemiBold,
                fontSize = 48.sp,
                lineHeight = 54.sp,
                textAlign = TextAlign.Center,
                color = BrandTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_subtitle),
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                color = BrandTextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Hero-фото бутылки (TODO ассет bg_removal из фигмы).
            Image(
                painter = painterResource(id = R.drawable.wine_scan),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(200.dp)
                    .height(350.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.weight(1f))
            // Кнопки.
            BrandButton(
                text = stringResource(R.string.home_scan),
                onClick = onNavigateToScanner,
                leadingIcon = Icons.Default.PhotoCamera
            )
            Spacer(modifier = Modifier.height(12.dp))
            BrandSecondaryButton(
                text = stringResource(R.string.home_gallery),
                onClick = onNavigateToGallery
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PopularBlock(
    state: SearchState,
    query: String,
    likedIds: Set<String>,
    onSearch: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.home_popular),
            fontFamily = Playfair,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = BrandTextPrimary
        )
        // TODO: фильтры по вину из макета следующим этапом.
//        TextButton(
//            onClick = {},
//            contentPadding = PaddingValues(0.dp),
//        ) {
//            Text(
//                stringResource(R.string.home_filter),
//                fontFamily = Inter,
//                fontWeight = FontWeight.SemiBold,
//                fontSize = 16.sp,
//                lineHeight = 20.sp,
//                color = BrandBurgundy600
//            )
//            Spacer(modifier = Modifier.width(4.dp))
//            Icon(
//                Icons.Default.FilterList,
//                contentDescription = null,
//                tint = BrandBurgundy600,
//                modifier = Modifier.size(24.dp)
//            )
//        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    SearchResultsContent(
        state = state,
        query = query,
        onSearch = onSearch,
        onNavigateToDetail = onNavigateToDetail,
        likedIds = likedIds,
        onToggleFavorite = onToggleFavorite
    )
}


@Preview(
    showBackground = true,
    showSystemUi = true,
)
@Composable
private fun SearchScreenPreview() {
    WineAppTheme {
        SearchScreenContent(
            state = SearchState.Success(
                result = SearchResult(
                    wines = MockDataProvider.wines,
                    totalCount = MockDataProvider.wines.size,
                    page = 1,
                    hasMore = false,
                ),
                query = "",
            )
        )
    }
}
