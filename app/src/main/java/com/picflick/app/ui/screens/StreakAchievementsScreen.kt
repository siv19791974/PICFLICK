package com.picflick.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.Flick
import com.picflick.app.ui.theme.PicFlickDarkSurface
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.isDarkModeOnBackground
import com.picflick.app.ui.theme.isDarkModeSecondaryText
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// Mid blue — matches FindFriendsScreen pill buttons exactly
private val MidBlue = Color(0xFF2A4A73)

data class AchievementItem(
    val title: String,
    val description: String,
    val requiredValue: Int,
    val currentValue: Int,
    val unitLabel: String,
    val emoji: String
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
    onBack: () -> Unit,
    onMythicClick: () -> Unit = {}
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

    val streakThreshold = 10 // Month 1 threshold — fetched from appConfig/mythicDraw in MythicDrawScreen

            val bgColor = isDarkModeBackground(isDarkMode)
        val textPrimary = isDarkModeOnBackground(isDarkMode)
        val textSecondary = isDarkModeSecondaryText(isDarkMode)

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
                title = "Streak",
                achievements = listOf(
                    AchievementItem("3-Day Spark", "Upload 3 days in a row", 3, currentStreak, "days", "✨"),
                    AchievementItem("7-Day Flame", "Upload 7 days in a row", 7, currentStreak, "days", "🔥"),
                    AchievementItem("30-Day Legend", "Upload 30 days in a row", 30, currentStreak, "days", "🏆"),
                    AchievementItem("Mythic Qualifier", "Hit the monthly streak threshold to enter the Mythic Draw. 3 winners, tiered prizes, streak-weighted tickets.", streakThreshold, currentStreak, "days", "👑"),
                    AchievementItem("Streak Saver", "Missed a day? One free streak recovery per month keeps your Mythic entry alive!", 1, 1, "info", "🛡️"),
                    AchievementItem("Early Bird", "Upload before 8am for 7 days", 7, earlyBirdDays, "days", "🌅"),
                    AchievementItem("Night Owl", "Upload after 10pm for 7 days", 7, nightOwlDays, "days", "🌙"),
                    AchievementItem("Weekend Warrior", "Upload on 4 weekends", 4, weekendDays, "weekends", "🏕️"),
                    AchievementItem("Always On", "Upload 14 days in a row", 14, currentStreak, "days", "📆")
                )
            ),
            AchievementCategory(
                title = "Social",
                achievements = listOf(
                    AchievementItem("First Share", "Share a photo to chat", 1, sharesToChat, "shares", "📨"),
                    AchievementItem("Conversation Starter", "Send 25 chat messages", 25, messagesSent, "messages", "💬"),
                    AchievementItem("Reaction Magnet", "Get 100 reactions", 100, totalReactionsReceived, "reactions", "❤️"),
                    AchievementItem("Supportive Friend", "React 100 times", 100, reactionsGiven, "reactions", "🤝")
                )
            ),
            AchievementCategory(
                title = "Creativity",
                achievements = listOf(
                    AchievementItem("Caption Crafter", "Post with caption 30 times", 30, captionCount, "captions", "📝"),
                    AchievementItem("Tag Team", "Tag friends in 20 posts", 20, taggedPostsCount, "tags", "🏷️"),
                    AchievementItem("Visual Storyteller", "Post 50 photos", 50, totalPosts, "photos", "📸"),
                    AchievementItem("Portfolio", "Post 100 photos", 100, totalPosts, "photos", "🖼️")
                )
            ),
            AchievementCategory(
                title = "Discovery",
                achievements = listOf(
                    AchievementItem("New Connections", "Add 10 friends", 10, followingCount, "friends", "🧑‍🤝‍🧑"),
                    AchievementItem("Circle Builder", "Add 25 friends", 25, followingCount, "friends", "🌐"),
                    AchievementItem("Community Star", "Receive 250 reactions", 250, totalReactionsReceived, "reactions", "⭐"),
                    AchievementItem("Rising Creator", "Reach 250 posts + reactions", 250, totalPosts + totalReactionsReceived, "points", "🚀")
                )
            )
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { categories.size })

    // Next Mythic Draw date calculation
    val nextDrawDate = remember {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MONTH, 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.time
    }
    val nextDrawMonth = remember(nextDrawDate) {
        val fmt = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        fmt.format(nextDrawDate)
    }
    val daysUntilDraw = remember {
        val now = java.util.Calendar.getInstance().timeInMillis
        val diff = nextDrawDate.time - now
        kotlin.math.max(0, (diff / (1000 * 60 * 60 * 24)).toInt())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ─── BLACK HEADER BAR (matches Find Friends) ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Streak Achievements",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // ─── NEXT MYTHIC DRAW (clickable → Mythic Draw page) ───
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .clickable { onMythicClick() }
                .border(
                    1.5.dp,
                    MidBlue.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎲 NEXT MYTHIC DRAW",
                    color = MidBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1st $nextDrawMonth",
                    color = textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$daysUntilDraw days to go — keep your streak alive!",
                    color = textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                if (currentStreak < streakThreshold) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You need ${streakThreshold - currentStreak} more daily uploads to qualify",
                        color = if (isDarkMode) Color(0xFFFF6B6B) else Color(0xFFD32F2F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "✓ You're eligible! ${(currentStreak / 10).coerceAtLeast(1)} lottery ticket${if ((currentStreak / 10).coerceAtLeast(1) > 1) "s" else ""}",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CLICK FOR INFO",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ─── FLOATING PILLS ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, category ->
                val selected = pagerState.currentPage == index
                val pillBg = when {
                    selected -> MidBlue
                    isDarkMode -> PicFlickDarkSurface
                    else -> Color(0xFFE3F0FA)
                }
                val pillText = when {
                    selected -> Color.White
                    else -> textPrimary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillBg)
                        .border(
                            1.dp,
                            if (selected) MidBlue else textSecondary.copy(alpha = 0.25f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.title,
                        color = pillText,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ─── PAGER CONTENT ───
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                val achievements = categories[page].achievements
                achievements.forEachIndexed { index, item ->
                    val itemProgress = (item.currentValue.toFloat() / item.requiredValue.toFloat()).coerceIn(0f, 1f)
                    val unlocked = item.unlocked

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Emoji circle — MidBlue gradient
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            if (isDarkMode) {
                                                listOf(
                                                    MidBlue.copy(alpha = 0.35f),
                                                    MidBlue.copy(alpha = 0.12f)
                                                )
                                            } else {
                                                listOf(
                                                    MidBlue.copy(alpha = 0.25f),
                                                    MidBlue.copy(alpha = 0.05f)
                                                )
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = item.emoji, fontSize = 26.sp)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

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
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { itemProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(100)),
                                    color = if (unlocked) Color(0xFF4CAF50) else MidBlue,
                                    trackColor = textSecondary.copy(alpha = 0.15f)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Progress text
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when {
                                        item.title == "100-Day Mythic" && unlocked -> "MYTHIC"
                                        unlocked -> "\u2713"
                                        else -> "${item.currentValue}/${item.requiredValue}"
                                    },
                                    color = if (unlocked) Color(0xFF4CAF50) else textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.unitLabel,
                                    color = textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    // Faint divider between achievements (not after the last one)
                    if (index < achievements.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            thickness = 0.5.dp,
                            color = textSecondary.copy(alpha = 0.12f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
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

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun StreakAchievementsScreenPreview() {
    StreakAchievementsScreen(
        currentStreak = 9,
        currentUserId = "",
        photos = emptyList(),
        totalReactionsReceived = 47,
        followingCount = 12,
        onBack = {}
    )
}