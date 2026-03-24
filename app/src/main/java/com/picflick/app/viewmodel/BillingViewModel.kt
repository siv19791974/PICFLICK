package com.picflick.app.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import com.picflick.app.data.SubscriptionTier
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Data class representing a subscription product
 */
data class SubscriptionProduct(
    val productId: String,
    val tier: SubscriptionTier,
    val name: String,
    val description: String,
    val price: String,
    val billingPeriod: String,
    val isYearly: Boolean,
    val offerToken: String,
    val basePlanId: String,
    val offerId: String?,
    val offerTags: List<String>,
    val productDetails: ProductDetails
)

/**
 * Sealed class for billing purchase events
 */
sealed class BillingEvent {
    data object PurchaseInitiated : BillingEvent()
    data class PurchaseSuccess(val purchase: Purchase) : BillingEvent()
    data class PurchaseError(val errorCode: Int, val message: String) : BillingEvent()
    data object PurchaseCancelled : BillingEvent()
    data object AlreadyOwned : BillingEvent()
    data class PurchasesRestored(val count: Int, val tier: SubscriptionTier) : BillingEvent()
}

/**
 * ViewModel for handling Google Play Billing
 * Manages subscriptions, purchases, and billing state
 */
class BillingViewModel : ViewModel() {

    private var billingClient: BillingClient? = null
    private lateinit var functions: FirebaseFunctions
    private val validatedTierByPurchaseToken = mutableMapOf<String, SubscriptionTier>()

    // Product IDs for each subscription tier
    companion object {
        const val PRODUCT_STANDARD_MONTHLY = "picflick_standard_monthly"
        const val PRODUCT_PLUS_MONTHLY = "picflick_plus_monthly"
        const val PRODUCT_PRO_MONTHLY = "picflick_pro_monthly"
        const val PRODUCT_ULTRA_MONTHLY = "picflick_ultra_monthly"

        const val PRODUCT_STANDARD_YEARLY = "picflick_standard_yearly"
        const val PRODUCT_PLUS_YEARLY = "picflick_plus_yearly"
        const val PRODUCT_PRO_YEARLY = "picflick_pro_yearly"
        const val PRODUCT_ULTRA_YEARLY = "picflick_ultra_yearly"

        // Phase-1 migration support: unified product can coexist with legacy product IDs.
        const val PRODUCT_UNIFIED = "picflick_premium"
    }

    // State flows
    private val _products = MutableStateFlow<List<SubscriptionProduct>>(emptyList())
    val products: StateFlow<List<SubscriptionProduct>> = _products.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _billingEvent = MutableStateFlow<BillingEvent?>(null)
    val billingEvent: StateFlow<BillingEvent?> = _billingEvent.asStateFlow()

    private val _currentTier = MutableStateFlow(SubscriptionTier.FREE)
    val currentTier: StateFlow<SubscriptionTier> = _currentTier.asStateFlow()

    /**
     * Initialize the billing client
     */
    fun initialize(context: Context) {
        if (billingClient != null) return

        // Initialize Firebase Functions
        functions = FirebaseFunctions.getInstance()

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        connectToGooglePlay()
    }

    /**
     * Connect to Google Play Billing
     */
    private fun connectToGooglePlay() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    queryProducts()
                    queryPurchases()
                } else {
                    _isConnected.value = false
                    _billingEvent.value = BillingEvent.PurchaseError(
                        billingResult.responseCode,
                        "Failed to connect to billing: ${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                // Retry connection
                connectToGooglePlay()
            }
        })
    }

    /**
     * Query available subscription products
     */
    fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_STANDARD_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PLUS_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ULTRA_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_STANDARD_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PLUS_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ULTRA_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_UNIFIED)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val subscriptionProducts = productDetailsList.flatMap { productDetails ->
                    val offers = productDetails.subscriptionOfferDetails.orEmpty()

                    // Legacy SKUs typically expose one offer; unified product may expose many.
                    offers.mapNotNull { offerDetails ->
                        val pricingPhase = offerDetails.pricingPhases.pricingPhaseList
                            .firstOrNull { it.recurrenceMode != ProductDetails.RecurrenceMode.NON_RECURRING }
                            ?: offerDetails.pricingPhases.pricingPhaseList.firstOrNull()

                        val tier = resolveTier(productDetails.productId, offerDetails)
                        if (tier != null && pricingPhase != null) {
                            SubscriptionProduct(
                                productId = productDetails.productId,
                                tier = tier,
                                name = productDetails.name,
                                description = productDetails.description,
                                price = pricingPhase.formattedPrice,
                                billingPeriod = pricingPhase.billingPeriod,
                                isYearly = isYearlyCycle(pricingPhase.billingPeriod, offerDetails),
                                offerToken = offerDetails.offerToken,
                                basePlanId = offerDetails.basePlanId,
                                offerId = offerDetails.offerId,
                                offerTags = offerDetails.offerTags,
                                productDetails = productDetails
                            )
                        } else null
                    }
                }
                _products.value = subscriptionProducts
            }
        }
    }

    /**
     * Query existing purchases
     */
    private fun queryPurchases(emitRestoredEvent: Boolean = false) {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _purchases.value = purchasesList
                updateCurrentTier(purchasesList)

                if (emitRestoredEvent) {
                    viewModelScope.launch {
                        syncActivePurchasesWithServer(purchasesList)
                        _billingEvent.value = BillingEvent.PurchasesRestored(
                            purchasesList.size,
                            _currentTier.value
                        )
                        _isLoading.value = false
                    }
                } else {
                    _isLoading.value = false
                }
            } else {
                _isLoading.value = false
                if (emitRestoredEvent) {
                    _billingEvent.value = BillingEvent.PurchaseError(
                        billingResult.responseCode,
                        billingResult.debugMessage.ifBlank { "Failed to restore purchases" }
                    )
                }
            }
        }
    }

    private suspend fun syncActivePurchasesWithServer(purchasesList: List<Purchase>) {
        val activePurchases = purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        if (activePurchases.isEmpty()) {
            try {
                functions.getHttpsCallable("handlePurchaseCancelled")
                    .call(hashMapOf<String, Any>())
                    .await()
                _currentTier.value = SubscriptionTier.FREE
                Log.d("BillingViewModel", "No active purchases found; server subscription state downgraded to FREE")
            } catch (e: Exception) {
                Log.w("BillingViewModel", "Failed to sync no-active-purchase state with server", e)
            }
            return
        }

        activePurchases.forEach { purchase ->
            val productId = purchase.products.firstOrNull() ?: return@forEach
            try {
                val resolvedProduct = resolveProductForPurchase(purchase)
                val data = hashMapOf<String, Any>(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId,
                    "packageName" to "com.picflick.app"
                ).apply {
                    resolvedProduct?.let {
                        this["tierHint"] = it.tier.name
                        this["isYearlyHint"] = it.isYearly
                        this["basePlanIdHint"] = it.basePlanId
                        this["offerIdHint"] = it.offerId ?: ""
                        this["offerTagsHint"] = it.offerTags
                    }
                }
                val result = functions.getHttpsCallable("validatePurchase").call(data).await()
                applyValidatedTierFromResult(purchase, result.getData())
            } catch (e: Exception) {
                Log.w("BillingViewModel", "Restore sync validation failed for $productId", e)
            }
        }
    }

    fun restorePurchases() {
        if (_isConnected.value.not()) {
            _billingEvent.value = BillingEvent.PurchaseError(
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                "Billing service is not connected yet"
            )
            return
        }

        _isLoading.value = true
        queryPurchases(emitRestoredEvent = true)
    }

    /**
     * Update current tier based on active purchases
     */
    private fun updateCurrentTier(purchases: List<Purchase>) {
        val highestActiveTier = purchases
            .asSequence()
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .mapNotNull { purchase ->
                purchase.products
                    .asSequence()
                    .mapNotNull { productId -> getTierFromProductId(productId) }
                    .maxByOrNull { it.ordinal }
                    ?: validatedTierByPurchaseToken[purchase.purchaseToken]
            }
            .maxByOrNull { tier -> tier.ordinal }
            ?: SubscriptionTier.FREE

        _currentTier.value = highestActiveTier
    }

    /**
     * Get tier from product ID
     */
    private fun getTierFromProductId(productId: String): SubscriptionTier? {
        return when (productId) {
            PRODUCT_STANDARD_MONTHLY, PRODUCT_STANDARD_YEARLY -> SubscriptionTier.STANDARD
            PRODUCT_PLUS_MONTHLY, PRODUCT_PLUS_YEARLY -> SubscriptionTier.PLUS
            PRODUCT_PRO_MONTHLY, PRODUCT_PRO_YEARLY -> SubscriptionTier.PRO
            PRODUCT_ULTRA_MONTHLY, PRODUCT_ULTRA_YEARLY -> SubscriptionTier.ULTRA
            else -> null
        }
    }

    private fun resolveTier(
        productId: String,
        offer: ProductDetails.SubscriptionOfferDetails
    ): SubscriptionTier? {
        // Legacy product IDs keep working.
        getTierFromProductId(productId)?.let { return it }

        // Unified product path: infer tier from base plan / offer metadata.
        val candidates = buildList {
            add(offer.basePlanId)
            offer.offerId?.let(::add)
            addAll(offer.offerTags)
        }.joinToString(" ").lowercase()

        return when {
            candidates.contains("standard") -> SubscriptionTier.STANDARD
            candidates.contains("plus") -> SubscriptionTier.PLUS
            candidates.contains("pro") -> SubscriptionTier.PRO
            candidates.contains("ultra") -> SubscriptionTier.ULTRA
            else -> null
        }
    }

    private fun isYearlyCycle(
        billingPeriod: String,
        offer: ProductDetails.SubscriptionOfferDetails
    ): Boolean {
        if (billingPeriod.contains("P1Y", ignoreCase = true)) return true

        val cycleHints = buildList {
            add(offer.basePlanId)
            offer.offerId?.let(::add)
            addAll(offer.offerTags)
        }.joinToString(" ").lowercase()

        return cycleHints.contains("year") || cycleHints.contains("annual")
    }

    private fun resolveProductForPurchase(purchase: Purchase): SubscriptionProduct? {
        val productId = purchase.products.firstOrNull() ?: return null
        val matches = _products.value.filter { it.productId == productId }

        return when {
            matches.isEmpty() -> null
            matches.size == 1 -> matches.first()
            else -> null // ambiguous for unified product restore without extra purchase metadata
        }
    }

    private fun applyValidatedTierFromResult(purchase: Purchase, resultData: Any?) {
        @Suppress("UNCHECKED_CAST")
        val data = resultData as? Map<String, Any?> ?: return
        val tierName = data["tier"] as? String ?: return
        val parsedTier = runCatching { SubscriptionTier.valueOf(tierName.uppercase()) }.getOrNull() ?: return

        validatedTierByPurchaseToken[purchase.purchaseToken] = parsedTier
        if (parsedTier.ordinal >= _currentTier.value.ordinal) {
            _currentTier.value = parsedTier
        }
    }

    /**
     * Launch purchase flow
     */
    fun purchaseSubscription(activity: Activity, product: SubscriptionProduct) {
        _isLoading.value = true
        _billingEvent.value = BillingEvent.PurchaseInitiated

        val offerToken = product.offerToken.ifBlank {
            product.productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken.orEmpty()
        }
        if (offerToken.isBlank()) {
            _isLoading.value = false
            _billingEvent.value = BillingEvent.PurchaseError(
                BillingClient.BillingResponseCode.ERROR,
                "No offer token available"
            )
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product.productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))

        // Explicit upgrade/downgrade handling: tell Google Play this is a plan replacement
        // so billing/proration is handled correctly and users are not double-charged.
        val activePurchase = _purchases.value.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.isNotEmpty()
        }
        val currentProductId = activePurchase?.products?.firstOrNull()
        if (activePurchase != null && currentProductId != null && currentProductId != product.productId) {
            val updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(activePurchase.purchaseToken)
                .setSubscriptionReplacementMode(
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                )
                .build()
            billingFlowBuilder.setSubscriptionUpdateParams(updateParams)
        }

        val billingFlowParams = billingFlowBuilder.build()

        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)

        if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
            _isLoading.value = false
            _billingEvent.value = BillingEvent.PurchaseError(
                billingResult?.responseCode ?: BillingClient.BillingResponseCode.ERROR,
                billingResult?.debugMessage ?: "Unknown error"
            )
        }
    }

    /**
     * Handle purchase updates
     */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        _isLoading.value = false

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { purchaseList ->
                    _purchases.value = purchaseList
                    updateCurrentTier(purchaseList)
                    purchaseList.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                    _billingEvent.value = BillingEvent.PurchaseSuccess(purchaseList.first())
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingEvent.value = BillingEvent.PurchaseCancelled
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _billingEvent.value = BillingEvent.AlreadyOwned
                queryPurchases() // Refresh purchases
            }
            else -> {
                _billingEvent.value = BillingEvent.PurchaseError(
                    billingResult.responseCode,
                    billingResult.debugMessage
                )
            }
        }
    }

    /**
     * Acknowledge purchase
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Purchase acknowledged - validate with server
                validatePurchaseWithServer(purchase)
            }
        }
    }

    /**
     * Validate purchase with Firebase Cloud Function
     * This verifies the purchase with Google Play API server-side
     */
    private fun validatePurchaseWithServer(purchase: Purchase) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val productId = purchase.products.firstOrNull()
                val resolvedProduct = resolveProductForPurchase(purchase)
                val data = hashMapOf<String, Any>(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to (productId ?: ""),
                    "packageName" to "com.picflick.app"
                ).apply {
                    resolvedProduct?.let {
                        this["tierHint"] = it.tier.name
                        this["isYearlyHint"] = it.isYearly
                        this["basePlanIdHint"] = it.basePlanId
                        this["offerIdHint"] = it.offerId ?: ""
                        this["offerTagsHint"] = it.offerTags
                    }
                }

                val result = functions
                    .getHttpsCallable("validatePurchase")
                    .call(data)
                    .await()
                
                // Access the result data through the public accessor
                @Suppress("UNCHECKED_CAST")
                val resultData = result.getData() as? Map<String, Any>
                
                if (resultData?.get("success") == true) {
                    applyValidatedTierFromResult(purchase, resultData)
                    _billingEvent.value = BillingEvent.PurchaseSuccess(purchase)
                    // Refresh purchases to get updated tier
                    queryPurchases()
                } else {
                    val errorMsg = resultData?.get("error") as? String ?: "Validation failed"
                    _billingEvent.value = BillingEvent.PurchaseError(
                        BillingClient.BillingResponseCode.ERROR,
                        errorMsg
                    )
                }
            } catch (e: Exception) {
                _billingEvent.value = BillingEvent.PurchaseError(
                    BillingClient.BillingResponseCode.ERROR,
                    "Server validation error: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Check if user has active subscription
     */
    fun hasActiveSubscription(): Boolean {
        return _purchases.value.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    /**
     * Get product for a specific tier
     */
    fun getProductForTier(tier: SubscriptionTier, yearly: Boolean = false): SubscriptionProduct? {
        if (tier == SubscriptionTier.FREE) return null

        // First prefer explicit cycle match from parsed offers (works for unified + legacy).
        _products.value.firstOrNull { it.tier == tier && it.isYearly == yearly }?.let { return it }

        // Backward-compatible fallback by legacy product IDs.
        val legacyProductId = when (tier) {
            SubscriptionTier.STANDARD -> if (yearly) PRODUCT_STANDARD_YEARLY else PRODUCT_STANDARD_MONTHLY
            SubscriptionTier.PLUS -> if (yearly) PRODUCT_PLUS_YEARLY else PRODUCT_PLUS_MONTHLY
            SubscriptionTier.PRO -> if (yearly) PRODUCT_PRO_YEARLY else PRODUCT_PRO_MONTHLY
            SubscriptionTier.ULTRA -> if (yearly) PRODUCT_ULTRA_YEARLY else PRODUCT_ULTRA_MONTHLY
            else -> return null
        }
        return _products.value.find { it.productId == legacyProductId }
    }

    /**
     * Clear billing event
     */
    fun clearBillingEvent() {
        _billingEvent.value = null
    }

    /**
     * End connection when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        billingClient?.endConnection()
    }
}
