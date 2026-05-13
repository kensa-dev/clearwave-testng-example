package com.clearwave.stubs

import com.clearwave.order.NotificationStatus

/**
 * Primed scenario for order stub responses and async notification sequences.
 */
sealed class OrderScenario {

    /**
     * Order is accepted. The stub returns PENDING synchronously then fires [notifications]
     * asynchronously to the callback URL included in the order request.
     */
    data class Accepted(
        val notifications: List<NotificationStatus>,
        val notificationDelayMs: Long = 50L,
    ) : OrderScenario()

    /** Order is rejected. The stub returns PENDING synchronously then fires a single REJECTED notification. */
    data object Rejected : OrderScenario()

    companion object {
        /** Standard happy path: Acknowledged → Committed → Completed. */
        val completed = Accepted(
            listOf(
                NotificationStatus.ACKNOWLEDGED,
                NotificationStatus.COMMITTED,
                NotificationStatus.COMPLETED,
            )
        )

        /** Delayed path: Acknowledged → Committed → Delayed → Completed. */
        val delayed = Accepted(
            listOf(
                NotificationStatus.ACKNOWLEDGED,
                NotificationStatus.COMMITTED,
                NotificationStatus.DELAYED,
                NotificationStatus.COMPLETED,
            )
        )
    }
}
