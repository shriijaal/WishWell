package com.wishwell.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wishwell.app.di.AppContainer
import com.wishwell.app.ui.add.AddItemScreen
import com.wishwell.app.ui.collections.CollectionsScreen
import com.wishwell.app.ui.detail.ItemDetailRoute
import com.wishwell.app.ui.edit.EditItemScreen
import com.wishwell.app.ui.settings.SettingsScreen
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.ui.tracking.PriceTrackingScreen
import com.wishwell.app.ui.wishlist.WishlistScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    TopLevelDestination("wishlist", "Wishlist", Icons.Rounded.Bookmarks),
    TopLevelDestination("tracking", "Tracking", Icons.AutoMirrored.Rounded.TrendingDown),
    TopLevelDestination("collections", "Collections", Icons.Rounded.Category),
    TopLevelDestination("settings", "Settings", Icons.Rounded.Tune)
)

@Composable
fun WishwellRoot(
    container: AppContainer,
    startUrl: String?,
    startItemId: Long?,
    onStartConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute == null || currentRoute in setOf("wishlist", "tracking", "collections", "settings")
    val colors = MaterialTheme.colorScheme

    val navbarStyle by container.prefs.navbarStyle.collectAsStateWithLifecycle(initialValue = "classic")
    val navbarGlassGlow by container.prefs.navbarGlassGlow.collectAsStateWithLifecycle(initialValue = true)
    var navCompact by remember { mutableStateOf(false) }
    var navScrollAccumulator by remember { mutableFloatStateOf(0f) }
    var wishlistSelectionMode by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        navCompact = false
        navScrollAccumulator = 0f
        if (currentRoute != "wishlist") wishlistSelectionMode = false
    }
    val effectiveCompact = when (navbarStyle) {
        "glass_compact" -> true
        "glass_expanded" -> false
        "glass_adaptive", "liquid_glass" -> navCompact
        else -> false
    }
    val navNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                navScrollAccumulator = (navScrollAccumulator + delta).coerceIn(-200f, 200f)
                if (delta < -8f && navScrollAccumulator < -24f) navCompact = true
                if (delta > 8f && navScrollAccumulator > -8f) navCompact = false
                if (delta > 12f) navCompact = false
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(startUrl, startItemId) {
        when {
            startItemId != null -> {
                navController.navigate("item/$startItemId") { launchSingleTop = true }
                onStartConsumed()
            }

            startUrl != null -> {
                navController.navigate("add?url=${Uri.encode(startUrl)}")
                onStartConsumed()
            }
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showChrome) {
                WishBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = ::navigateTopLevel,
                    style = navbarStyle,
                    compact = effectiveCompact,
                    glow = navbarGlassGlow
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == "wishlist" && !wishlistSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate("add") },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = CircleShape,
                    icon = {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    text = { Text("Save a want") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "wishlist",
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(navNestedScrollConnection)
        ) {
            composable("wishlist") {
                WishlistScreen(
                    container = container,
                    onOpenItem = { navController.navigate("item/$it") },
                    onAddItem = { navController.navigate("add") },
                    onEditItem = { navController.navigate("edit/$it") { launchSingleTop = true } },
                    onSelectionModeChanged = { wishlistSelectionMode = it }
                )
            }

            composable("tracking") {
                PriceTrackingScreen(
                    container = container,
                    onOpenItem = { navController.navigate("item/$it") },
                    onAddItem = { navController.navigate("add") },
                    onEditItem = { navController.navigate("edit/$it") { launchSingleTop = true } }
                )
            }

            composable("collections") {
                CollectionsScreen(container = container)
            }

            composable("settings") {
                SettingsScreen(container = container)
            }

            composable(
                route = "add?url={url}",
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                AddItemScreen(
                    container = container,
                    initialUrl = entry.arguments?.getString("url").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                        navController.navigate("item/$id") { launchSingleTop = true }
                    }
                )
            }

            composable(
                route = "item/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { entry ->
                val itemId = entry.arguments?.getLong("itemId") ?: 0L
                ItemDetailRoute(
                    container = container,
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onOpenItem = { navController.navigate("item/$it") { launchSingleTop = true } },
                    onTrackProduct = { url -> navController.navigate("add?url=${Uri.encode(url)}") },
                    onEdit = { navController.navigate("edit/$itemId") { launchSingleTop = true } }
                )
            }

            composable(
                route = "edit/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { entry ->
                val itemId = entry.arguments?.getLong("itemId") ?: 0L
                EditItemScreen(
                    container = container,
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }

}

@Composable
private fun WishBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    style: String = "classic",
    compact: Boolean = false,
    glow: Boolean = true
) {
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val isGlass = style != "classic"

    if (isGlass) {
        val pillFraction by animateFloatAsState(
            targetValue = if (compact) 0.62f else 1f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
            label = "navPillWidth"
        )
        val outerHPad by animateDpAsState(
            targetValue = if (compact) 22.dp else 14.dp,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
            label = "navOuterPad"
        )
        val rowVPad by animateDpAsState(
            targetValue = if (compact) 6.dp else 8.dp,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
            label = "navRowPad"
        )
        val rowHPad by animateDpAsState(
            targetValue = if (compact) 6.dp else 8.dp,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
            label = "navRowHPad"
        )
        val itemGap by animateDpAsState(
            targetValue = if (compact) 2.dp else 4.dp,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
            label = "navGap"
        )
        val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = outerHPad, end = outerHPad, top = 6.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(pillFraction),
                contentAlignment = Alignment.Center
            ) {
                if (glow) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.primary.copy(alpha = 0.22f))
                            .blur(16.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surface.copy(alpha = 0.78f),
                    tonalElevation = 0.dp,
                    shadowElevation = if (glow) 8.dp else 14.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f),
                                extra.hairline.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rowHPad, vertical = rowVPad)
                ) {
                    val count = destinations.size.coerceAtLeast(1)
                    val totalGap = itemGap * (count - 1)
                    val itemWidth = ((maxWidth - totalGap) / count).coerceAtLeast(0.dp)
                    val targetX = itemGap * selectedIndex + itemWidth * selectedIndex
                    val indicatorHeight = maxHeight

                    // Liquid sliding indicator
                    val indicatorX by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f),
                        label = "navIndicatorX"
                    )
                    val indicatorWidth by animateDpAsState(
                        targetValue = itemWidth,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
                        label = "navIndicatorW"
                    )

                    // Indicator behind items
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(indicatorX.roundToPx(), 0) }
                                .size(width = indicatorWidth, height = indicatorHeight)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            colors.primary.copy(alpha = 0.18f),
                                            colors.primary.copy(alpha = 0.12f),
                                            colors.primary.copy(alpha = 0.18f)
                                        )
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(itemGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        destinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            val contentColor by animateColorAsState(
                                targetValue = if (selected) colors.primary else extra.muted,
                                animationSpec = tween(180),
                                label = "navFg-${destination.route}"
                            )
                            val itemVPad by animateDpAsState(
                                targetValue = if (compact) 10.dp else 8.dp,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                                label = "navItemPad-${destination.route}"
                            )
                            val bounceScale = remember(destination.route) { Animatable(1f) }
                            LaunchedEffect(selected) {
                                if (selected) {
                                    bounceScale.snapTo(0.86f)
                                    bounceScale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(dampingRatio = 0.38f, stiffness = 520f)
                                    )
                                } else {
                                    bounceScale.snapTo(1f)
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onNavigate(destination.route) }
                                    .padding(vertical = itemVPad, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .scale(bounceScale.value)
                                )
                                AnimatedVisibility(
                                    visible = !compact,
                                    enter = fadeIn(tween(160)) + expandVertically(
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
                                        expandFrom = Alignment.Top
                                    ),
                                    exit = fadeOut(tween(120)) + shrinkVertically(
                                        animationSpec = tween(140),
                                        shrinkTowards = Alignment.Top
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = destination.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 0.2.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                            ),
                                            color = contentColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    } else {
        Column(modifier = Modifier.background(colors.surface)) {
            HorizontalDivider(color = extra.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val bounceScale = remember(destination.route) { Animatable(1f) }
                    LaunchedEffect(selected) {
                        if (selected) {
                            bounceScale.snapTo(0.86f)
                            bounceScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.38f, stiffness = 520f)
                            )
                        } else {
                            bounceScale.snapTo(1f)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigate(destination.route) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) colors.primary else extra.muted,
                            modifier = Modifier
                                .size(21.dp)
                                .scale(bounceScale.value)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                            color = if (selected) colors.primary else extra.muted
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (selected) colors.primary else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
