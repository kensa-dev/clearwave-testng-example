package com.clearwave

import com.clearwave.order.NotificationStatus
import com.clearwave.order.NotificationStatus.*
import com.clearwave.order.OrderRequest
import com.clearwave.order.OrderResponse
import com.clearwave.order.SupplierNotification
import com.clearwave.stubs.OrderScenario
import com.clearwave.support.ClearwaveTestNgListener.Companion.fibreVisionStub
import com.clearwave.support.ClearwaveTestNgListener.Companion.httpClient
import com.clearwave.support.ClearwaveTestNgListener.Companion.openNetworkStub
import com.clearwave.support.ClearwaveTestNgListener.Companion.orderService
import com.clearwave.support.ClearwaveTest
import com.clearwave.support.TelecomsCapturedOutputs.OrderConfirmation
import com.clearwave.support.TelecomsFixtures.appointmentSlot
import com.clearwave.support.TelecomsFixtures.broadbandProfile
import com.clearwave.support.TelecomsFixtures.broadbandSupplier
import com.clearwave.support.TelecomsFixtures.customerId
import com.clearwave.support.TelecomsFixtures.serviceAddress
import com.clearwave.support.TelecomsFixtures.trackingId
import com.clearwave.support.TelecomsFixtures.voiceProfile
import com.clearwave.support.TelecomsFixtures.voiceSupplier
import com.clearwave.support.TelecomsParty
import com.clearwave.support.TrackingId
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kensa.*
import dev.kensa.render.Language
import dev.kensa.state.CapturedInteractionBuilder.Companion.from
import dev.kensa.util.Attributes
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.testng.annotations.Test
import kotlin.time.Duration.Companion.seconds

@Notes("""
**Order Service** orchestrates provisioning across two suppliers for a combined voice + broadband package.

Notification lifecycle (per supplier):

| Status | Meaning |
|--------|---------|
| `ACKNOWLEDGED` | Supplier has received and accepted the order |
| `COMMITTED` | Resources reserved; engineer visit booked |
| `DELAYED` | Engineer visit rescheduled |
| `COMPLETED` | Service is live |
| `REJECTED` | Order cannot be fulfilled |

See [FeasibilityServiceTest](#FeasibilityServiceTest) for feasibility pre-checks.
""")
class OrderServiceTest : ClearwaveTest() {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `voice and broadband order is successfully completed`() {
        given(openNetworkWillCompleteTheOrder())
        and(fibreVisionWillCompleteTheOrder())

        whenever(aVoiceAndBroadbandOrderIsPlaced())

        then(theOrderConfirmation(), shouldBePending())
        thenEventuallyAllNotifications(
            shouldShowBothSuppliersCompletedSuccessfully(
                voiceSupplier = fixtures[voiceSupplier],
                broadbandSupplier = fixtures[broadbandSupplier],
            )
        )
    }

    @Test
    fun `order is delayed then completed`() {
        given(openNetworkWillCompleteTheOrder())
        and(fibreVisionWillDelayThenCompleteTheOrder())

        whenever(aVoiceAndBroadbandOrderIsPlaced())

        then(theOrderConfirmation(), shouldBePending())
        thenEventuallyFibreVisionNotifications(shouldShowLifecycle(
            supplier = fixtures[broadbandSupplier],
            lifecycle = theDelayedLifecycle(),
        ))
    }

    @Test
    fun `order is rejected by fibre vision`() {
        given(openNetworkWillCompleteTheOrder())
        and(fibreVisionWillRejectTheOrder())

        whenever(aVoiceAndBroadbandOrderIsPlaced())

        then(theOrderConfirmation(), shouldBePending())
        thenEventuallyFibreVisionNotifications(shouldBeRejected(
            supplier = fixtures[broadbandSupplier],
        ))
    }

    // --- Givens ---

    private fun openNetworkWillCompleteTheOrder() = Action<GivensContext> { (fixtures) ->
        openNetworkStub.primeOrder(fixtures[trackingId], OrderScenario.completed)
    }

    private fun fibreVisionWillCompleteTheOrder() = Action<GivensContext> { (fixtures) ->
        fibreVisionStub.primeOrder(fixtures[trackingId], OrderScenario.completed)
    }

    private fun fibreVisionWillDelayThenCompleteTheOrder() = Action<GivensContext> { (fixtures) ->
        fibreVisionStub.primeOrder(fixtures[trackingId], OrderScenario.delayed)
    }

    private fun fibreVisionWillRejectTheOrder() = Action<GivensContext> { (fixtures) ->
        fibreVisionStub.primeOrder(fixtures[trackingId], OrderScenario.Rejected)
    }

    // --- Action ---

    private fun aVoiceAndBroadbandOrderIsPlaced() = Action<ActionContext> { (fixtures, interactions) ->
        val tid = fixtures[trackingId]
        openNetworkStub.register(tid, interactions)
        fibreVisionStub.register(tid, interactions)

        val orderRequest = OrderRequest(
            customerId              = fixtures[customerId],
            address                 = fixtures[serviceAddress],
            voiceProfile            = fixtures[voiceProfile],
            broadbandProfile        = fixtures[broadbandProfile],
            appointmentSlot         = fixtures[appointmentSlot],
            notificationCallbackUrl = "http://localhost:${orderService.port}/api/notifications/$tid",
        )

        val requestBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(orderRequest)
        val httpRequest = Request(POST, "http://localhost:${orderService.port}/api/orders")
            .header(TrackingId.HEADER, tid.toString())
            .header("Content-Type", "application/json")
            .body(requestBody)

        interactions.capture(
            from(TelecomsParty.Customer)
                .to(TelecomsParty.OrderService)
                .with(requestBody, "Place Order Request")
                .with(Attributes.of("language", Language.Json))
                .underTest(true)
        )

        val response = httpClient(httpRequest)
        outputs[OrderConfirmation] = mapper.readValue<OrderResponse>(response.bodyString())

        interactions.capture(
            from(TelecomsParty.OrderService)
                .to(TelecomsParty.Customer)
                .with(response, "Order Confirmation — PENDING")
        )

        interactions.captureTimePassing("Awaiting supplier notifications")
    }

    // --- State Collectors and async helpers ---

    private fun theOrderConfirmation() = StateCollector { outputs[OrderConfirmation] }

    private fun allNotifications() = StateCollector {
        orderService.notificationsFor(fixtures[trackingId].toString())
    }

    private fun fibreVisionNotifications() = StateCollector {
        orderService.notificationsFor(fixtures[trackingId].toString())
            .filter { it.supplier == "FibreVision" }
    }

    private fun thenEventuallyAllNotifications(matcher: Matcher<List<SupplierNotification>>) =
        thenEventually(10.seconds, allNotifications(), matcher)

    private fun thenEventuallyFibreVisionNotifications(matcher: Matcher<List<SupplierNotification>>) =
        thenEventually(10.seconds, fibreVisionNotifications(), matcher)

    // --- Assertions ---

    private fun shouldBePending() = Matcher<OrderResponse> { result ->
        MatcherResult(
            result.status == "PENDING",
            { "Expected order status PENDING but was ${result.status}" },
            { "Expected order status not to be PENDING" }
        )
    }

    private fun shouldShowBothSuppliersCompletedSuccessfully(
        voiceSupplier: String,
        broadbandSupplier: String,
    ) = Matcher<List<SupplierNotification>> { notifications ->
        val completedLifecycle = listOf(ACKNOWLEDGED, COMMITTED, COMPLETED)
        val voiceStatuses = notifications.filter { it.supplier == voiceSupplier }.map { it.status }
        val broadbandStatuses = notifications.filter { it.supplier == broadbandSupplier }.map { it.status }
        MatcherResult(
            notifications.size == 6 && voiceStatuses == completedLifecycle && broadbandStatuses == completedLifecycle,
            { "Expected both suppliers to complete: ${voiceSupplier}=$voiceStatuses, ${broadbandSupplier}=$broadbandStatuses" },
            { "Expected not both suppliers to complete" }
        )
    }

    @ExpandableRenderedValue
    private fun theDelayedLifecycle(): List<NotificationStatus> =
        listOf(ACKNOWLEDGED, COMMITTED, DELAYED, COMPLETED)

    private fun shouldShowLifecycle(
        supplier: String,
        lifecycle: List<NotificationStatus>,
    ) = Matcher<List<SupplierNotification>> { notifications ->
        val actual = notifications.map { it.status }
        MatcherResult(
            actual == lifecycle,
            { "Expected $supplier to show ${lifecycle.joinToString(" → ")} but got $actual" },
            { "Expected not to show $lifecycle lifecycle" }
        )
    }

    private fun shouldBeRejected(supplier: String) = Matcher<List<SupplierNotification>> { notifications ->
        MatcherResult(
            notifications.size == 1 && notifications.single().status == REJECTED,
            { "Expected a single REJECTED notification from ${supplier} but got $notifications" },
            { "Expected not to be rejected" }
        )
    }
}
