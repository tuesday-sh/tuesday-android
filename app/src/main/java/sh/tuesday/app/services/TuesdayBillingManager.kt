package sh.tuesday.app.services

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.tuesday.app.BuildConfig
import java.util.UUID

data class SubscriptionPlanOffer(
    val productId: String,
    val title: String,
    val formattedPrice: String,
    val periodLabel: String,
    val badge: String? = null
)

data class BillingUiState(
    val isConnected: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val selectedProductId: String = TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL,
    val plans: List<SubscriptionPlanOffer> = TuesdayBillingManager.defaultPlanOffers(),
    val statusMessage: String? = null
)

class TuesdayBillingManager(
    context: Context,
    private val repository: TuesdayRepository
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    init {
        startConnection()
    }

    fun selectPlan(productId: String) {
        if (productId in SUPPORTED_TIER1_SKUS) {
            _uiState.value = _uiState.value.copy(
                selectedProductId = productId,
                statusMessage = null
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun startConnection() {
        if (billingClient.isReady) {
            querySubscriptionProducts()
            return
        }
        try {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _uiState.value = _uiState.value.copy(isConnected = true)
                        querySubscriptionProducts()
                        queryActivePurchasesSilent()
                    } else {
                        Log.i("TuesdayBilling", "Play Billing setup finished with code ${billingResult.responseCode}: ${billingResult.debugMessage}")
                        _uiState.value = _uiState.value.copy(isConnected = false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _uiState.value = _uiState.value.copy(isConnected = false)
                }
            })
        } catch (e: Exception) {
            Log.w("TuesdayBilling", "Play Billing connection unavailable: ${e.message}")
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }

    fun querySubscriptionProducts() {
        if (!billingClient.isReady) return
        val productList = SUPPORTED_TIER1_SKUS.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                productDetailsMap.clear()
                val mappedOffers = mutableListOf<SubscriptionPlanOffer>()
                for (details in productDetailsList) {
                    productDetailsMap[details.productId] = details
                    val offer = details.subscriptionOfferDetails?.firstOrNull()
                    val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    val formattedPrice = pricingPhase?.formattedPrice
                        ?: if (details.productId == SKU_PRO_TIER1_MONTHLY) "$4.99" else "$49.00"
                    val isAnnual = details.productId == SKU_PRO_TIER1_ANNUAL
                    mappedOffers.add(
                        SubscriptionPlanOffer(
                            productId = details.productId,
                            title = if (isAnnual) "Annual" else "Monthly",
                            formattedPrice = formattedPrice,
                            periodLabel = if (isAnnual) "/ year" else "/ month",
                            badge = if (isAnnual) "SAVE 18%" else null
                        )
                    )
                }
                if (mappedOffers.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(plans = mappedOffers)
                }
            }
        }
    }

    fun launchPurchaseFlow(
        activity: Activity?,
        productId: String = _uiState.value.selectedProductId,
        onSuccess: (() -> Unit)? = null
    ) {
        val targetSku = if (productId in SUPPORTED_TIER1_SKUS) productId else SKU_PRO_TIER1_ANNUAL
        _uiState.value = _uiState.value.copy(
            selectedProductId = targetSku,
            isPurchasing = true,
            statusMessage = null
        )

        val details = productDetailsMap[targetSku]
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken

        if (activity != null && billingClient.isReady && details != null && !offerToken.isNullOrBlank()) {
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .setObfuscatedAccountId(repository.billingAccountIdentifier())
                .setObfuscatedProfileId(repository.billingProfileSlug())
                .build()

            val result = billingClient.launchBillingFlow(activity, flowParams)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                return
            }
            Log.w("TuesdayBilling", "launchBillingFlow returned ${result.responseCode}: ${result.debugMessage}")
        }

        // Sandbox / Debug / Sideload fallback when Play Store SKU is not provisioned on local/test builds
        if (BuildConfig.DEBUG || repository.isDeveloperMode.value) {
            val sandboxToken = "gp_sandbox_${UUID.randomUUID()}"
            repository.upgradeToProTier1(productId = targetSku, purchaseToken = sandboxToken)
            _uiState.value = _uiState.value.copy(
                isPurchasing = false,
                statusMessage = "✓ Activated Pro Tier 1 (${if (targetSku == SKU_PRO_TIER1_MONTHLY) "Monthly" else "Annual"} • Play Sandbox)"
            )
            onSuccess?.invoke()
        } else {
            _uiState.value = _uiState.value.copy(
                isPurchasing = false,
                statusMessage = "⚠ Google Play Store billing is unavailable on this device."
            )
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isPurchasing = false)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiState.value = _uiState.value.copy(
                    isPurchasing = false,
                    statusMessage = "Purchase canceled."
                )
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    isPurchasing = false,
                    statusMessage = "Purchase failed (${billingResult.debugMessage.ifBlank { "code ${billingResult.responseCode}" }})."
                )
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val sku = purchase.products.firstOrNull() ?: _uiState.value.selectedProductId
            val token = purchase.purchaseToken

            if (!purchase.isAcknowledged && billingClient.isReady) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(token)
                    .build()
                billingClient.acknowledgePurchase(ackParams) { ackResult ->
                    Log.d("TuesdayBilling", "Acknowledged Play purchase ($sku): ${ackResult.responseCode}")
                }
            }

            repository.upgradeToProTier1(productId = sku, purchaseToken = token)
            _uiState.value = _uiState.value.copy(
                isPurchasing = false,
                statusMessage = "✓ Pro Tier 1 activated via Google Play"
            )
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _uiState.value = _uiState.value.copy(
                isPurchasing = false,
                statusMessage = "⏳ Purchase is pending payment confirmation in Google Play."
            )
        } else {
            _uiState.value = _uiState.value.copy(isPurchasing = false)
        }
    }

    private fun queryActivePurchasesSilent() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (active != null) {
                    handlePurchase(active)
                } else {
                    scope.launch {
                        repository.reconcileWhenPlayHasNoActivePurchase()
                    }
                }
            }
        }
    }

    fun restorePurchases() {
        _uiState.value = _uiState.value.copy(
            isRestoring = true,
            statusMessage = "Checking Google Play & Tuesday.sh entitlement..."
        )
        if (billingClient.isReady) {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                val active = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                } else null

                if (active != null) {
                    handlePurchase(active)
                    _uiState.value = _uiState.value.copy(
                        isRestoring = false,
                        statusMessage = "✓ Restored Pro Tier 1 subscription from Google Play"
                    )
                } else {
                    restoreFromServerFallback()
                }
            }
        } else {
            restoreFromServerFallback()
        }
    }

    private fun restoreFromServerFallback() {
        scope.launch {
            val restoredTier = repository.fetchServerEntitlementStatus()
            val isPro = repository.isProTier1Entitled(restoredTier)
            _uiState.value = _uiState.value.copy(
                isRestoring = false,
                statusMessage = if (isPro) {
                    "✓ Restored Pro Tier 1 entitlement from server"
                } else {
                    "No active Pro Tier 1 subscription found for this device."
                }
            )
        }
    }

    companion object {
        const val SKU_PRO_TIER1_MONTHLY = "sh.tuesday.pro.tier1.monthly"
        const val SKU_PRO_TIER1_ANNUAL = "sh.tuesday.pro.tier1.annual"
        const val ENTITLEMENT_PRO_TIER1 = "pro_tier_1"
        const val ENTITLEMENT_FREE = "free"

        val SUPPORTED_TIER1_SKUS = listOf(
            SKU_PRO_TIER1_ANNUAL,
            SKU_PRO_TIER1_MONTHLY
        )

        fun defaultPlanOffers(): List<SubscriptionPlanOffer> = listOf(
            SubscriptionPlanOffer(
                productId = SKU_PRO_TIER1_ANNUAL,
                title = "Annual",
                formattedPrice = "$49.00",
                periodLabel = "/ year",
                badge = "SAVE 18%"
            ),
            SubscriptionPlanOffer(
                productId = SKU_PRO_TIER1_MONTHLY,
                title = "Monthly",
                formattedPrice = "$4.99",
                periodLabel = "/ month",
                badge = null
            )
        )
    }
}
