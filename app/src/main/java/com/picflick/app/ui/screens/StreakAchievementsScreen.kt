package com.picflick.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.Flick
import com.picflick.app.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

data class AchievementItem(
    val title: String,
    val description: String,
    val requiredValue: Int,
    val currentValue: Int,
    val unitLabel: String,
    val emoji: String,
    val lightGradient: List<Color>,
    val darkGradient: List<Color>
) {
    val unlocked: Boolean get() = currentValue >= requiredValue
}

data class AchievementCategory(
    val title: String,
    val achievements: List<AchievementItem>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreakAchievementsScreen(
    currentStreak: Int,
    currentUserId: String,
    photos: List<Flick>,
    totalReactionsReceived: Int,
    followingCount: Int,
    onBack: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val scope = rememberCoroutineScope()

    var messagesSent by remember(currentUserId) { mutableIntStateOf(0) }
    var sharesToChat by remember(currentUserId) { mutableIntStateOf(0) }
    var reactionsGiven by remember(currentUserId) { mutableIntStateOf(0) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        try {
            val db = FirebaseFirestore.getInstance()

            val sentMessages = db.collectionGroup("messages")
                .whereEqualTo("senderId", currentUserId)
                .get()
                .await()
            messagesSent = sentMessages.size()
            sharesToChat = sentMessages.documents.count { !it.getString("flickId").isNullOrBlank() }

            val reactedDocs = db.collection("flicks")
                .whereNotEqualTo(FieldPath.of("reactions", currentUserId), null)
                .get()
                .await()
            reactionsGiven = reactedDocs.size()
        } catch (_: Exception) {
            messagesSent = 0
            sharesToChat = 0
            reactionsGiven = 0
        }
    }

    val earlyBirdDays = remember(photos) { photos.countDistinctDaysMatching { it.get(Calendar.HOUR_OF_DAY) < 8 } }
    val nightOwlDays = remember(photos) { photos.countDistinctDaysMatching { it.get(Calendar.HOUR_OF_DAY) >= 22 } }
    val weekendDays = remember(photos) {
        photos.countDistinctDaysMatching {
            val day = it.get(Calendar.DAY_OF_WEEK)
            day == Calendar.SATURDAY || day == Calendar.SUNDAY
        }
    }
    val captionCount = remember(photos) { photos.count { it.description.isNotBlank() } }
    val taggedPostsCount = remember(photos) { photos.count { it.taggedFriends.isNotEmpty() } }
    val totalPosts = remember(photos) { photos.size }

    val bgColor = if (isDarkMode) Color(0xFF0E1118) else Color(0xFFE9F4FF)
    val cardColor = if (isDarkMode) Color(0xFF171B26) else Color.White
    val textPrimary = if (isDarkMode) Color(0xFFF5F8FF) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFFB8C1D9) else Color(0xFF475569)

    val categories = remember(
        currentStreak,
        earlyBirdDays,
        nightOwlDays,
        weekendDays,
        sharesToChat,
        messagesSent,
        totalReactionsReceived,
        reactionsGiven,
        captionCount,
        taggedPostsCount,
        totalPosts,
        followingCount
    ) {
        listOf(
            AchievementCategory(
                title = "Consistency",
                achievements = listOf(
                    achievement("Early Bird", "Upload before 8am for 7 days", 7, earlyBirdDays, "days", "🌅"),
                    achievement("Night Owl", "Upload after 10pm for 7 days", 7, nightOwlDays, "days", "🌙"),
                    achievement("Weekend Warrior", "Upload on 4 weekends", 4, weekendDays, "weekends", "🏕️"),
                    achievement("Always On", "Upload 14 days in a row", 14, currentStreak, "days", "📆")
                )
            ),
            AchievementCategory(
                title = "Streak",
                achievements = listOf(
                    achievement("3-Day Spark", "Upload 3 days in a row", 3, currentStreak, "days", "✨"),
                    achievement("7-Day Flame", "Upload 7 days in a row", 7, currentStreak, "days", "🔥"),
                    achievement("30-Day Legend", "Upload 30 days in a row", 30, currentStreak, "days", "🏆"),
                    achievement("100-Day Mythic", "Upload 100 days in a row", 100, currentStreak, "days", "👑")
                )
            ),
            AchievementCategory(
                title = "Social",
                achievements = listOf(
                    achievement("First Share", "Share a photo to chat", 1, sharesToChat, "shares", "📨"),
                    achievement("Conversation Starter", "Send 25 chat messages", 25, messagesSent, "messages", "💬"),
                    achievement("Reaction Magnet", "Get 100 reactions", 100, totalReactionsReceived, "reactions", "❤️"),
                    achievement("Supportive Friend", "React 100 times", 100, reactionsGiven, "reactions", "🤝")
                )
            ),
            AchievementCategory(
                title = "Creativity",
                achievements = listOf(
                    achievement("Caption Crafter", "Post with caption 30 times", 30, captionCount, "captions", "📝"),
                    achievement("Tag Team", "Tag friends in 20 posts", 20, taggedPostsCount, "tags", "🏷️"),
                    achievement("Visual Storyteller", "Post 50 photos", 50, totalPosts, "photos", "📸"),
                    achievement("Portfolio", "Post 100 photos", 100, totalPosts, "photos", "🖼️")
                )
            ),
            AchievementCategory(
                title = "Discovery",
                achievements = listOf(
                    achievement("New Connections", "Add 10 friends", 10, followingCount, "friends", "🧑‍🤝‍🧑"),
                    achievement("Circle Builder", "Add 25 friends", 25, followingCount, "friends", "🌐"),
                    achievement("Community Star", "Receive 250 reactions", 250, totalReactionsReceived, "reactions", "⭐"),
                    achievement("Rising Creator", "Reach 250 posts + reactions", 250, totalPosts + totalReactionsReceived, "points", "🚀")
                )
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { categories.size })
    val allAchievements = categories.flatMap { it.achievements }
    val unlockedCount = allAchievements.count { it.unlocked }
    val nextLocked = allAchievements.firstOrNull { !it.unlocked }
    val progressToNext = nextLocked?.let {
        (it.currentValue.toFloat() / it.requiredValue.toFloat()).coerceIn(0f, 1f)
    } ?: 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Text(
                text = "Streak Achievements",
                color = textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Streak",
                    color = textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$currentStreak days",
                    color = textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$unlockedCount / ${allAchievements.size} unlocked",
                    color = textSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
                nextLocked?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Next: ${it.title} (${it.currentValue}/${it.requiredValue} ${it.unitLabel})",
                        color = textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressToNext },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100)),
                    color = if (isDarkMode) Color(0xFF67B6FF) else Color(0xFF3182F6),
                    trackColor = if (isDarkMode) Color(0xFF243049) else Color(0xFFD9E6FF)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEachIndexed { index, category ->
                val selected = pagerState.currentPage == index
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            if (isDarkMode) Color(0xFF2A3C5A) else Color(0xFFD9E9FF)
                        } else {
                            if (isDarkMode) Color(0xFF1A2232) else Color(0xFFF4F8FF)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = category.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = textPrimary,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories[page].achievements.forEach { item ->
                    val gradient = if (isDarkMode) item.darkGradient else item.lightGradient

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(brush = Brush.linearGradient(gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = item.emoji, fontSize = 26.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = item.description,
                                    color = textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (item.unlocked) {
                                        if (isDarkMode) Color(0xFF1E6F45) else Color(0xFFCBF6DC)
                                    } else {
                                        if (isDarkMode) Color(0xFF3A3F4C) else Color(0xFFE8ECF5)
                                    }
                                ),
                                shape = RoundedCornerShape(100)
                            ) {
                                Text(
                                    text = if (item.unlocked) "Unlocked" else "${item.currentValue}/${item.requiredValue}",
                                    color = if (item.unlocked) {
                                        if (isDarkMode) Color(0xFFE4FFE6) else Color(0xFF0F5132)
                                    } else {
                                        if (isDarkMode) Color(0xFFF0F4FF) else Color(0xFF0A1020)
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun List<Flick>.countDistinctDaysMatching(predicate: (Calendar) -> Boolean): Int {
    return asSequence()
        .mapNotNull { flick ->
            val ts = flick.timestamp.takeIf { it > 0 } ?: return@mapNotNull null
            val calendar = Calendar.getInstance().apply { timeInMillis = ts }
            if (!predicate(calendar)) return@mapNotNull null
            Triple(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.DAY_OF_YEAR),
                calendar.get(Calendar.DAY_OF_WEEK)
            )
        }
        .distinct()
        .count()
}

private fun achievement(
    title: String,
    description: String,
    requiredValue: Int,
    currentValue: Int,
    unitLabel: String,
    emoji: String
): AchievementItem {
    return AchievementItem(
        title = title,
        description = description,
        requiredValue = requiredValue,
        currentValue = currentValue.coerceAtLeast(0),
        unitLabel = unitLabel,
        emoji = emoji,
        lightGradient = listOf(Color(0xFFE9F5FF), Color(0xFFB8DFFF)),
        darkGradient = listOf(Color(0xFF234063), Color(0xFF325C8D))
    )
}
