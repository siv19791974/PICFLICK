package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.isDarkModeOnBackground
import com.picflick.app.ui.theme.isDarkModeSecondaryText
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val MidBlue = Color(0xFF2A4A73)
private val GoldColor = Color(0xFFFFD700)
private val SilverColor = Color(0xFFC0C0C0)
private val BronzeColor = Color(0xFFCD7F32)

@Composable
fun MythicDrawScreen(
    currentStreak: Int,
    currentUserId: String,
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val bgColor = isDarkModeBackground(isDarkMode)
    val textPrimary = isDarkModeOnBackground(isDarkMode)
    val textSecondary = isDarkModeSecondaryText(isDarkMode)

    var drawData by remember { mutableStateOf<MythicDrawData?>(null) }
    var hallOfFame by remember { mutableStateOf<List<HallOfFameEntry>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val db = FirebaseFirestore.getInstance()

            // Get current month draw
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val monthKey = "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
            val drawSnap = db.collection("system").document("mythic_draw_$monthKey").get().await()

            // Get hall of fame
            val hallSnap = db.collection("system").document("mythic_hall_of_fame").get().await()
            val hallList = if (hallSnap.exists()) {
                (hallSnap.get("winners") as? List<Map<String, Any>>)?.map { m ->
                    HallOfFameEntry(
                        monthKey = m["monthKey"] as? String ?: "",
                        monthNumber = (m["monthNumber"] as? Long)?.toInt() ?: 0,
                        place = (m["place"] as? Long)?.toInt() ?: 0,
                        userId = m["userId"] as? String ?: "",
                        userName = m["userName"] as? String ?: "Unknown",
                        streak = (m["streak"] as? Long)?.toInt() ?: 0,
                        crown = m["crown"] as? String ?: "",
                    )
                }?.reversed() ?: emptyList()
            } else emptyList()

            // Get leaderboard from current draw or query users
            val lb = if (drawSnap.exists()) {
                (drawSnap.get("leaderboard") as? List<Map<String, Any>>)?.map { m ->
                    LeaderboardEntry(
                        userId = m["userId"] as? String ?: "",
                        userName = m["userName"] as? String ?: "Unknown",
                        streak = (m["streak"] as? Long)?.toInt() ?: 0,
                        photoUrl = m["photoUrl"] as? String ?: "",
                    )
                } ?: emptyList()
            } else {
                // Fallback: query top 10 streaks from users
                val usersSnap = db.collection("users")
                    .orderBy("streak.current", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()
                usersSnap.documents.map { doc ->
                    LeaderboardEntry(
                        userId = doc.id,
                        userName = doc.getString("displayName") ?: "Unknown",
                        streak = ((doc.get("streak") as? Map<*, *>)?.get("current") as? Long)?.toInt() ?: 0,
                        photoUrl = doc.getString("photoUrl") ?: "",
                    )
                }
            }

            drawData = if (drawSnap.exists()) {
                val winners = (drawSnap.get("winners") as? List<Map<String, Any>>)?.map { m ->
                    WinnerEntry(
                        place = (m["place"] as? Long)?.toInt() ?: 0,
                        userId = m["userId"] as? String ?: "",
                        userName = m["userName"] as? String ?: "Unknown",
                        streak = (m["streak"] as? Long)?.toInt() ?: 0,
                        crown = m["crown"] as? String ?: "",
                    )
                } ?: emptyList()
                MythicDrawData(
                    monthKey = drawSnap.getString("monthKey") ?: monthKey,
                    monthNumber = (drawSnap.get("monthNumber") as? Long)?.toInt() ?: 0,
                    streakThreshold = (drawSnap.get("streakThreshold") as? Long)?.toInt() ?: 100,
                    winners = winners,
                    eligibleUserCount = (drawSnap.get("eligibleUserCount") as? Long)?.toInt() ?: 0,
                    totalUserCount = (drawSnap.get("totalUserCount") as? Long)?.toInt() ?: 0,
                    noWinner = drawSnap.getBoolean("noWinner") ?: false,
                )
            } else {
                // No draw yet this month — show upcoming info
                val allDraws = db.collection("system").get().await().documents
                    .filter { it.id.startsWith("mythic_draw_") && it.id != "mythic_draw_$monthKey" }
                val monthNum = allDraws.size + 1
                val thresholds = mapOf(1 to 10, 2 to 30, 3 to 50, 4 to 70)
                val threshold = if (monthNum >= 5) 100 else (thresholds[monthNum] ?: 100)
                MythicDrawData(
                    monthKey = monthKey,
                    monthNumber = monthNum,
                    streakThreshold = threshold,
                    winners = emptyList(),
                    eligibleUserCount = 0,
                    totalUserCount = 0,
                    noWinner = false,
                    isUpcoming = true,
                )
            }

            hallOfFame = hallList
            leaderboard = lb
        } catch (_: Exception) {
            // Fallback
            drawData = MythicDrawData(
                monthKey = "",
                monthNumber = 1,
                streakThreshold = 10,
                winners = emptyList(),
                eligibleUserCount = 0,
                totalUserCount = 0,
                noWinner = false,
                isUpcoming = true,
            )
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
                text = "Mythic Monthly Draw",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MidBlue)
            }
            return@Column
        }

        val dd = drawData ?: return@Column

        // ─── CURRENT DRAW STATUS ───
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .border(
                    1.dp,
                    if (currentStreak >= dd.streakThreshold) GoldColor.copy(alpha = 0.4f)
                    else MidBlue.copy(alpha = 0.35f),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = if (dd.isUpcoming) "UPCOMING DRAW" else "${dd.monthKey} DRAW RESULTS",
                    color = if (currentStreak >= dd.streakThreshold) GoldColor else MidBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Month #${dd.monthNumber} · Threshold: ${dd.streakThreshold} days",
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (dd.isUpcoming) {
                    val daysToGo = (dd.streakThreshold - currentStreak).coerceAtLeast(0)
                    val progress = (currentStreak.toFloat() / dd.streakThreshold.toFloat()).coerceIn(0f, 1f)
                    Text(
                        text = if (currentStreak >= dd.streakThreshold)
                            "STATUS: ELIGIBLE ✓ You're in the draw!"
                        else
                            "STATUS: $daysToGo more days to qualify",
                        color = if (currentStreak >= dd.streakThreshold) Color(0xFF4CAF50) else textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100)),
                        color = if (currentStreak >= dd.streakThreshold) Color(0xFF4CAF50) else MidBlue,
                        trackColor = textSecondary.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$currentStreak / ${dd.streakThreshold} days",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                } else if (dd.noWinner) {
                    Text(
                        text = "No eligible entrants this month. Keep uploading!",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                } else if (dd.winners.isNotEmpty()) {
                    Text(
                        text = "${dd.eligibleUserCount} entrants · ${dd.winners.size} winner${if (dd.winners.size > 1) "s" else ""}",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ─── WINNERS SECTION ───
        if (!dd.isUpcoming && dd.winners.isNotEmpty()) {
            Text(
                text = "THIS MONTH'S WINNERS",
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp),
                color = GoldColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            dd.winners.forEach { winner ->
                WinnerCard(
                    winner = winner,
                    isCurrentUser = winner.userId == currentUserId,
                    bgColor = bgColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    isDarkMode = isDarkMode
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ─── USER'S MYTHIC STATS ───
        if (currentUserId.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "YOUR MYTHIC STATS",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            val contenderCount = userProfile.mythicContenderCount
            val storageBonus = userProfile.mythicStorageBonusTotal
            val crown = userProfile.mythicCrown
            val hasBanner = userProfile.mythicWinnerBanner.isNotBlank()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .border(1.dp, MidBlue.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatRow(label = "Current Streak", value = "$currentStreak days", icon = "🔥")
                    if (contenderCount > 0) StatRow(label = "Draw Entries", value = "$contenderCount months", icon = "🎫")
                    if (storageBonus > 0) StatRow(label = "Storage Bonus", value = "+$storageBonus MB", icon = "💾")
                    if (crown.isNotBlank()) StatRow(
                        label = "Current Crown",
                        value = crown.replaceFirstChar { it.uppercase() },
                        icon = when (crown) {
                            "gold" -> "👑"
                            "silver" -> "🥈"
                            "bronze" -> "🥉"
                            else -> "🏆"
                        }
                    )
                    if (hasBanner) StatRow(label = "Banner", value = "Active", icon = "🖼️")
                }
            }
        }

        // ─── LEADERBOARD ───
        if (leaderboard.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "STREAK LEADERBOARD",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            leaderboard.forEachIndexed { index, entry ->
                LeaderboardRow(
                    rank = index + 1,
                    entry = entry,
                    isCurrentUser = entry.userId == currentUserId,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
                if (index < leaderboard.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        thickness = 0.5.dp,
                        color = textSecondary.copy(alpha = 0.12f)
                    )
                }
            }
        }

        // ─── HALL OF FAME ───
        if (hallOfFame.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "HALL OF FAME",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = GoldColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            hallOfFame.take(20).forEach { entry ->
                HallOfFameRow(
                    entry = entry,
                    isCurrentUser = entry.userId == currentUserId,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // ─── HOW IT WORKS ───
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "HOW IT WORKS",
            modifier = Modifier.padding(horizontal = 24.dp),
            color = textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.dp, MidBlue.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                HowItWorksRow(step = "1", text = "Upload daily to build your streak")
                HowItWorksRow(step = "2", text = "Hit the monthly threshold to enter")
                HowItWorksRow(step = "3", text = "Longer streak = more lottery tickets")
                HowItWorksRow(step = "4", text = "3 winners picked randomly each month")
                HowItWorksRow(step = "5", text = "Winners get PRO + crown + profile banner")
                HowItWorksRow(step = "6", text = "All entrants get +50MB storage bonus")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun WinnerCard(
    winner: WinnerEntry,
    isCurrentUser: Boolean,
    bgColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean
) {
    val crownEmoji = when (winner.crown) {
        "gold" -> "👑"
        "silver" -> "🥈"
        "bronze" -> "🥉"
        else -> "🏆"
    }
    val crownColor = when (winner.crown) {
        "gold" -> GoldColor
        "silver" -> SilverColor
        "bronze" -> BronzeColor
        else -> MidBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .border(
                1.dp,
                if (isCurrentUser) crownColor.copy(alpha = 0.5f) else crownColor.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(crownColor.copy(alpha = 0.4f), crownColor.copy(alpha = 0.1f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = crownEmoji, fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${winner.place}${getOrdinalSuffix(winner.place)} Place",
                    color = crownColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = winner.userName + if (isCurrentUser) " (You)" else "",
                    color = textPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "${winner.streak}-day streak",
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    val rankColor = when (rank) {
        1 -> GoldColor
        2 -> SilverColor
        3 -> BronzeColor
        else -> textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                color = rankColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        AsyncImage(
            model = entry.photoUrl,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.userName + if (isCurrentUser) " (You)" else "",
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
            )
        }

        Text(
            text = "${entry.streak}d",
            color = textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HallOfFameRow(
    entry: HallOfFameEntry,
    isCurrentUser: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    val crownEmoji = when (entry.crown) {
        "gold" -> "👑"
        "silver" -> "🥈"
        "bronze" -> "🥉"
        else -> "🏆"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = crownEmoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.userName + if (isCurrentUser) " (You)" else "",
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${entry.monthKey} · ${entry.place}${getOrdinalSuffix(entry.place)} · ${entry.streak}d streak",
                color = textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, icon: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, color = Color(0xFFB7BDC9), fontSize = 13.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HowItWorksRow(step: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MidBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(text = step, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFFB7BDC9),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun getOrdinalSuffix(n: Int): String {
    return when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}

// ─── DATA CLASSES ───

data class MythicDrawData(
    val monthKey: String,
    val monthNumber: Int,
    val streakThreshold: Int,
    val winners: List<WinnerEntry>,
    val eligibleUserCount: Int,
    val totalUserCount: Int,
    val noWinner: Boolean,
    val isUpcoming: Boolean = false
)

data class WinnerEntry(
    val place: Int,
    val userId: String,
    val userName: String,
    val streak: Int,
    val crown: String
)

data class HallOfFameEntry(
    val monthKey: String,
    val monthNumber: Int,
    val place: Int,
    val userId: String,
    val userName: String,
    val streak: Int,
    val crown: String
)

data class LeaderboardEntry(
    val userId: String,
    val userName: String,
    val streak: Int,
    val photoUrl: String
)
