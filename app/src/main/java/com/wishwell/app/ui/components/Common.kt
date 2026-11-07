package com.wishwell.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.formatMoney
import kotlin.math.absoluteValue

enum class BadgeTone { LOW, GOOD, NEUTRAL, HIGH }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val extra = LocalWishExtraColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = extra.muted,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun WishPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    Surface(
        modifier = modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) colors.primary else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, extra.hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) colors.onPrimary else extra.muted,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurface,
                maxLines = 1
            )
        }
    }
}

fun categoryIcon(name: String?): ImageVector = when (name?.trim()?.lowercase()) {
    "tech", "technology", "electronics", "gadgets" -> Icons.Rounded.Devices
    "home", "house", "furniture" -> Icons.Rounded.Home
    "fashion", "clothing", "clothes", "style" -> Icons.Rounded.Checkroom
    "fitness", "sports", "gym", "health" -> Icons.Rounded.FitnessCenter
    "gifts", "gift" -> Icons.Rounded.CardGiftcard
    else -> Icons.Rounded.Category
}

@Composable
fun DealBadge(text: String, tone: BadgeTone = BadgeTone.GOOD) {
    val extra = LocalWishExtraColors.current
    val background = when (tone) {
        BadgeTone.LOW -> extra.lime
        BadgeTone.GOOD -> extra.moss
        BadgeTone.HIGH -> extra.terracotta
        BadgeTone.NEUTRAL -> extra.sunken
    }
    val foreground = when (tone) {
        BadgeTone.LOW -> extra.limeInk
        BadgeTone.GOOD -> Color(0xFFFFFCF4)
        BadgeTone.HIGH -> Color(0xFFFFFCF4)
        BadgeTone.NEUTRAL -> extra.muted
    }
    Surface(shape = RoundedCornerShape(6.dp), color = background) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ProductThumb(
    imageUrl: String?,
    storeName: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
) {
    val extra = LocalWishExtraColors.current
    val palette = listOf(
        Color(0xFFC9D6B6),
        Color(0xFFE7CDB8),
        Color(0xFFD8CBB3),
        Color(0xFFC3CFD2),
        Color(0xFFDCC9D4),
        Color(0xFFD4D2B6)
    )
    val base = palette[((storeName.hashCode() % palette.size) + palette.size) % palette.size]
    val letter = storeName.trim().firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, base.copy(alpha = 0.55f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.displayMedium,
            color = extra.muted.copy(alpha = 0.45f)
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductImageCarousel(
    imageUrls: List<String>,
    storeName: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
) {
    val urls = imageUrls.filter { it.isNotBlank() }.distinct()
    if (urls.size <= 1) {
        ProductThumb(
            imageUrl = urls.firstOrNull(),
            storeName = storeName,
            modifier = modifier,
            shape = shape
        )
        return
    }
    val pagerState = rememberPagerState(pageCount = { urls.size })
    Box(modifier = modifier.clip(shape)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ProductThumb(
                imageUrl = urls[page],
                storeName = storeName,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(urls.size.coerceAtMost(10)) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (selected) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White else Color.White.copy(alpha = 0.55f)
                        )
                )
            }
            if (urls.size > 10) {
                Text(
                    text = "+${urls.size - 10}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PriceRangeMeter(item: ItemWithStats, modifier: Modifier = Modifier) {
    val extra = LocalWishExtraColors.current
    val low = item.lowest
    val high = item.highest
    val position = item.rangePosition

    if (low == null || high == null || position == null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No price range yet",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Low ${formatMoney(low, item.item.currencyCode)}",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
            Text(
                "High ${formatMoney(high, item.item.currencyCode)}",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
        }
        Spacer(Modifier.height(5.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val centerY = size.height / 2f
            drawLine(
                color = extra.hairline,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = extra.moss.copy(alpha = 0.55f),
                start = Offset(0f, centerY),
                end = Offset(size.width * position, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawCircle(
                color = extra.moss,
                radius = 4.dp.toPx(),
                center = Offset(
                    (size.width - 8.dp.toPx()) * position + 4.dp.toPx(),
                    centerY
                )
            )
        }
    }
}

@Composable
fun WishCard(item: ItemWithStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val price = item.price
    val listPrice = item.item.listPrice
    val belowTarget = item.item.targetPrice?.let { t -> price != null && price <= t + 0.005 } == true

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, extra.hairline)
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            ProductThumb(
                imageUrl = item.item.imageUrl,
                storeName = item.item.storeName,
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.item.storeName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val badge = when {
                        item.isAtLowest -> "Low" to BadgeTone.LOW
                        belowTarget -> "Target" to BadgeTone.GOOD
                        item.discountPercent != null && item.discountPercent!! >= 1 ->
                            "-${item.discountPercent}%" to BadgeTone.GOOD
                        item.item.lastCheckFailed -> "Check failed" to BadgeTone.HIGH
                        else -> null
                    }
                    if (badge != null) DealBadge(badge.first, badge.second)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatMoney(price, item.item.currencyCode),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        maxLines = 1
                    )
                    if (listPrice != null && price != null && listPrice > price + 0.01) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatMoney(listPrice, item.item.currencyCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                            maxLines = 1
                        )
                    }
                }
                if (item.lowest != null && item.highest != null && item.rangePosition != null) {
                    Spacer(Modifier.height(8.dp))
                    PriceRangeMeter(item)
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    caption: String? = null,
    modifier: Modifier = Modifier
) {
    val extra = LocalWishExtraColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = extra.sunken.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }
    }
}

@Composable
fun NoticeBar(text: String, isError: Boolean = false, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isError) colors.error.copy(alpha = 0.10f) else extra.sunken,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = if (isError) colors.error else extra.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val extra = LocalWishExtraColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(extra.lime),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = extra.limeInk,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onAction,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(actionLabel)
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, color = extra.muted)
            }
        }
    }
}

@Composable
fun WishDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = colors.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface
            )
        },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) colors.error else colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = extra.muted)
            }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    WishDialog(
        title = title,
        confirmLabel = confirmLabel,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = 2.dp,
        color = color
    )
}

/** Dashed divider used inside empty charts. */
fun dashedEffect(@Suppress("UNUSED_PARAMETER") color: Color): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

@Composable
fun DashedRule(modifier: Modifier = Modifier) {
    val extra = LocalWishExtraColors.current
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = extra.hairline,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashedEffect(extra.hairline)
        )
    }
}

@Composable
fun IconCircleButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val extra = LocalWishExtraColors.current
    Surface(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, extra.hairline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Small dashed placeholder drawn when there is not enough history yet. */
@Composable
fun ChartPlaceholder(text: String, modifier: Modifier = Modifier) {
    val extra = LocalWishExtraColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.58f
            drawLine(
                color = extra.hairline,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashedEffect(extra.hairline)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}



