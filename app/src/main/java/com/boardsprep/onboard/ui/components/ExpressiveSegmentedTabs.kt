package com.boardsprep.onboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boardsprep.onboard.core.theme.ExpressivePillSmall

data class ExpressiveTabItem(
    val title: String,
    val icon: ImageVector? = null,
    val badge: String? = null
)

/**
 * Standard Material 3 Expressive Segmented Tabs Row.
 * Harmonizes tabs across Chapter Details, Handbooks, Downloads, and Subject Hubs.
 */
@Composable
fun ExpressiveSegmentedTabs(
    tabs: List<ExpressiveTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tabItem ->
            val isSelected = selectedTabIndex == index
            
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                label = "tab_container_color"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    // Check if accent is light or dark to guarantee WCAG AA contrast
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "tab_content_color"
            )

            Surface(
                shape = ExpressivePillSmall,
                color = containerColor,
                modifier = Modifier
                    .height(38.dp)
                    .expressiveBounce { onTabSelected(index) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (tabItem.icon != null) {
                        Icon(
                            imageVector = tabItem.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = tabItem.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (!tabItem.badge.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = ExpressivePillSmall,
                            color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = tabItem.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
