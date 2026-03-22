package com.picflick.app.viewmodel

import android.app.Activity
import android.content.Context
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
}

/**
 * ViewModel for handling Google Play Billing
 * Manages subscriptions, purchases, and billing state
 */
class BillingViewModel : ViewModel() {

    private var billingClient: BillingClient? = null
    private lateinit var functions: FirebaseFunctions

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
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val subscriptionProducts = productDetailsList.mapNotNull { productDetails ->
                    val tier = getTierFromProductId(productDetails.productId)
                    val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
                    val pricingPhase = offerDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()

                    if (tier != null && pricingPhase != null) {
                        SubscriptionProduct(
                            productId = productDetails.productId,
                            tier = tier,
                            name = productDetails.name,
                            description = productDetails.description,
                            price = pricingPhase.formattedPrice,
                            billingPeriod = pricingPhase.billingPeriod,
                            productDetails = productDetails
                        )
                    } else null
                }
                _products.value = subscriptionProducts
            }
        }
    }

    /**
     * Query existing purchases
     */
    private fun queryPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _purchases.value = purchasesList
                updateCurrentTier(purchasesList)
            }
        }
    }

    /**
     * Update current tier based on active purchases
     */
    private fun updateCurrentTier(purchases: List<Purchase>) {
        val activePurchase = purchases.find { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val tier = activePurchase?.let { purchase ->
            purchase.products.firstOrNull()?.let { productId ->
                getTierFromProductId(productId)
            }
        } ?: SubscriptionTier.FREE

        _currentTier.value = tier
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

    /**
     * Launch purchase flow
     */
    fun purchaseSubscription(activity: Activity, product: SubscriptionProduct) {
        _isLoading.value = true
        _billingEvent.value = BillingEvent.PurchaseInitiated

        val offerToken = product.productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: run {
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

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

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
                
                val data = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to purchase.products.firstOrNull(),
                    "packageName" to "com.picflick.app"
                )
                
                val result = functions
                    .getHttpsCallable("validatePurchase")
                    .call(data)
                    .await()
                
                // Access the result data through the public accessor
                @Suppress("UNCHECKED_CAST")
                val resultData = result.getData() as? Map<String, Any>
                
                if (resultData?.get("success") == true) {
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
        val productId = when (tier) {
            SubscriptionTier.STANDARD -> if (yearly) PRODUCT_STANDARD_YEARLY else PRODUCT_STANDARD_MONTHLY
            SubscriptionTier.PLUS -> if (yearly) PRODUCT_PLUS_YEARLY else PRODUCT_PLUS_MONTHLY
            SubscriptionTier.PRO -> if (yearly) PRODUCT_PRO_YEARLY else PRODUCT_PRO_MONTHLY
            SubscriptionTier.ULTRA -> if (yearly) PRODUCT_ULTRA_YEARLY else PRODUCT_ULTRA_MONTHLY
            else -> return null
        }
        return _products.value.find { it.productId == productId }
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
