package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Legal Screen - Terms, Conditions, and Content Policy
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
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
                        text = "Legal",
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
            contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Terms & Conditions",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last Updated: March 2025",
                        fontSize = 13.sp,
                        color = subtitleColor
                    )
                }
            }

            // Agreement
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
                            text = "Your Agreement",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "By using PicFlick, you agree to these Terms and Conditions. If you don't agree, please don't use the app. We tried to keep this simple, but some legal stuff is necessary to protect everyone.",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Acceptable Use
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
                            text = "Acceptable Use",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "PicFlick is for sharing photos with friends and family. Please use it responsibly.",
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val rules = listOf(
                            "✓ Share your own photos or ones you have permission to share",
                            "✓ Be respectful to other users",
                            "✓ Report inappropriate content",
                            "✗ Don't post illegal, harmful, or offensive content",
                            "✗ Don't harass, bully, or threaten others",
                            "✗ Don't spam or use the app for commercial promotion",
                            "✗ Don't attempt to hack, reverse engineer, or disrupt the service"
                        )
                        
                        rules.forEach { rule ->
                            Text(
                                text = rule,
                                fontSize = 14.sp,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Content Policy
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
                            text = "Content Policy",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "You keep ownership of your photos. By uploading, you give PicFlick permission to:\n• Store and display your photos to your friends\n• Use them for the app's basic functions (thumbnails, previews)\n\nWe will NEVER:\n• Sell your photos\n• Use them in advertisements without permission\n• Give them to third parties",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Our Rights
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B6B).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "PicFlick's Rights",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "To keep PicFlick safe and enjoyable for everyone, we reserve the right to:\n\n• Remove any content that violates our policies\n• Suspend or ban users who break the rules\n• Modify or discontinue features (we'll try to give notice)\n• Update these terms (we'll notify you of major changes)\n• Limit or restrict access to maintain service quality\n• Report illegal activity to authorities when required by law\n\nWe make these decisions at our discretion, but we promise to be fair and consistent.",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Storage Overage & Downgrade Policy
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
                            text = "Storage Overage & Downgrade Policy",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "If your subscription downgrades and your storage is above the new tier limit, PicFlick grants a 7-day grace period to delete photos and return within your plan limit. During grace, we send daily warnings. After grace ends, PicFlick may automatically delete oldest photos in batches until your account is back within the storage limit.\n\nTo avoid deletion, keep backups and reduce storage before grace ends.",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Account Termination
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
                            text = "Account Termination",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "You can delete your account anytime in Settings. This permanently removes:\n• Your photos and data\n• Your friend connections\n• Your messages\n\nWe may also terminate accounts that:\n• Violate these terms repeatedly\n• Engage in illegal activity\n• Attempt to harm the platform or other users\n\nSome data may be retained for legal or security purposes even after deletion.",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Disclaimers
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
                            text = "Disclaimers",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "PicFlick is provided \"as is\" without warranties of any kind. We:\n\n• Don't guarantee the service will always be available or error-free\n• Aren't responsible for content posted by users\n• Aren't liable for disputes between users\n• Recommend you back up important photos elsewhere\n\nIf something goes wrong, our liability is limited to what you paid us (which is often nothing for free users).",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Governing Law
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
                            text = "Governing Law",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "These terms are governed by the laws of your jurisdiction. Any disputes will be resolved through:\n\n1. Direct communication with us (we're reasonable people)\n2. Mediation if needed\n3. Courts as a last resort\n\nWe prefer to work things out directly rather than getting lawyers involved.",
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Questions
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBack() },
                    colors = CardDefaults.cardColors(
                        containerColor = accentColor.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Questions about these terms?",
                            color = textColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Go to Settings > Contact Us",
                            color = accentColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom spacing removed
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
