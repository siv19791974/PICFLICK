package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground

/**
 * Our Philosophy Screen - Why PicFlick exists
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhilosophyScreen(
    onBack: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val accentColor = Color(0xFF1565C0)

    Scaffold(
        topBar = {
            // Custom compact 48dp title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Our Philosophy",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Back to Basics",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Why we built something different",
                        fontSize = 14.sp,
                        color = subtitleColor
                    )
                }
            }

            // The Problem
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBackground
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "The Problem",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Big social media has lost its way. What started as connecting people became a machine for selling your attention. Endless ads. Manipulative algorithms. Everything designed to keep you scrolling, not communicating.",
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "The longer you stare at cat videos and viral fails, the more money they make. Your time is their product. Your attention is auctioned to the highest bidder. We think that's wrong.",
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Our Solution
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = accentColor.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Our Solution",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "PicFlick is designed to change this. To go back to basics.",
                            fontSize = 16.sp,
                            color = textColor,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Interaction with friends and family. Whether they be near or far. That's all, simple!",
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // What We Offer
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBackground
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "What PicFlick Offers",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "PicFlick allows you to share photos and communicate with your friends and family. Without the algorithms, the targeted advertising.",
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Feature bullets
                        val features = listOf(
                            "✓ Share photos with friends only",
                            "✓ Real-time chat with people you care about",
                            "✓ No endless scrolling feed",
                            "✓ No ads or algorithms",
                            "✓ Your data stays private",
                            "✓ Simple, intentional interaction"
                        )
                        
                        features.forEach { feature ->
                            Text(
                                text = feature,
                                fontSize = 14.sp,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Our Promise
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF00C853).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Our Promise",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00C853),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "We will never:",
                            fontSize = 15.sp,
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val promises = listOf(
                            "✗ Show you ads",
                            "✗ Use algorithms to manipulate your feed",
                            "✗ Sell your personal data",
                            "✗ Track you across the internet",
                            "✗ Design features to addict you"
                        )
                        
                        promises.forEach { promise ->
                            Text(
                                text = promise,
                                fontSize = 14.sp,
                                color = subtitleColor,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
