package com.legumlex.client.feature.board

import java.io.File

/** A credits order sent to the pay page and not yet settled: the order id, the pack bought, and when it was started (epoch millis). */
data class PendingOrder(val order: String, val pack: String, val startedAt: Long)

/**
 * The pending order kept on the device so it survives the app being closed while the pay page is open. It lives in
 * cache/board, which sign-out and an account switch wipe with the other board files, so it is never another person's.
 * One order at a time; a new purchase replaces it.
 */
class PendingOrderStore(cacheRoot: File?) {
    private val file: File? = cacheRoot?.let { File(File(it, "board"), "pending_order.txt") }

    fun get(): PendingOrder? = runCatching {
        val lines = file?.takeIf { it.isFile }?.readLines() ?: return@runCatching null
        val order = lines.getOrNull(0)?.trim().orEmpty()
        if (order.isBlank()) null else PendingOrder(order, lines.getOrNull(1).orEmpty().trim(), lines.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L)
    }.getOrNull()

    fun put(p: PendingOrder) {
        runCatching {
            val f = file ?: return
            f.parentFile?.mkdirs()
            f.writeText("${p.order.lines().first()}\n${p.pack.lines().firstOrNull().orEmpty()}\n${p.startedAt}\n")
        }
    }

    fun clear() { runCatching { file?.delete() } }
}

/** What the server's order status means for the pending order. */
sealed interface OrderVerdict {
    /** Paid: the credits are in; the order is done. */
    data object Paid : OrderVerdict

    /** Expired or cancelled: nothing was charged; the order is done. */
    data object Failed : OrderVerdict

    /**
     * Failed: the pay page ended without a payment, but the order stays payable and a later capture is honoured (the server
     * says so), so it is not "nothing was charged". The order is kept and asked about again for a while.
     */
    data object NotCompleted : OrderVerdict

    /** Flagged: the payment arrived but was not recorded automatically; the server says it will record or refund it. The order is done here. */
    data object Flagged : OrderVerdict

    /** Pending, or no answer: keep the order and ask again on the next resume or Check Again. */
    data object Pending : OrderVerdict
}

/** The server's word on an order ([status] null when it could not be asked). Anything it has not settled stays pending. */
fun orderVerdict(status: String?): OrderVerdict = when (status) {
    "paid" -> OrderVerdict.Paid
    "expired", "cancelled" -> OrderVerdict.Failed
    "failed" -> OrderVerdict.NotCompleted
    "flagged" -> OrderVerdict.Flagged
    else -> OrderVerdict.Pending
}

/** How long a "failed" order is kept and asked about (a late capture is still honoured); after that it is let go. */
const val FAILED_KEEP_MILLIS = 3L * 60 * 60 * 1000

/** Whether a not-completed order has been kept long enough ([startedAt] epoch millis). */
fun failedOrderGivenUp(startedAt: Long, now: Long): Boolean = startedAt <= 0L || now - startedAt > FAILED_KEEP_MILLIS

/** An order the server does not know (404): it can never settle, so it is let go; any other failure to ask leaves it pending. */
fun orderGone(http: Int?): Boolean = http == 404

const val PENDING_NOTE = "Payment not confirmed yet. If you did pay, your credits arrive shortly; otherwise nothing was charged."
const val FAILED_NOTE = "Payment not completed. If you did pay, it will be matched and your credits added."
const val EXPIRED_NOTE = "That payment was not made and nothing was charged. You can try again."
const val FLAGGED_NOTE = "Your payment was received but could not be recorded automatically. We will record or refund it shortly."
const val NO_BROWSER_NOTE = "The payment page could not be opened on this phone. Nothing was charged."
