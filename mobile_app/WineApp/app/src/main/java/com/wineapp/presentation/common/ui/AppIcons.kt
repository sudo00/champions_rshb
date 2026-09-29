package com.wineapp.presentation.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.wineapp.R

/**
 * Фирменные иконки из res/drawable как ImageVector — drop-in замена
 * Icons.Default.* в Icon(...), leadingIcon и т.п. Цвет задаётся tint'ом.
 */
object AppIcons {
    val AiStar: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ai_star)
    val AiStarFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ai_star_filled)
    val Star: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.star_filled)
    val StarOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.star)
    val Heart: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.heart)
    val HeartFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.heart_filled)
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.close_cross)
    val Filter: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.filter)
    val Plus: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.plus)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.check)
    val ChevronLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.chevron_left)
    val ChevronRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.chevron_right)
    val ChevronUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.chevron_up)
    /** Стрелка вверх — «отправить» в полях вопроса сомелье. */
    val ArrowUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.arrow_up)
    val ChevronDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.down)
    val Camera: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.photo_camera)
    val CameraFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.camera_filled)
    val Gallery: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.photo)
    val Burger: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.burger)
    val Alert: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.alert_sign)
    val Dislike: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.dislike)
    val WineBottle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.wine_bottle)
    val Grape: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.grape)
    val MapMarker: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.map_marker)
    val MapMarkerFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.map_marker_filled)
    val MyLocation: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.my_location)
    val MyLocationFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.my_location_filled)
    val Rotate: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.rotate_360)
    val Sort: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sort_down)
    val Lightning: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lightning)
    val LightningFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lightning_filled)
    val LightningOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lightning_off)
    val Play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.play)
    val Trash: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.trash)
    val Bookmark: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.bookmark)
    val BookmarkFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.bookmark_filled)
    val Home: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.home)
    val HomeFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.home_filled)
    val Search: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.search)
    val SearchFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.search_filled)
    val Question: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.question)
}
