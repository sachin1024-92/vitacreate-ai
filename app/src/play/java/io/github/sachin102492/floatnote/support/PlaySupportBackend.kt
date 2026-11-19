package io.github.sachin102492.floatnote.support

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.sachin102492.floatnote.R

/**
 * One-time "supporter" tips sold through Google Play Billing. Any owned tip makes the user a
 * supporter. Tips are non-consumable so supporter status can be restored on a new device.
 */
class PlaySupportBackend(context: Context) : SupportBackend, PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var listener: SupportBackend.Listener? = null
    private var details: Map<String, ProductDetails> = emptyMap()

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    override val donateUrl: String? = null

    override fun start(listener: SupportBackend.Listener) {
        this.listener = listener
        listener.onSupporterChanged(Perks.isSupporter(appContext))
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryProducts()
                    refreshPurchases()
                } else {
                    Log.w(TAG, "Billing unavailable: ${result.debugMessage}")
                    post { it.onMessage(R.string.support_unavailable) }
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun buy(activity: Activity, tierId: String) {
        val product = details[tierId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun end() {
        listener = null
        client.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                if (list.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                    post { it.onMessage(R.string.support_pending) }
                }
                if (list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    handleOwned(list)
                    post { it.onMessage(R.string.support_thanks) }
                }
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            BillingResponseCode.USER_CANCELED -> Unit
            else -> post { it.onMessage(R.string.support_failed) }
        }
    }

    private fun queryProducts() {
        val products = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, productResult ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = productResult.productDetailsList.associateBy { it.productId }
            main.post {
                details = found
                val tiers = PRODUCT_IDS.mapNotNull { id ->
                    found[id]?.let { product ->
                        SupportTier(
                            id = id,
                            title = product.name,
                            price = product.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty(),
                        )
                    }
                }
                listener?.onTiers(tiers)
            }
        }
    }

    /** Restores supporter status, and revokes it if every tip was refunded. */
    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) handleOwned(purchases)
        }
    }

    private fun handleOwned(purchases: List<Purchase>) {
        val owned = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                it.products.any { id -> id in PRODUCT_IDS }
        }
        // Unacknowledged purchases are refunded automatically after three days.
        for (purchase in owned.filterNot { it.isAcknowledged }) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { ack ->
                if (ack.responseCode != BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${ack.debugMessage}")
                }
            }
        }
        main.post {
            Perks.setSupporter(appContext, owned.isNotEmpty())
            listener?.onSupporterChanged(owned.isNotEmpty())
        }
    }

    private fun post(action: (SupportBackend.Listener) -> Unit) {
        main.post { listener?.let(action) }
    }

    private companion object {
        const val TAG = "PlaySupport"

        /** One-time in-app products to create in Play Console (Monetize → In-app products). */
        val PRODUCT_IDS = listOf("supporter_coffee", "supporter_lunch", "supporter_hero")
    }
}
