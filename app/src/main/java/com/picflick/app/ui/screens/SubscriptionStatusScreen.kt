package com.app.picflick.ui.screens

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
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.picflick.data.SubscriptionTier
import com.app.picflick.data.UserProfile
import com.app.picflick.data.getColor
import com.app.picflick.data.getDailyUploadLimit
import com.app.picflick.data.getDarkColor
import com.app.picflick.data.getDisplayName
import com.app.picflick.data.getLightColor
import com.app.picflick.data.getMonthlyPrice
import com.app.picflick.data.getQualityDescription
import com.app.picflick.data.getStorageLimitGB
import com.app.picflick.data.getStorageLimitBytes
import com.app.picflick.viewmodel.BillingEvent
import com.app.picflick.viewmodel.BillingViewModel
import com.app.picflick.viewmodel.SubscriptionProduct

/**
 * Subscription Status Screen - Financial/tier details
 * Shows current tier, upgrade/downgrade options, and billing info
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionStatusScreen(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    onBack: () -> Unit,
    onUpgrade: (SubscriptionTier) -> Unit = {},
    onDowngrade: (SubscriptionTier) -> Unit = {},
    onManagePayment: () -> Unit = {}
) {
    val tier = userProfile.subscriptionTier
    val tierColor = tier.getColor()
    
    // Collect billing state with explicit types
    val products: List<SubscriptionProduct> by billingViewModel.products.collectAsState()
    val isLoading: Boolean by billingViewModel.isLoading.collectAsState()
    val billingEvent: BillingEvent? by billingViewModel.billingEvent.collectAsState()
    
    // Handle billing events
    billingEvent?.let { event: BillingEvent ->
        when (event) {
            is BillingEvent.PurchaseSuccess -> {
                // Show success message
                // Could use a Snackbar here
            }
            is BillingEvent.PurchaseError -> {
                // Show error message
            }
            else -> {}
        }
        billingViewModel.clearBillingEvent()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Subscription",
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
                    containerColor = tierColor
                )
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Current Tier Card
            CurrentSubscriptionCard(
                userProfile = userProfile,
                tier = tier,
                tierColor = tierColor,
                onManagePayment = onManagePayment,
                products = products
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Usage This Month
            UsageCard(
                userProfile = userProfile,
                tier = tier
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // All Tiers Comparison
            AllTiersCard(
                currentTier = tier,
                billingViewModel = billingViewModel,
                products = products,
                onUpgrade = onUpgrade,
                onDowngrade = onDowngrade
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Founder Info (if applicable)
            if (userProfile.isFounder) {
                FounderCard(tier = tier)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CurrentSubscriptionCard(
    userProfile: UserProfile,
    tier: SubscriptionTier,
    tierColor: Color,
    onManagePayment: () -> Unit,
    products: List<SubscriptionProduct>
) {
    // Find product for current tier
    val currentProduct = products.find { it.tier == tier }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Large Tier Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
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
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = tier.getDisplayName(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                    
                    if (userProfile.isFounder) {
                        Text(
                            text = "Founder Status",
                            fontSize = 14.sp,
                            color = Color(0xFF6200EA),
                            fontWeight = FontWeight.Medium
                        )
                    } else if (tier != SubscriptionTier.FREE) {
                        // Use actual product price if available
                        val price = currentProduct?.price ?: "$${tier.getMonthlyPrice()}"
                        Text(
                            text = "$price/month",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            HorizontalDivider(color = Color(0xFFE0E0E0))
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tier Benefits
            Text(
                text = "Your Benefits",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            BenefitRow(
                icon = Icons.Default.Photo,
                text = "${tier.getDailyUploadLimit()} photos per day"
            )
            
            BenefitRow(
                icon = Icons.Default.PhotoLibrary,
                text = "${tier.getStorageLimitGB()} GB storage"
            )
            
            BenefitRow(
                icon = Icons.Default.Check,
                text = "${tier.getQualityDescription()} photo quality"
            )
            
            BenefitRow(
                icon = Icons.Default.Photo,
                text = "${userProfile.totalPhotos} stored"
            )
            
            if (tier == SubscriptionTier.FREE) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Upgrade to save more memories with better quality!",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            
            // Payment Info (only for paid tiers)
            if (tier != SubscriptionTier.FREE && !userProfile.isFounder) {
                Spacer(modifier = Modifier.height(20.dp))
                
                HorizontalDivider(color = Color(0xFFE0E0E0))
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManagePayment),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Payment Method",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Google Play Billing",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242)
                        )
                    }
                    
                    Text(
                        text = "Manage",
                        fontSize = 14.sp,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF1565C0)
                    )
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00C853),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            fontSize = 15.sp,
            color = Color(0xFF424242)
        )
    }
}

@Composable
private fun UsageCard(
    userProfile: UserProfile,
    tier: SubscriptionTier
) {
    val dailyUsed = userProfile.dailyUploadsToday
    val dailyLimit = tier.getDailyUploadLimit()
    val dailyPercent = if (dailyLimit > 0 && dailyLimit != Int.MAX_VALUE) {
        (dailyUsed * 100 / dailyLimit)
    } else 0
    
    val storageUsed = userProfile.storageUsedBytes
    val storageLimit = tier.getStorageLimitBytes()
    val storagePercent = if (storageLimit > 0) {
        (storageUsed * 100 / storageLimit).toInt()
    } else 0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Usage This Month",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Uploads Progress
            UsageProgressRow(
                label = "Uploads",
                used = dailyUsed,
                limit = dailyLimit,
                percent = dailyPercent,
                unit = "photos"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Storage Progress
            val usedGB = storageUsed / (1024.0 * 1024.0 * 1024.0)
            val limitGB = storageLimit / (1024.0 * 1024.0 * 1024.0)
            
            UsageProgressRow(
                label = "Storage",
                used = usedGB.toInt(),
                limit = limitGB.toInt(),
                percent = storagePercent,
                unit = "GB"
            )
        }
    }
}

@Composable
private fun UsageProgressRow(
    label: String,
    used: Int,
    limit: Int,
    percent: Int,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(70.dp),
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        // Progress Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            percent > 80 -> Color.Red
                            percent > 60 -> Color(0xFFFF8F00)
                            else -> Color(0xFF00C853)
                        }
                    )
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Usage Text
        Text(
            text = if (limit == Int.MAX_VALUE) "$used" else "$used/$limit",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF424242),
            modifier = Modifier.width(60.dp)
        )
    }
}

@Composable
private fun AllTiersCard(
    currentTier: SubscriptionTier,
    billingViewModel: BillingViewModel,
    products: List<SubscriptionProduct>,
    onUpgrade: (SubscriptionTier) -> Unit,
    onDowngrade: (SubscriptionTier) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "All Plans",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // List all tiers
            SubscriptionTier.entries.forEach { tier ->
                val product = products.find { it.tier == tier }
                
                TierComparisonRow(
                    tier = tier,
                    product = product,
                    isCurrent = tier == currentTier,
                    onUpgrade = if (tier > currentTier) { -> onUpgrade(tier) } else null,
                    onDowngrade = if (tier < currentTier) { -> onDowngrade(tier) } else null
                )
                
                if (tier != SubscriptionTier.ULTRA) {
                    HorizontalDivider(
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TierComparisonRow(
    tier: SubscriptionTier,
    product: SubscriptionProduct?,
    isCurrent: Boolean,
    onUpgrade: (() -> Unit)?,
    onDowngrade: (() -> Unit)?
) {
    val tierColor = tier.getColor()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tier Color Dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tierColor)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Tier Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tier.getDisplayName(),
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) tierColor else Color(0xFF424242)
                )
                
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CURRENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00C853)
                        )
                    }
                }
            }
            
            Text(
                text = "${tier.getStorageLimitGB()} GB • ${tier.getDailyUploadLimit()} uploads/day",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        // Price or Action Button
        when {
            tier == SubscriptionTier.FREE -> {
                Text(
                    text = "Free",
                    fontSize = 15.sp,
                    color = Color.Gray
                )
            }
            isCurrent -> {
                // Use actual product price if available
                val price = product?.price ?: "$${tier.getMonthlyPrice()}"
                Text(
                    text = price,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF424242)
                )
            }
            onUpgrade != null -> {
                // Upgrade button
                val price = product?.price ?: "$${tier.getMonthlyPrice()}"
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = price,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = tierColor
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tierColor.copy(alpha = 0.15f))
                            .clickable(onClick = onUpgrade)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Upgrade",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tierColor
                        )
                    }
                }
            }
            onDowngrade != null -> {
                // Downgrade button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEEEEEE))
                        .clickable(onClick = onDowngrade)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Downgrade",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun FounderCard(tier: SubscriptionTier) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3E5F5) // Light purple
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6200EA)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "Founder Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6200EA)
                )
                Text(
                    text = "You get 1 year of ${tier.getDisplayName()} for free!",
                    fontSize = 14.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    text = "Thank you for being an early supporter.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
