/**
 * Get storage subtitle text for the Manage Storage settings item
 */
private fun getStorageSubtitle(userProfile: UserProfile): String {
    val usedGB = userProfile.storageUsedBytes / (1024.0 * 1024.0 * 1024.0)
    val tier = userProfile.subscriptionTier
    val limitGB = tier.getStorageLimitGB()
    
    return when {
        userProfile.isFounder -> String.format("%.1f GB used - Founder (1 year free)", usedGB)
        tier == SubscriptionTier.FREE -> String.format("%.1f GB / %d GB free", usedGB, limitGB)
        else -> String.format("%.1f GB / %d GB %s", usedGB, limitGB, tier.getDisplayName())
    }
}

/**
 * Get subscription subtitle text for the Subscription settings item
 */
private fun getSubscriptionSubtitle(userProfile: UserProfile): String {
    val tier = userProfile.subscriptionTier
    
    return when {
        userProfile.isFounder -> "$${tier.getMonthlyPrice()} ${tier.getDisplayName()} - Founder 1 year free"
        tier == SubscriptionTier.FREE -> "Upgrade for more features"
        else -> "$${tier.getMonthlyPrice()}/mo ${tier.getDisplayName()}"
    }
}
