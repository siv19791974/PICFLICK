package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.picflick.app.data.Flick
import com.picflick.app.data.UserProfile
import com.picflick.app.util.withCacheBust
import com.picflick.app.viewmodel.HomeViewModel

/**
 * Explore page for discovering trending and popular photos
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ExploreScreen(
    userProfile: UserProfile,
    viewModel: HomeViewModel,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ExploreTab.TRENDING) }
    
    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadExploreFlicks() }
    )

    // Load explore data
    LaunchedEffect(Unit) {
        viewModel.loadExploreFlicks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // NO BANNER - banner is in MainActivity

        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { 
                searchQuery = it
                if (it.isEmpty()) {
                    viewModel.clearExploreSearch()
                }
            },
            onSearch = { 
                viewModel.searchExploreFlicks(searchQuery)
            },
            modifier = Modifier.padding(16.dp)
        )

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                if (selectedTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Color(0xFFD7ECFF)
                    )
                }
            }
        ) {
            ExploreTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.label,
                            color = if (selectedTab == tab) Color.White else Color.Gray
                        )
                    }
                )
            }
        }

        // Modern PullRefresh content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when (selectedTab) {
                ExploreTab.TRENDING -> TrendingContent(
                    flicks = viewModel.exploreFlicks,
                    onPhotoClick = onPhotoClick,
                    onUserClick = onUserClick
                )
                ExploreTab.POPULAR -> PopularContent(
                    flicks = viewModel.exploreFlicks.sortedByDescending { it.getTotalReactions() },
                    onPhotoClick = onPhotoClick,
                    onUserClick = onUserClick
                )
                ExploreTab.NEW -> NewContent(
                    flicks = viewModel.exploreFlicks.sortedByDescending { it.timestamp },
                    onPhotoClick = onPhotoClick,
                    onUserClick = onUserClick
                )
                ExploreTab.FOR_YOU -> ForYouContent(
                    userProfile = userProfile,
                    flicks = viewModel.exploreFlicks,
                    onPhotoClick = onPhotoClick,
                    onUserClick = onUserClick
                )
            }

            // Modern PullRefreshIndicator
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search photos, users, tags...", color = Color.Gray) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.Gray
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.Gray
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFD7ECFF),
            unfocusedBorderColor = Color.Gray,
            focusedContainerColor = Color(0xFF1C1C1E),
            unfocusedContainerColor = Color(0xFF1C1C1E),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun TrendingContent(
    flicks: List<Flick>,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "🔥 Trending Now",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Featured/Trending photo (first one)
        if (flicks.isNotEmpty()) {
            item {
                FeaturedPhotoCard(
                    flick = flicks.first(),
                    onPhotoClick = onPhotoClick,
                    onUserClick = onUserClick
                )
            }
        }

        // Trending users section
        item {
            Text(
                text = "📸 Popular Creators",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        // Photo grid for the rest
        items(flicks.drop(1).chunked(2)) { rowFlicks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFlicks.forEach { flick ->
                    ExplorePhotoCard(
                        flick = flick,
                        onPhotoClick = onPhotoClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowFlicks.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PopularContent(
    flicks: List<Flick>,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(flicks.take(20)) { flick ->
            PopularPhotoRow(
                flick = flick,
                rank = flicks.indexOf(flick) + 1,
                onPhotoClick = onPhotoClick,
                onUserClick = onUserClick
            )
        }
    }
}

@Composable
private fun NewContent(
    flicks: List<Flick>,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(flicks.take(50)) { flick ->
            NewPhotoCard(
                flick = flick,
                onPhotoClick = onPhotoClick,
                onUserClick = onUserClick
            )
        }
    }
}

@Composable
private fun ForYouContent(
    userProfile: UserProfile,
    flicks: List<Flick>,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    // Filter photos from friends
    val friendFlicks = flicks.filter { it.userId in userProfile.following }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (friendFlicks.isEmpty()) {
            item {
                EmptyExploreState(
                    icon = Icons.Default.Person,
                    title = "Follow more friends!",
                    subtitle = "Photos from people you follow will appear here"
                )
            }
        } else {
            item {
                Text(
                    text = "From People You Follow",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(friendFlicks) { flick ->
                ExplorePhotoCard(
                    flick = flick,
                    onPhotoClick = onPhotoClick
                )
            }
        }
    }
}

@Composable
private fun FeaturedPhotoCard(
    flick: Flick,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPhotoClick(flick) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Column {
            // Photo
            AsyncImage(
                model = withCacheBust(flick.imageUrl, flick.timestamp),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Crop
            )

            // User info bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable { onUserClick(flick.userId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = flick.userName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = flick.userName,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${flick.getTotalReactions()} reactions",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // Trending badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFF5722), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🔥 TRENDING",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorePhotoCard(
    flick: Flick,
    onPhotoClick: (Flick) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onPhotoClick(flick) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Box {
            AsyncImage(
                model = withCacheBust(flick.imageUrl, flick.timestamp),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Reaction count overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "❤️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = flick.getTotalReactions().toString(),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PopularPhotoRow(
    flick: Flick,
    rank: Int,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPhotoClick(flick) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            text = "#$rank",
            color = when (rank) {
                1 -> Color(0xFFFFD700) // Gold
                2 -> Color(0xFFC0C0C0) // Silver
                3 -> Color(0xFFCD7F32) // Bronze
                else -> Color.Gray
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp)
        )

        // Thumbnail
        AsyncImage(
            model = withCacheBust(flick.imageUrl, flick.timestamp),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = flick.userName,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${flick.getTotalReactions()} reactions • ${flick.commentCount} comments",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // Crown for top 3
        if (rank <= 3) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = when (rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    else -> Color(0xFFCD7F32)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NewPhotoCard(
    flick: Flick,
    onPhotoClick: (Flick) -> Unit,
    onUserClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPhotoClick(flick) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = withCacheBust(flick.imageUrl, flick.timestamp),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = flick.userName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                if (flick.description.isNotEmpty()) {
                    Text(
                        text = flick.description.take(50),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }

                // "New" badge
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(Color(0xFFD7ECFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✨ NEW",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyExploreState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

enum class ExploreTab(val label: String) {
    TRENDING("Trending"),
    POPULAR("Popular"),
    NEW("New"),
    FOR_YOU("For You")
}
