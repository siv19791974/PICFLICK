package com.picflick.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground

/**
 * PicFlick Privacy Policy Screen
 * Your own privacy policy - not linking to another company
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val context = LocalContext.current
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
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
                        text = "Privacy Policy",
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
                PolicyHeader(isDarkMode = isDarkMode)
            }

            // Last Updated
            item {
                Text(
                    text = "Last Updated: March 2025",
                    color = subtitleColor,
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            // Introduction
            item {
                PolicySection(
                    title = "Introduction",
                    content = """PicFlick ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our mobile application.

By using PicFlick, you agree to the collection and use of information in accordance with this policy.""",
                    isDarkMode = isDarkMode
                )
            }

            // Information We Collect
            item {
                PolicySection(
                    title = "Information We Collect",
                    content = """We collect the following types of information:

• Account Information: Email address, display name, profile photo, phone number
• Photos & Content: Photos you upload, captions, tags, reactions
• Social Data: Friend connections, followers, blocked users list
• Device Information: Device ID for push notifications, app usage statistics
• Contact Information: Only if you choose to sync contacts for friend discovery

We do NOT collect:
• Your precise location
• Your contacts without explicit permission
• Payment information (handled by Google Play)""",
                    isDarkMode = isDarkMode
                )
            }

            // How We Use Information
            item {
                PolicySection(
                    title = "How We Use Your Information",
                    content = """We use your information to:

• Provide and maintain PicFlick services
• Display your photos to your friends (according to your privacy settings)
• Send push notifications for likes, comments, and new followers
• Suggest friends based on your contacts (if you opt-in)
• Improve our app and user experience
• Prevent fraud and abuse

We will NEVER:
• Sell your personal information to third parties
• Use your photos for advertising without permission
• Share your data with unauthorized parties""",
                    isDarkMode = isDarkMode
                )
            }

            // Data Storage
            item {
                PolicySection(
                    title = "Data Storage & Security",
                    content = """Your data is stored securely using:

• Firebase (Google Cloud) for encrypted storage
• Industry-standard security practices
• Regular security audits and updates

Your photos are stored in the cloud and remain accessible only to you and users you authorize based on your privacy settings.""",
                    isDarkMode = isDarkMode
                )
            }

            // Your Rights
            item {
                PolicySection(
                    title = "Your Rights",
                    content = """You have the right to:

• Access all your data stored in PicFlick
• Download your photos and data
• Delete your account and all associated data permanently
• Modify your privacy settings at any time
• Block other users from seeing your content
• Opt-out of friend discovery

To exercise these rights, go to Settings > Privacy in the app.""",
                    isDarkMode = isDarkMode
                )
            }

            // Children
            item {
                PolicySection(
                    title = "Children's Privacy",
                    content = """PicFlick is not intended for children under 13 years of age. We do not knowingly collect personal information from children under 13. If you are a parent and believe your child has provided us with personal information, please contact us immediately.""",
                    isDarkMode = isDarkMode
                )
            }

            // Changes
            item {
                PolicySection(
                    title = "Changes to This Policy",
                    content = """We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy in the app and updating the "Last Updated" date.

Continued use of PicFlick after changes constitutes acceptance of the revised policy.""",
                    isDarkMode = isDarkMode
                )
            }

            // Contact
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:privacy@picflick.app")
                                putExtra(Intent.EXTRA_SUBJECT, "PicFlick Privacy Question")
                            }
                            context.startActivity(intent)
                        },
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
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Questions? Contact Us",
                                color = textColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "privacy@picflick.app",
                                color = accentColor,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = subtitleColor,
                            modifier = Modifier.size(20.dp)
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
private fun PolicyHeader(isDarkMode: Boolean) {
    val accentColor = Color(0xFF1565C0)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PicFlick",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = "Privacy Policy",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkMode) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your privacy is our priority",
            fontSize = 14.sp,
            color = if (isDarkMode) Color.Gray else Color.DarkGray
        )
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val subtitleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBackground = if (isDarkMode) Color(0xFF1C1C1E) else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1565C0),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = content,
                fontSize = 14.sp,
                color = textColor,
                lineHeight = 20.sp
            )
        }
    }
}
