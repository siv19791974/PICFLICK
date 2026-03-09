package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground

/**
 * About/Version Screen - App info, version, changelog, and links
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPrivacyPolicy: () -> Unit = {},
    onLegal: () -> Unit = {},
    onPhilosophy: () -> Unit = {},
    onContactUs: () -> Unit = {}
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
                        text = "About",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Logo/Icon Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Icon Placeholder
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "PicFlick Logo",
                            modifier = Modifier.size(50.dp),
                            tint = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "PicFlick",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )

                    Text(
                        text = "Private Photo Sharing",
                        fontSize = 16.sp,
                        color = subtitleColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Version Badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Version 1.0.0",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Build 20250309.1",
                        fontSize = 12.sp,
                        color = subtitleColor
                    )
                }
            }

            // Quick Links
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBackground
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Quick Links",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Our Philosophy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPhilosophy() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Our Philosophy",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "What we stand for",
                                    fontSize = 13.sp,
                                    color = subtitleColor
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = subtitleColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                        // Privacy Policy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPrivacyPolicy() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Privacy Policy",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "Your data, your control",
                                    fontSize = 13.sp,
                                    color = subtitleColor
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = subtitleColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                        // Legal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLegal() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Legal",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "Terms and conditions",
                                    fontSize = 13.sp,
                                    color = subtitleColor
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = subtitleColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray, thickness = 0.5.dp)

                        // Contact Us
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onContactUs() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Contact Us",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "Get help and support",
                                    fontSize = 13.sp,
                                    color = subtitleColor
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = subtitleColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // What's New / Recent Improvements
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
                            text = "✨ What's New",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Changelog items
                        ChangelogItem(
                            icon = "📸",
                            title = "Maximum Photo Quality",
                            description = "Photos now upload at 100% JPEG quality for the best possible image quality",
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ChangelogItem(
                            icon = "⚖️",
                            title = "New Legal Section",
                            description = "Added Terms, Content Policy, and clear rights information",
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ChangelogItem(
                            icon = "🎯",
                            title = "Philosophy Page",
                            description = "Learn what makes PicFlick different from big social media",
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ChangelogItem(
                            icon = "🔒",
                            title = "Privacy Improvements",
                            description = "Enhanced privacy controls and clearer data policies",
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ChangelogItem(
                            icon = "✓",
                            title = "Upload Animation",
                            description = "New countdown animation shows remaining daily uploads after posting",
                            isDarkMode = isDarkMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ChangelogItem(
                            icon = "🐛",
                            title = "Bug Fixes",
                            description = "Fixed profile photo disappearing and navigation color issues",
                            isDarkMode = isDarkMode
                        )
                    }
                }
            }

            // Made With Love
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = accentColor.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Made with ❤️ for friends and family",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "© 2025 PicFlick Team",
                            fontSize = 13.sp,
                            color = subtitleColor
                        )
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

@Composable
private fun ChangelogItem(
    icon: String,
    title: String,
    description: String,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = subtitleColor,
                lineHeight = 18.sp
            )
        }
    }
}
