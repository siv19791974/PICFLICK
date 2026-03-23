package com.picflick.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picflick.app.data.SubscriptionTier
import com.picflick.app.data.UserProfile
import com.picflick.app.data.getColor
import com.picflick.app.data.getDailyUploadLimit
import com.picflick.app.data.getDisplayName
import com.picflick.app.data.getMonthlyPrice
import com.picflick.app.data.getStorageLimitGB
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.viewmodel.BillingEvent
import com.picflick.app.viewmodel.BillingViewModel
import com.picflick.app.viewmodel.SubscriptionProduct

/**
 * Plan Options Screen - Shows all subscription tiers with clear comparison
 * Users can view all plans and click to purchase
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanOptionsScreen(
    userProfile: UserProfile,
    billingViewModel: BillingViewModel,
    onBack: () -> Unit,
    onPurchase: (SubscriptionTier) -> Unit,
    onRestorePurchases: () -> Unit = {}
) {
    val isDarkMode = ThemeManager.isDarkMode.value
    val context = LocalContext.current
    val products: List<SubscriptionProduct> by billingViewModel.products.collectAsState()
    val billingEvent: BillingEvent? by billingViewModel.billingEvent.collectAsState()
    val currentTier = userProfile.subscriptionTier

    LaunchedEffect(billingEvent) {
        when (val event = billingEvent) {
            is BillingEvent.PurchasesRestored -> {
                val message = if (event.tier == SubscriptionTier.FREE) {
                    "No active purchases found"
                } else {
                    "Purchases restored: ${event.tier.getDisplayName()}"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                billingViewModel.clearBillingEvent()
            }
            is BillingEvent.PurchaseError -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                billingViewModel.clearBillingEvent()
            }
            null,
            is BillingEvent.PurchaseSuccess,
            is BillingEvent.PurchaseCancelled,
            is BillingEvent.AlreadyOwned,
            is BillingEvent.PurchaseInitiated -> Unit
        }
    }
    
    // All tiers to display
    val allTiers = listOf(
        SubscriptionTier.FREE,
        SubscriptionTier.STANDARD,
        SubscriptionTier.PLUS,
        SubscriptionTier.PRO,
        SubscriptionTier.ULTRA
    )
    
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
                        text = "Plan Options",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
        containerColor = isDarkModeBackground(isDarkMode)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header text
            Text(
                text = "Choose Your Plan",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) Color.White else Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Upgrade for more storage and daily uploads",
                fontSize = 14.sp,
                color = if (isDarkMode) Color.Gray else Color.DarkGray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedButton(
                onClick = onRestorePurchases,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore purchases")
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Current Plan indicator
            if (currentTier != SubscriptionTier.FREE) {
                CurrentPlanCard(
                    tier = currentTier,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // All Plan Cards
            allTiers.forEach { tier ->
                val isCurrentPlan = tier == currentTier
                val price = getTierPrice(tier, products)
                
                PlanCard(
                    tier = tier,
                    price = price,
                    isCurrentPlan = isCurrentPlan,
                    isDarkMode = isDarkMode,
                    onClick = { 
                        if (!isCurrentPlan) {
                            onPurchase(tier)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer note
            Text(
                text = "All plans include: Photo sharing • Friend connections • Privacy controls",
                fontSize = 12.sp,
                color = if (isDarkMode) Color.Gray else Color.DarkGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Subscriptions auto-renew. Cancel anytime in Play Store.",
                fontSize = 11.sp,
                color = if (isDarkMode) Color.Gray else Color.DarkGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CurrentPlanCard(
    tier: SubscriptionTier,
    modifier: Modifier = Modifier
) {
    val tierColor = tier.getColor()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = tierColor.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = tierColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Current Plan: ${tier.getDisplayName()}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = tierColor
            )
        }
    }
}

@Composable
private fun PlanCard(
    tier: SubscriptionTier,
    price: String,
    isCurrentPlan: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val tierColor = tier.getColor()
    val storageGB = tier.getStorageLimitGB()
    val dailyUploads = tier.getDailyUploadLimit()
    val uploadsText = if (dailyUploads == Int.MAX_VALUE) "Unlimited" else "$dailyUploads"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrentPlan, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentPlan) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with tier name and price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tier color indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = tier.getDisplayName(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                }
                
                if (isCurrentPlan) {
                    // Current Plan badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tierColor.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Current",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tierColor
                        )
                    }
                } else {
                    // Price
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = price,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = tierColor
                        )
                        if (tier != SubscriptionTier.FREE) {
                            Text(
                                text = "/month",
                                fontSize = 12.sp,
                                color = if (isDarkMode) Color.Gray else Color.DarkGray
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            HorizontalDivider(
                color = if (isDarkMode) Color(0xFF2C2C2E) else Color.LightGray,
                thickness = 0.5.dp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Features row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Storage
                FeatureItem(
                    icon = Icons.Default.Storage,
                    value = "$storageGB GB",
                    label = "Storage",
                    isDarkMode = isDarkMode
                )
                
                // Daily Uploads
                FeatureItem(
                    icon = Icons.Default.Photo,
                    value = uploadsText,
                    label = "Uploads/Day",
                    isDarkMode = isDarkMode
                )
                
                // Photos (estimated average at ~3 MB per photo)
                // Formula: GB × 1024 MB ÷ 3 MB per photo
                val estimatedPhotos = ((storageGB * 1024.0) / 3.0).toInt()
                val displayPhotos = when {
                    estimatedPhotos >= 1000 -> "${estimatedPhotos / 1000}K"
                    else -> "$estimatedPhotos"
                }
                FeatureItem(
                    icon = Icons.Default.Check,
                    value = "~$displayPhotos",
                    label = "Photos",
                    isDarkMode = isDarkMode
                )
            }
            
            // Button for non-current plans
            if (!isCurrentPlan) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tierColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upgrade,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (tier == SubscriptionTier.FREE) "Downgrade to Free" else "Choose ${tier.getDisplayName()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    isDarkMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDarkMode) Color.Gray else Color.DarkGray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDarkMode) Color.White else Color.Black
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isDarkMode) Color.Gray else Color.DarkGray
        )
    }
}

private fun getTierPrice(tier: SubscriptionTier, products: List<SubscriptionProduct>): String {
    return when (tier) {
        SubscriptionTier.FREE -> "FREE"
        else -> {
            val product = products.find { it.tier == tier }
            if (product != null) {
                product.price
            } else {
                val price = tier.getMonthlyPrice()
                if (price > 0) "$$price" else "N/A"
            }
        }
    }
}