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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.data.UserProfile
import com.picflick.app.data.getColor
import com.picflick.app.data.getDarkColor
import com.picflick.app.data.getLightColor
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
    onBack: () -> Unit,
    onUserProfileClick: (String) -> Unit = {}
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val bgColor = isDarkModeBackground(isDarkMode)
    val textPrimary = isDarkModeOnBackground(isDarkMode)
    val textSecondary = isDarkModeSecondaryText(isDarkMode)

    var drawData by remember { mutableStateOf<MythicDrawData?>(null) }
    var hallOfFame by remember { mutableStateOf<List<HallOfFameEntry>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var pastDraws by remember { mutableStateOf<List<PastDrawEntry>>(emptyList()) }
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

                val statsMap = drawSnap.get("stats") as? Map<String, Any>
                val stats = statsMap?.let { s ->
                    DrawStats(
                        totalEntrants = (s["totalEntrants"] as? Number)?.toInt() ?: 0,
                        totalUsers = (s["totalUsers"] as? Number)?.toInt() ?: 0,
                        yourOddsDenom = (s["yourOddsDenom"] as? Number)?.toInt() ?: 0,
                        ticketPoolSize = (s["ticketPoolSize"] as? Number)?.toInt() ?: 0,
                        averageStreak = (s["averageStreak"] as? Number)?.toInt() ?: 0,
                        highestStreak = (s["highestStreak"] as? Number)?.toInt() ?: 0,
                        drawStatus = s["drawStatus"] as? String ?: "complete",
                        drawCompletedAt = (s["drawCompletedAt"] as? Number)?.toLong() ?: 0L,
                    )
                }

                val animMap = drawSnap.get("drawAnimation") as? Map<String, Any>
                val anim = animMap?.let { a ->
                    DrawAnimation(
                        isLive = a["isLive"] as? Boolean ?: false,
                        startedAt = (a["startedAt"] as? Number)?.toLong(),
                        completedAt = (a["completedAt"] as? Number)?.toLong(),
                        winnerRevealed = a["winnerRevealed"] as? Boolean ?: false,
                    )
                }

                MythicDrawData(
                    monthKey = drawSnap.getString("monthKey") ?: monthKey,
                    monthNumber = (drawSnap.get("monthNumber") as? Long)?.toInt() ?: 0,
                    streakThreshold = (drawSnap.get("streakThreshold") as? Long)?.toInt() ?: 100,
                    winners = winners,
                    eligibleUserCount = (drawSnap.get("eligibleUserCount") as? Long)?.toInt() ?: 0,
                    totalUserCount = (drawSnap.get("totalUserCount") as? Long)?.toInt() ?: 0,
                    noWinner = drawSnap.getBoolean("noWinner") ?: false,
                    stats = stats,
                    drawAnimation = anim,
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

            // Fetch past draws (all mythic_draw_* docs except current month)
            val allSystemDocs = db.collection("system").get().await()
            val pastList = allSystemDocs.documents
                .filter { it.id.startsWith("mythic_draw_") && it.id != "mythic_draw_$monthKey" && it.exists() }
                .sortedByDescending { it.id }
                .take(12)
                .mapNotNull { doc ->
                    val mk = doc.getString("monthKey") ?: return@mapNotNull null
                    val winnersList = (doc.get("winners") as? List<Map<String, Any>>)?.map { w ->
                        WinnerEntry(
                            place = (w["place"] as? Long)?.toInt() ?: 0,
                            userId = w["userId"] as? String ?: "",
                            userName = w["userName"] as? String ?: "Unknown",
                            streak = (w["streak"] as? Long)?.toInt() ?: 0,
                            crown = w["crown"] as? String ?: "",
                        )
                    } ?: emptyList()
                    PastDrawEntry(
                        monthKey = mk,
                        monthNumber = (doc.get("monthNumber") as? Long)?.toInt() ?: 0,
                        status = doc.getString("status") ?: "completed",
                        winners = winnersList,
                        eligibleUserCount = (doc.get("eligibleUserCount") as? Long)?.toInt() ?: 0,
                        totalUserCount = (doc.get("totalUserCount") as? Long)?.toInt() ?: 0,
                    )
                }
            pastDraws = pastList
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

        // ─── NEXT DRAW COUNTDOWN ───
        // If no completed draw yet (first month), show a big countdown card
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

        if (dd.isUpcoming || pastDraws.isEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
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
                    if (currentStreak < dd.streakThreshold) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You need ${dd.streakThreshold - currentStreak} more daily uploads to qualify",
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
                    isDarkMode = isDarkMode,
                    onClick = { onUserProfileClick(winner.userId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ─── USER'S MYTHIC STATS ───
        if (currentUserId.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))

            val contenderCount = userProfile.mythicContenderCount
            val boostAmount = userProfile.mythicUploadBoostAmount
            val hasActiveBoost = userProfile.hasActiveUploadBoost()
            val crown = userProfile.mythicCrown
            val hasBanner = userProfile.mythicWinnerBanner.isNotBlank()
            val tier = userProfile.mythicTier
            val consecutiveMonths = userProfile.mythicConsecutiveMonths
            val isChampion = userProfile.mythicChampion

            // Streak progress bar — fatter, color-matched to achievement ring
            val progress = (currentStreak.toFloat() / dd.streakThreshold.toFloat()).coerceIn(0f, 1f)
            val progressColor = when {
                progress >= 1f -> GoldColor
                progress >= 0.8f -> Color(0xFFFF5722)
                progress >= 0.6f -> Color(0xFFFF9800)
                progress >= 0.3f -> Color(0xFF4CAF50)
                else -> Color(0xFF2196F3)
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔥", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Current Streak", color = textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$currentStreak / ${dd.streakThreshold} days",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(100)),
                    color = progressColor,
                    trackColor = textSecondary.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (contenderCount > 0) StatRow(label = "Draw Entries", value = "$contenderCount months", icon = "🎫")
                if (hasActiveBoost) StatRow(
                    label = "Upload Boost",
                    value = "+$boostAmount/day",
                    icon = "🚀"
                )
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

                // ─── MYTHIC CHAMPION BADGE (permanent, ULTRA winners only) ───
                if (isChampion) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                            .border(1.dp, GoldColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🏆", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MYTHIC CHAMPION",
                                color = GoldColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Permanent badge — ${userProfile.mythicChampionMonth}",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // ─── TIER BADGE ───
                if (tier.isNotBlank() && consecutiveMonths > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val tierEmoji = when (tier) {
                        "diamond" -> "💎"
                        "gold" -> "🥇"
                        "silver" -> "🥈"
                        else -> "🥉"
                    }
                    val tierColor = when (tier) {
                        "diamond" -> Color(0xFFB9F2FF)
                        "gold" -> GoldColor
                        "silver" -> SilverColor
                        else -> BronzeColor
                    }
                    val tierLabel = when (tier) {
                        "diamond" -> "Diamond Mythic"
                        "gold" -> "Gold Mythic"
                        "silver" -> "Silver Mythic"
                        else -> "Bronze Mythic"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tierColor.copy(alpha = 0.12f))
                            .border(1.dp, tierColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = tierEmoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tierLabel.uppercase(),
                                color = tierColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "$consecutiveMonths consecutive month${if (consecutiveMonths > 1) "s" else ""} entered",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ─── LEADERBOARD ───
        if (leaderboard.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "WORLDWIDE STREAK LEADERBOARD",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Tap any user to view their profile",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = textSecondary.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .border(1.5.dp, MidBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    leaderboard.forEachIndexed { index, entry ->
                        LeaderboardRow(
                            rank = index + 1,
                            entry = entry,
                            isCurrentUser = entry.userId == currentUserId,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            bgColor = bgColor,
                            isDarkMode = isDarkMode,
                            onClick = { onUserProfileClick(entry.userId) }
                        )
                    }
                }
            }
        }

        // ─── GLOBAL STATS WIDGET ───
        if (!dd.isUpcoming) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "DRAW STATS",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            val stats = dd.stats
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .border(1.dp, MidBlue.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        StatBox(
                            label = "Entrants",
                            value = "${stats?.totalEntrants ?: 0}",
                            icon = "🎫",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatBox(
                            label = "Total Users",
                            value = "${stats?.totalUsers ?: 0}",
                            icon = "👥",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatBox(
                            label = "Avg Streak",
                            value = "${stats?.averageStreak ?: 0}d",
                            icon = "📊",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatBox(
                            label = "Top Streak",
                            value = "${stats?.highestStreak ?: 0}d",
                            icon = "🔝",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val oddsDenom = stats?.yourOddsDenom ?: 1
                    val userTickets = currentStreak / 10
                    val safeTickets = userTickets.coerceAtLeast(1)
                    val oddsText = if (oddsDenom > 0 && userTickets > 0) {
                        "Your ${safeTickets} ticket${if (safeTickets > 1) "s" else ""} · 1 in ${(oddsDenom + safeTickets - 1) / safeTickets} odds"
                    } else if (currentStreak > 0) {
                        "Upload daily to earn tickets"
                    } else {
                        "Start your streak to enter"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MidBlue.copy(alpha = 0.1f))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = oddsText,
                            color = MidBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ─── LIVE DRAW ANIMATION ───
        if (dd.drawAnimation?.isLive == true && !dd.isUpcoming) {
            Spacer(modifier = Modifier.height(24.dp))
            MythicDrawAnimation(
                isDarkMode = isDarkMode,
                onComplete = { /* animation completes */ }
            )
        }

        // ─── HALL OF FAME ───
        if (hallOfFame.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
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
                    textSecondary = textSecondary,
                    onClick = { onUserProfileClick(entry.userId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // ─── PAST DRAWS ───
        if (pastDraws.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "PAST DRAWS",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))

            pastDraws.forEach { pd ->
                PastDrawCard(
                    entry = pd,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    isDarkMode = isDarkMode,
                    onWinnerClick = { userId -> onUserProfileClick(userId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ─── PRIZES ───
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "PRIZES",
            modifier = Modifier.padding(horizontal = 24.dp),
            color = textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.5.dp, MidBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PrizeRow(place = "1st", prize = "3 months PRO + Gold Crown + Profile Banner", color = GoldColor, textPrimary = textPrimary)
                PrizeRow(place = "2nd", prize = "1 month PRO + Silver Crown + Profile Banner", color = SilverColor, textPrimary = textPrimary)
                PrizeRow(place = "3rd", prize = "2 weeks PRO + Bronze Crown + Profile Banner", color = BronzeColor, textPrimary = textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🏆 PRO winners receive ULTRA if already PRO. ULTRA winners get extended + Champion badge.",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MidBlue)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "🎁 ALL ENTRANTS (runners up): +30% upload boost for 30 days + tier badge progression towards Bronze/Silver/Gold/Diamond Mythic.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // ─── HOW IT WORKS ───
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "HOW IT WORKS",
            modifier = Modifier.padding(horizontal = 24.dp),
            color = textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.5.dp, MidBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                HowItWorksRow(step = "1", text = "Upload daily to build your streak", textPrimary = textPrimary)
                HowItWorksRow(step = "2", text = "Hit the monthly threshold to enter", textPrimary = textPrimary)
                HowItWorksRow(step = "3", text = "Longer streak = more lottery tickets", textPrimary = textPrimary)
                HowItWorksRow(step = "4", text = "3 winners picked randomly each month", textPrimary = textPrimary)
                HowItWorksRow(step = "5", text = "Winners get PRO + crown + profile banner", textPrimary = textPrimary)
                HowItWorksRow(step = "6", text = "All entrants get +30% upload boost for 30 days", textPrimary = textPrimary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WinnerCard(
    winner: WinnerEntry,
    isCurrentUser: Boolean,
    bgColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit = {}
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
            )
            .clickable(onClick = onClick),
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
private fun PastDrawCard(
    entry: PastDrawEntry,
    textPrimary: Color,
    textSecondary: Color,
    isDarkMode: Boolean,
    onWinnerClick: (String) -> Unit = {}
) {
    val bgColor = if (isDarkMode) Color(0xFF1A1A2E) else Color(0xFFF0F0F0)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .border(1.dp, MidBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.monthKey,
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Month #${entry.monthNumber}",
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
            if (entry.winners.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                entry.winners.forEach { w ->
                    val emoji = when (w.crown) {
                        "gold" -> "👑"
                        "silver" -> "🥈"
                        "bronze" -> "🥉"
                        else -> "🏆"
                    }
                    Text(
                        text = "$emoji #${w.place} ${w.userName} (${w.streak}d)",
                        color = textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onWinnerClick(w.userId) }
                    )
                }
            } else {
                Text(
                    text = "No eligible entrants",
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${entry.eligibleUserCount} entrants · ${entry.totalUserCount} total users",
                color = textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    bgColor: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit = {}
) {
    val medalEmoji = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> null
    }
    val rankColor = when (rank) {
        1 -> GoldColor
        2 -> SilverColor
        3 -> BronzeColor
        else -> textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number or medal
        Box(
            modifier = Modifier.width(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (medalEmoji != null) {
                Text(text = medalEmoji, fontSize = 20.sp)
            } else {
                Text(
                    text = "$rank",
                    color = rankColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Avatar with tier ring (matches app-wide style)
        val tier = SubscriptionTier.fromString(entry.tier)
        val tierColor = tier.getColor()
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        listOf(
                            tierColor,
                            tier.getDarkColor(),
                            tier.getLightColor(),
                            tierColor
                        )
                    )
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = entry.photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.userName + if (isCurrentUser) " (You)" else "",
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${entry.streak}-day streak",
                    color = textSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Chevron + streak
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.streak}d",
                    color = rankColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

@Composable
private fun HallOfFameRow(
    entry: HallOfFameEntry,
    isCurrentUser: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit = {}
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
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
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
private fun HowItWorksRow(step: String, text: String, textPrimary: Color = Color(0xFFB7BDC9)) {
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
            color = textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun PrizeRow(place: String, prize: String, color: Color, textPrimary: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = place,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = prize,
            color = textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp
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
    val isUpcoming: Boolean = false,
    val stats: DrawStats? = null,
    val drawAnimation: DrawAnimation? = null
)

data class DrawStats(
    val totalEntrants: Int,
    val totalUsers: Int,
    val yourOddsDenom: Int,
    val ticketPoolSize: Int,
    val averageStreak: Int,
    val highestStreak: Int,
    val drawStatus: String,
    val drawCompletedAt: Long
)

data class DrawAnimation(
    val isLive: Boolean,
    val startedAt: Long?,
    val completedAt: Long?,
    val winnerRevealed: Boolean
)

data class PastDrawEntry(
    val monthKey: String,
    val monthNumber: Int,
    val status: String,
    val winners: List<WinnerEntry>,
    val eligibleUserCount: Int,
    val totalUserCount: Int
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
    val photoUrl: String,
    val tier: String = "FREE"
)

// ─── STAT BOX ───

@Composable
private fun StatBox(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C2E))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color(0xFFB7BDC9),
                fontSize = 11.sp
            )
        }
    }
}

// ─── LIVE DRAW ANIMATION ───

@Composable
private fun MythicDrawAnimation(
    isDarkMode: Boolean,
    onComplete: () -> Unit
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, GoldColor.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Spinning wheel
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val colors = listOf(
                        GoldColor,
                        SilverColor,
                        BronzeColor,
                        MidBlue,
                        GoldColor.copy(alpha = 0.7f),
                        SilverColor.copy(alpha = 0.7f),
                        BronzeColor.copy(alpha = 0.7f),
                        MidBlue.copy(alpha = 0.7f),
                    )
                    val center = this.center
                    val radius = size.minDimension / 2
                    val segmentAngle = 360f / colors.size
                    colors.forEachIndexed { index, color ->
                        drawArc(
                            color = color,
                            startAngle = index * segmentAngle,
                            sweepAngle = segmentAngle - 2f,
                            useCenter = true,
                        )
                    }
                    drawCircle(
                        color = Color(0xFF1A1A2E),
                        radius = radius * 0.25f,
                        center = center
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🎡 LIVE DRAW",
                    color = GoldColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spinning the wheel...",
                    color = Color(0xFFB7BDC9),
                    fontSize = 12.sp
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000L)
        onComplete()
    }
}

// ─── PREVIEWS ───

@Preview(name = "MythicDraw - Upcoming", showBackground = true, backgroundColor = 0xFF0D1F33)
@Composable
fun MythicDrawUpcomingPreview() {
    MythicDrawScreen(
        currentStreak = 47,
        currentUserId = "preview_user",
                    userProfile = UserProfile(
                uid = "preview_user",
                displayName = "Preview User",
                mythicTier = "bronze",
                mythicContenderCount = 2,
                mythicUploadBoostAmount = 3,
                mythicUploadBoostExpiry = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)
            ),
        onBack = {}
    )
}

@Preview(name = "MythicDraw - Completed", showBackground = true, backgroundColor = 0xFF0D1F33)
@Composable
fun MythicDrawCompletedPreview() {
    MythicDrawScreen(
        currentStreak = 120,
        currentUserId = "preview_user",
                    userProfile = UserProfile(
                uid = "preview_user",
                displayName = "Preview User",
                mythicTier = "silver",
                mythicContenderCount = 5,
                mythicUploadBoostAmount = 30,
                mythicUploadBoostExpiry = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)
            ),
        onBack = {}
    )
}

@Preview(name = "MythicDraw - Live Animation", showBackground = true, backgroundColor = 0xFF0D1F33)
@Composable
fun MythicDrawLivePreview() {
    MythicDrawAnimation(isDarkMode = true, onComplete = {})
}
