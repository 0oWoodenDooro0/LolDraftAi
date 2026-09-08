package com.loldraft.client.compose.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.analytics.model.SortDirection
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary

data class DataGridColumn<T>(
    val id: String,
    val title: String,
    val width: Dp,
    val alignment: TextAlign = TextAlign.Start,
    val isSortable: Boolean = true,
    val cellContent: @Composable (item: T) -> Unit,
)

@Composable
fun <T> ExcelDataGrid(
    columns: List<DataGridColumn<T>>,
    items: List<T>,
    currentSortColumn: String,
    currentSortDirection: SortDirection,
    onSortColumn: (String) -> Unit,
    modifier: Modifier = Modifier,
    summaryContent: (@Composable () -> Unit)? = null,
) {
    val hScrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
    ) {
        // Table with Horizontal and Vertical Scrolling
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScrollState),
            ) {
                // Sticky Header Row
                Row(
                    modifier =
                        Modifier
                            .background(CardDark)
                            .height(40.dp)
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    columns.forEach { col ->
                        val isSorted = col.id.equals(currentSortColumn, ignoreCase = true)
                        val sortIndicator =
                            if (isSorted) {
                                if (currentSortDirection == SortDirection.ASCENDING) " ▲" else " ▼"
                            } else {
                                ""
                            }

                        Box(
                            modifier =
                                Modifier
                                    .width(col.width)
                                    .clickable(enabled = col.isSortable) {
                                        onSortColumn(col.id)
                                    }
                                    .padding(horizontal = 6.dp),
                            contentAlignment =
                                when (col.alignment) {
                                    TextAlign.End -> Alignment.CenterEnd
                                    TextAlign.Center -> Alignment.Center
                                    else -> Alignment.CenterStart
                                },
                        ) {
                            Text(
                                text = "${col.title}$sortIndicator",
                                color = if (isSorted) GoldAccent else TextPrimary,
                                fontWeight = if (isSorted) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = col.alignment,
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderDark, thickness = 1.dp)

                // Scrollable Table Body
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("無符合篩選條件之數據", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(items) { index, item ->
                            val isEven = index % 2 == 0
                            val rowBg = if (isEven) SurfaceDark else CardDark.copy(alpha = 0.5f)

                            Row(
                                modifier =
                                    Modifier
                                        .background(rowBg)
                                        .height(36.dp)
                                        .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                columns.forEach { col ->
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(col.width)
                                                .padding(horizontal = 6.dp),
                                        contentAlignment =
                                            when (col.alignment) {
                                                TextAlign.End -> Alignment.CenterEnd
                                                TextAlign.Center -> Alignment.Center
                                                else -> Alignment.CenterStart
                                            },
                                    ) {
                                        col.cellContent(item)
                                    }
                                }
                            }
                            HorizontalDivider(color = BorderDark.copy(alpha = 0.3f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Summary Bar (Excel-like status bar)
        if (summaryContent != null) {
            HorizontalDivider(color = BorderDark, thickness = 1.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(CardDark)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                summaryContent()
            }
        }
    }
}
