package com.picflick.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.ui.theme.ThemeManager

data class AchievementItem(
    val title: String,
    val description: String,
    val requiredDays: Int,
    val emoji: String,
    val unlocked: Boolean,
    val lightGradient: List<Color>,
    val darkGradient: List<Color>
)

@Composable
fun StreakAchievementsScreen(
    currentStreak: Int,
    onBack: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value

    val bgColor = if (isDarkMode) Color(0xFF0E1118) else Color(0xFFE9F4FF)
    val cardColor = if (isDarkMode) Color(0xFF171B26) else Color.White
    val textPrimary = if (isDarkMode) Color(0xFFF5F8FF) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFFB8C1D9) else Color(0xFF475569)

    val achievements = listOf(
        AchievementItem(
            title = "First Spark",
            description = "Upload 1 day in a row",
            requiredDays = 1,
            emoji = "✨",
            unlocked = currentStreak >= 1,
            lightGradient = listOf(Color(0xFFFFF3B0), Color(0xFFFFD166)),
            darkGradient = listOf(Color(0xFF6C4E00), Color(0xFFB98400))
        ),
        AchievementItem(
            title = "Getting Hot",
            description = "Upload 3 days in a row",
            requiredDays = 3,
            emoji = "🔥",
            unlocked = currentStreak >= 3,
            lightGradient = listOf(Color(0xFFFFC9A9), Color(0xFFFF8C42)),
            darkGradient = listOf(Color(0xFF6E2A00), Color(0xFFB64500))
        ),
        AchievementItem(
            title = "One Week Strong",
            description = "Upload 7 days in a row",
            requiredDays = 7,
            emoji = "🏆",
            unlocked = currentStreak >= 7,
            lightGradient = listOf(Color(0xFFFAD0FF), Color(0xFFD48CFF)),
            darkGradient = listOf(Color(0xFF492160), Color(0xFF7D3FA3))
        ),
        AchievementItem(
            title = "Half Month Hero",
            description = "Upload 15 days in a row",
            requiredDays = 15,
            emoji = "💎",
            unlocked = currentStreak >= 15,
            lightGradient = listOf(Color(0xFFBFF4FF), Color(0xFF61D6FF)),
            darkGradient = listOf(Color(0xFF1D4E5A), Color(0xFF2B8AA1))
        ),
        AchievementItem(
            title = "Monthly Legend",
            description = "Upload 30 days in a row",
            requiredDays = 30,
            emoji = "👑",
            unlocked = currentStreak >= 30,
            lightGradient = listOf(Color(0xFFFFD3E0), Color(0xFFFF8FB1)),
            darkGradient = listOf(Color(0xFF5A2437), Color(0xFFA04366))
        ),
        AchievementItem(
            title = "Ultimate Creator",
            description = "Upload 100 days in a row",
            requiredDays = 100,
            emoji = "🌈",
            unlocked = currentStreak >= 100,
            lightGradient = listOf(Color(0xFFD5F9C7), Color(0xFF7EE081)),
            darkGradient = listOf(Color(0xFF244D2A), Color(0xFF3E8A49))
        )
    )

    val unlockedCount = achievements.count { it.unlocked }
    val progressToNext = achievements.firstOrNull { !it.unlocked }
        ?.let { currentStreak.toFloat() / it.requiredDays.toFloat() }
        ?.coerceIn(0f, 1f)
        ?: 1f

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
                    text = "$unlockedCount / ${achievements.size} unlocked",
                    color = textSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
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

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(achievements) { achievement ->
                val gradient = if (isDarkMode) achievement.darkGradient else achievement.lightGradient
                val badgeTextColor = if (isDarkMode) Color(0xFFF6FAFF) else Color(0xFF0A1020)

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
                            Text(
                                text = achievement.emoji,
                                fontSize = 26.sp
                            )
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = achievement.title,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = achievement.description,
                                color = textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (achievement.unlocked) {
                                    if (isDarkMode) Color(0xFF1E6F45) else Color(0xFFCBF6DC)
                                } else {
                                    if (isDarkMode) Color(0xFF3A3F4C) else Color(0xFFE8ECF5)
                                }
                            ),
                            shape = RoundedCornerShape(100)
                        ) {
                            Text(
                                text = if (achievement.unlocked) "Unlocked" else "${achievement.requiredDays}d",
                                color = if (achievement.unlocked) {
                                    if (isDarkMode) Color(0xFFE4FFE6) else Color(0xFF0F5132)
                                } else {
                                    badgeTextColor
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
