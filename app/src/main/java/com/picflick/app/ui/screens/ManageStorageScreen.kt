package com.picflick.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.picflick.app.ui.components.PullRefreshContainer
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.SubscriptionProduct
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Manage Storage Screen - Full storage dashboard
 * Shows storage usage, tier status, and upgrade options
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ManageStorageScreen(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    onBack: () -> Unit,
    onUpgrade: (SubscriptionTier) -> Unit = {}
) {
    val tier = userProfile.subscriptionTier
    val tierColor = tier.getColor()
    var storageUsed by remember(userProfile.uid) { mutableLongStateOf(userProfile.storageUsedBytes) }
    val storageLimit = tier.getStorageLimitBytes()
    val storagePercent = if (storageLimit > 0) {
        (storageUsed * 100 / storageLimit).toInt()
    } else 0
    val displayPercent = storagePercent.coerceAtMost(100)
    val usedGB = storageUsed / (1024.0 * 1024.0 * 1024.0)
    val totalGB = storageLimit / (1024.0 * 1024.0 * 1024.0)
    val remainingGB = totalGB - usedGB
    val isDarkMode = ThemeManager.isDarkMode.value

    // Live storage sync from user profile document.
    DisposableEffect(userProfile.uid) {
        val registration: ListenerRegistration = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userProfile.uid)
            .addSnapshotListener { snapshot, _ ->
                val liveBytes = snapshot?.getNumericLong("storageUsedBytes") ?: return@addSnapshotListener
                storageUsed = liveBytes
            }

        onDispose {
            registration.remove()
        }
    }

    // One-time backfill for legacy users who have photos but no storageUsedBytes tracked yet.
    LaunchedEffect(userProfile.uid) {
        if (storageUsed > 0L) return@LaunchedEffect
        val recalculatedBytes = runCatching { calculateStorageUsedBytesFromFiles(userProfile.uid) }.getOrNull() ?: return@LaunchedEffect
        if (recalculatedBytes > 0L) {
            storageUsed = recalculatedBytes
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userProfile.uid)
                    .update("storageUsedBytes", recalculatedBytes)
                    .await()
            }
        }
    }
    
    // Calculate actual photo count from Firestore
    var actualPhotoCount by remember { mutableIntStateOf(userProfile.totalPhotos) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshNonce++
        }
    )

    LaunchedEffect(userProfile.uid, refreshNonce) {
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("flicks")
                .whereEqualTo("userId", userProfile.uid)
                .get()
                .await()
            actualPhotoCount = snapshot.size()
        } catch (_: Exception) {
            // Fallback to profile count if query fails
            actualPhotoCount = userProfile.totalPhotos
        } finally {
            isRefreshing = false
        }
    }
    
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
                        text = "Manage Storage",
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
        PullRefreshContainer(
            refreshing = isRefreshing,
            pullRefreshState = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Large Storage Meter Card
                StorageMeterCard(
                    usedGB = usedGB,
                    totalGB = totalGB,
                    remainingGB = remainingGB,
                    percent = displayPercent,
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
                    photosCount = actualPhotoCount,
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

                // Storage Tips
                StorageTipsCard(
                    percent = storagePercent,
                    isDarkMode = isDarkMode
                )

                Spacer(modifier = Modifier.height(80.dp))
            }
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
                    percent < 60 -> Color(0xFF00C853)  // Green
                    percent < 80 -> Color(0xFFFFD600)  // Yellow
                    percent < 90 -> Color(0xFFFF6D00)  // Orange
                    else -> Color.Red                   // Red
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar with color coding
            val barColor = when {
                percent < 60 -> Color(0xFF00C853)  // Green
                percent < 80 -> Color(0xFFFFD600)  // Yellow
                percent < 90 -> Color(0xFFFF6D00)  // Orange
                else -> Color.Red                   // Red
            }
            
            val progressFraction = (percent / 100f).coerceIn(0f, 1f)
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
                        .fillMaxWidth(progressFraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(barColor)
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
                    value = String.format(Locale.US, "%.2f GB", usedGB),
                    color = tierColor,
                    isDarkMode = isDarkMode
                )
                StorageStat(
                    label = "Total",
                    value = String.format(Locale.US, "%.0f GB", totalGB),
                    color = if (isDarkMode) Color.Gray else Color.DarkGray,
                    isDarkMode = isDarkMode
                )
                StorageStat(
                    label = "Remaining",
                    value = String.format(Locale.US, "%.2f GB", remainingGB),
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
                containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
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
                    tint = if (isDarkMode) Color(0xFFFF8F00) else Color(0xFF1565C0),
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
                        ?: nextTier.getMonthlyPrice().let { "\$it" }
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

private fun DocumentSnapshot.getNumericLong(field: String): Long? {
    val value = get(field) ?: return null
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private suspend fun calculateStorageUsedBytesFromFiles(userId: String): Long {
    val root = FirebaseStorage.getInstance().reference.child("photos").child(userId)
    return calculateFolderBytesRecursive(root)
}

private suspend fun calculateFolderBytesRecursive(folderRef: StorageReference): Long {
    val listResult = folderRef.listAll().await()
    var total = 0L

    listResult.items.forEach { itemRef ->
        total += itemRef.metadata.await().sizeBytes
    }

    listResult.prefixes.forEach { childFolder ->
        total += calculateFolderBytesRecursive(childFolder)
    }

    return total
}

@Composable
private fun StorageTipsCard(percent: Int, isDarkMode: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                percent >= 110 -> if (isDarkMode) Color(0xFF3D1F1F) else Color(0xFFFFEBEE)
                percent >= 90 -> if (isDarkMode) Color(0xFF3D2E1F) else Color(0xFFFFF3E0)
                else -> if (isDarkMode) Color(0xFF1F3D1F) else Color(0xFFE8F5E9)
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
                    percent >= 110 -> Icons.Default.Warning
                    percent >= 90 -> Icons.Default.Info
                    else -> Icons.Default.Check
                },
                contentDescription = when {
                    percent >= 110 -> stringResource(R.string.content_desc_warning)
                    percent >= 90 -> stringResource(R.string.content_desc_info)
                    else -> stringResource(R.string.content_desc_check)
                },
                tint = when {
                    percent >= 110 -> Color.Red
                    percent >= 90 -> Color(0xFFFF8F00)
                    else -> Color(0xFF00C853)
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = when {
                        percent >= 110 -> "Storage max reached (110%)"
                        percent >= 90 -> "Storage warning (90%+)"
                        else -> "Storage looking good"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        percent >= 110 -> Color.Red
                        percent >= 90 -> Color(0xFFFF8F00)
                        else -> Color(0xFF00C853)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        percent >= 110 -> "Uploads are paused. Delete photos or upgrade your plan to continue."
                        percent >= 100 -> "Storage appears full. Grace uploads may still complete until 110%."
                        percent >= 90 -> "You are close to full storage."
                        else -> "You have plenty of space for more photos."
                    },
                    fontSize = 13.sp,
                    color = if (isDarkMode) Color.Gray else Color.DarkGray
                )
            }
        }
    }
}

