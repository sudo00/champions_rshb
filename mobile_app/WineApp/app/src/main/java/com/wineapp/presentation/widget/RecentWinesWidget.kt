package com.wineapp.presentation.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wineapp.MainActivity
import com.wineapp.R
import com.wineapp.domain.model.SavedScan
import dagger.hilt.EntryPoints
import java.util.Locale

/**
 * Виджет рабочего стола в фирменной светлой гамме: кремовая карточка,
 * заголовок с бургунди-точкой, строки вин с разделителями, бургунди-кнопка.
 * Источник данных — scan_history (снимок вина хранится локально, сеть не нужна).
 * Оценка каталога (0–5) показывается в шкале /10, как раньше.
 */
class RecentWinesWidget : GlanceAppWidget() {

    /**
     * Несколько поддерживаемых размеров — обязательно для растягивания:
     * в режиме Exact виджет привязан к одному размеру и лаунчер не даёт его тянуть.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            androidx.compose.ui.unit.DpSize(120.dp, 110.dp),
            androidx.compose.ui.unit.DpSize(180.dp, 110.dp),
            androidx.compose.ui.unit.DpSize(180.dp, 220.dp),
            androidx.compose.ui.unit.DpSize(250.dp, 180.dp),
            androidx.compose.ui.unit.DpSize(250.dp, 320.dp),
            androidx.compose.ui.unit.DpSize(350.dp, 180.dp),
            androidx.compose.ui.unit.DpSize(350.dp, 320.dp),
            androidx.compose.ui.unit.DpSize(500.dp, 220.dp),
            androidx.compose.ui.unit.DpSize(500.dp, 400.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scans = loadRecentScans(context, MAX_ITEMS)
        val title = context.getString(R.string.app_name)
        val scanLabel = context.getString(R.string.widget_scan)
        val emptyLabel = context.getString(R.string.widget_recent_empty)
        provideContent {
            WidgetContent(
                title = title,
                scanLabel = scanLabel,
                emptyLabel = emptyLabel,
                scans = scans
            )
        }
    }

    private suspend fun loadRecentScans(context: Context, limit: Int): List<SavedScan> {
        return try {
            val entryPoint = EntryPoints.get(context.applicationContext, WidgetEntryPoint::class.java)
            entryPoint.scanHistoryRepository().getRecentScans(limit).getOrNull() ?: emptyList()
        } catch (e: Exception) {
            Log.e("RecentWinesWidget", "Load recent scans failed", e)
            emptyList()
        }
    }

    companion object {
        const val MAX_ITEMS = 3
        const val EXTRA_DESTINATION = "widget_destination"
        const val EXTRA_WINE_ID = "widget_wine_id"
        const val DEST_SCANNER = "scanner"
        const val DEST_DETAIL = "detail"

        private val destinationKey = ActionParameters.Key<String>(EXTRA_DESTINATION)
        private val wineIdKey = ActionParameters.Key<String>(EXTRA_WINE_ID)

        fun openScannerAction() = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(destinationKey to DEST_SCANNER)
        )

        fun openDetailAction(wineId: String) = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(
                destinationKey to DEST_DETAIL,
                wineIdKey to wineId
            )
        )
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun WidgetContent(
    title: String,
    scanLabel: String,
    emptyLabel: String,
    scans: List<SavedScan>
) {
    // Сколько строк вин влезает в текущую высоту виджета (важно при растягивании/сжатии).
    val maxRows = when {
        LocalSize.current.height < 200.dp -> 1
        LocalSize.current.height < 280.dp -> 2
        else -> RecentWinesWidget.MAX_ITEMS
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_bg))
            .cornerRadius(28.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(ColorProvider(R.color.widget_dot))
                    .cornerRadius(4.dp)
            ) {}
        }
        Spacer(modifier = GlanceModifier.height(12.dp))
        if (scans.isEmpty()) {
            Text(
                text = emptyLabel,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_wine_region),
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
        } else {
            val rows = scans.take(maxRows)
            rows.forEachIndexed { index, scan ->
                WidgetWineRow(scan = scan)
                if (index < rows.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorProvider(R.color.widget_divider))
                    ) {}
                    Spacer(modifier = GlanceModifier.height(10.dp))
                } else {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                }
            }
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(R.color.widget_button_bg))
                .cornerRadius(14.dp)
                .padding(vertical = 12.dp)
                .clickable(RecentWinesWidget.openScannerAction()),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_scan),
                    contentDescription = scanLabel,
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = scanLabel,
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_button_text),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun WidgetWineRow(scan: SavedScan) {
    val wine = scan.wine
    val title = if (wine.vintage != null) "${wine.name} ${wine.vintage}" else wine.name
    val subtitle = listOfNotNull(wine.region, wine.country).joinToString(", ")
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(RecentWinesWidget.openDetailAction(wine.id)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_wine_name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_wine_region),
                        fontSize = 13.sp
                    )
                )
            }
        }
        wine.rating?.let { rating ->
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = String.format(Locale.US, "%.1f", rating * 2f),
                style = TextStyle(
                    color = ColorProvider(R.color.widget_rating),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }
    }
}
