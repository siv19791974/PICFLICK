package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.R
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.data.UserProfile
import com.picflick.app.data.getColor
import com.picflick.app.data.getDailyUploadLimit
import com.picflick.app.data.getDarkColor
import com.picflick.app.data.getDisplayName
import com.picflick.app.data.getLightColor
import com.picflick.app.data.getMonthlyPrice
import com.picflick.app.data.getNextTier
import com.picflick.app.data.getQualityDescription
import com.picflick.app.data.getStorageLimitGB
import com.picflick.app.data.getStorageLimitBytes
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.isDarkModeSurface
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.SubscriptionProduct

/**
 * Manage Storage Screen - Full storage dashboard
 * Shows storage usage, tier status, and upgrade options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStorageScreen(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    onBack: () -> Unit,
    onUpgrade: (SubscriptionTier) -> Unit = {}
) {
    val tier = userProfile.subscriptionTier
    val tierColor = tier.getColor()
    val storageUsed = userProfile.storageUsedBytes
    val storageLimit = tier.getStorageLimitBytes()
    val storagePercent = if (storageLimit > 0) {
        (storageUsed * 100 / storageLimit).toInt()
    } else 0
    val usedGB = storageUsed / (1024.0 * 1024.0 * 1024.0)
    val totalGB = storageLimit / (1024.0 * 1024.0 * 1024.0)
    val remainingGB = totalGB - usedGB
    val isDarkMode = ThemeManager.isDarkMode.value
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manage Storage",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) Color.Black else Color(0xFF1565C0)
                )
            )
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Large Storage Meter Card
            StorageMeterCard(
                usedGB = usedGB,
                totalGB = totalGB,
                remainingGB = remainingGB,
                percent = storagePercent,
                tier = tier,
                tierColor = tierColor,
                isDarkMode = isDarkMode
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current Tier Status
            CurrentTierCard(
                tier = tier,
                tierColor = tierColor,
                isFounder = userProfile.isFounder,
                photosCount = userProfile.totalPhotos,
                dailyUploads = userProfile.dailyUploadsToday,
                dailyLimit = tier.getDailyUploadLimit(),
                isDarkMode = isDarkMode
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Upgrade Options
            if (tier.getNextTier() != null) {
                UpgradeOptionsCard(
                    currentTier = tier,
                    billingViewModel = billingViewModel,
                    onUpgrade = onUpgrade,
                    isDarkMode = isDarkMode
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Storage Breakdown
            StorageBreakdownCard(
                photosCount = userProfile.totalPhotos,
                usedGB = usedGB,
                averagePhotoSize = if (userProfile.totalPhotos > 0) usedGB / userProfile.totalPhotos else 0.0,
                quality = tier.getQualityDescription(),
                isDarkMode = isDarkMode
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Storage Tips
            StorageTipsCard(
                percent = storagePercent,
                isDarkMode = isDarkMode
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StorageMeterCard(
    usedGB: Double,
    totalGB: Double,
    remainingGB: Double,
    percent: Int,
    tier: SubscriptionTier,
    tierColor: Color,
    isDarkMode: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage Used",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color.White else Color(0xFF424242)
                )
                
                // Tier Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tier.getDisplayName(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Big percentage display
            Text(
                text = "$percent%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    percent > 90 -> Color.Red
                    percent > 75 -> Color(0xFFFF8F00)
                    else -> Color(0xFF1565C0)
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE0E0E0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent / 100f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    tierColor.copy(alpha = 0.7f),
                                    tierColor
                                )
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageStat(
                    label = "Used",
                    value = String.format("%.1f GB", usedGB),
                    color = tierColor,
                    isDarkMode = isDarkMode
                )
                StorageStat(
                    label = "Total",
                    value = String.format("%.0f GB", totalGB),
                    color = if (isDarkMode) Color.Gray else Color.DarkGray,
                    isDarkMode = isDarkMode
                )
                StorageStat(
                    label = "Remaining",
                    value = String.format("%.1f GB", remainingGB),
                    color = if (remainingGB < 5) Color.Red else Color(0xFF00C853),
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String, color: Color, isDarkMode: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isDarkMode) Color.Gray else Color.DarkGray
        )
    }
}

@Composable
private fun CurrentTierCard(
    tier: SubscriptionTier,
    tierColor: Color,
    isFounder: Boolean,
    photosCount: Int,
    dailyUploads: Int,
    dailyLimit: Int,
    isDarkMode: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Tier Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    tierColor,
                                    tier.getDarkColor(),
                                    tier.getLightColor(),
                                    tierColor
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = stringResource(R.string.content_desc_storage),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = tier.getDisplayName(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                    if (isFounder) {
                        Text(
                            text = "Founder Status - 1 Year Free",
                            fontSize = 12.sp,
                            color = Color(0xFF6200EA),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE0E0E0))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Features List
            TierFeatureRow(
                icon = Icons.Default.Photo,
                label = "Daily Uploads",
                value = if (dailyLimit == Int.MAX_VALUE) "$dailyUploads / Unlimited" else "$dailyUploads / $dailyLimit",
                isUnlimited = dailyLimit == Int.MAX_VALUE,
                isDarkMode = isDarkMode
            )
            
            TierFeatureRow(
                icon = Icons.Default.Storage,
                label = "Storage",
                value = "${tier.getStorageLimitGB()} GB",
                isHighlighted = true,
                isDarkMode = isDarkMode
            )
            
            TierFeatureRow(
                icon = Icons.Default.Check,
                label = "Photo Quality",
                value = tier.getQualityDescription(),
                isHighlighted = true,
                isDarkMode = isDarkMode
            )
            
            TierFeatureRow(
                icon = Icons.Default.Photo,
                label = "Total Photos",
                value = "$photosCount stored",
                isHighlighted = false,
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
private fun TierFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isUnlimited: Boolean = false,
    isHighlighted: Boolean = false,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.content_desc_check),
            tint = if (isHighlighted) Color(0xFF1565C0) else if (isDarkMode) Color.Gray else Color.DarkGray,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF424242)
        )
        
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                isUnlimited -> Color(0xFF00C853)
                isHighlighted -> Color(0xFF1565C0)
                else -> if (isDarkMode) Color.Gray else Color.DarkGray
            }
        )
    }
}

@Composable
private fun UpgradeOptionsCard(
    currentTier: SubscriptionTier,
    billingViewModel: BillingViewModel,
    onUpgrade: (SubscriptionTier) -> Unit,
    isDarkMode: Boolean
) {
    val nextTier = currentTier.getNextTier()
    val products: List<SubscriptionProduct> by billingViewModel.products.collectAsState()
    
    // Find product for next tier
    val nextTierProduct: SubscriptionProduct? = products.find { it.tier == nextTier }
    
    if (nextTier != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onUpgrade(nextTier) },
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFFFF8E1) // Dark gray in dark mode, light amber in light
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Upgrade,
                    contentDescription = stringResource(R.string.content_desc_upgrade),
                    tint = Color(0xFFFF8F00),
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Upgrade to ${nextTier.getDisplayName()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color(0xFF424242)
                    )
                    Text(
                        text = "${nextTier.getStorageLimitGB()} GB • ${nextTier.getDailyUploadLimit()} uploads/day",
                        fontSize = 13.sp,
                        color = if (isDarkMode) Color.Gray else Color.DarkGray
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    // Use actual product price if available, otherwise fallback
                                        val priceText = nextTierProduct?.price
                        ?: nextTier?.getMonthlyPrice()?.let { "$${it}" }
                        ?: "N/A"
                    Text(
                        text = priceText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Text(
                        text = "/month",
                        fontSize = 12.sp,
                        color = if (isDarkMode) Color.Gray else Color.DarkGray
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.content_desc_next),
                    tint = Color(0xFF1565C0)
                )
            }
        }
    }
}

@Composable
private fun StorageBreakdownCard(
    photosCount: Int,
    usedGB: Double,
    averagePhotoSize: Double,
    quality: String,
    isDarkMode: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Storage Breakdown",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkMode) Color.White else Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            BreakdownRow(
                label = "Photos stored",
                value = "$photosCount",
                isDarkMode = isDarkMode
            )
            
            BreakdownRow(
                label = "Average photo size",
                value = String.format("%.1f MB", averagePhotoSize * 1024),
                isDarkMode = isDarkMode
            )
            
            BreakdownRow(
                label = "Current quality",
                value = quality,
                isDarkMode = isDarkMode
            )
            
            BreakdownRow(
                label = "Storage used",
                value = String.format("%.2f GB", usedGB),
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, isDarkMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (isDarkMode) Color.Gray else Color.DarkGray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF424242)
        )
    }
}

@Composable
private fun StorageTipsCard(percent: Int, isDarkMode: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                percent > 90 -> if (isDarkMode) Color(0xFF3D1F1F) else Color(0xFFFFEBEE) // Dark red / Light red
                percent > 75 -> if (isDarkMode) Color(0xFF3D2E1F) else Color(0xFFFFF3E0) // Dark orange / Light orange
                else -> if (isDarkMode) Color(0xFF1F3D1F) else Color(0xFFE8F5E9) // Dark green / Light green
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when {
                    percent > 90 -> Icons.Default.Warning
                    percent > 75 -> Icons.Default.Info
                    else -> Icons.Default.Check
                },
                contentDescription = when {
                    percent > 90 -> stringResource(R.string.content_desc_warning)
                    percent > 75 -> stringResource(R.string.content_desc_info)
                    else -> stringResource(R.string.content_desc_check)
                },
                tint = when {
                    percent > 90 -> Color.Red
                    percent > 75 -> Color(0xFFFF8F00)
                    else -> Color(0xFF00C853)
                },
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = when {
                        percent > 90 -> "Storage almost full!"
                        percent > 75 -> "Running low on space"
                        else -> "Storage looking good"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        percent > 90 -> Color.Red
                        percent > 75 -> Color(0xFFFF8F00)
                        else -> Color(0xFF00C853)
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = when {
                        percent > 90 -> "Upgrade your plan or delete old photos to free up space."
                        percent > 75 -> "Consider upgrading soon to get more storage for your memories."
                        else -> "You have plenty of space for more photos. Keep capturing moments!"
                    },
                    fontSize = 13.sp,
                    color = if (isDarkMode) Color.Gray else Color.DarkGray
                )
            }
        }
    }
}
