package com.clearwave.stubs

import com.clearwave.support.TelecomsParty
import com.clearwave.support.TrackingId
import dev.kensa.state.CapturedInteractionBuilder.Companion.from
import dev.kensa.state.CapturedInteractions
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Test stub for the FibreVision supplier (XML API).
 *
 * Follows the same priming / registration / state-inspection contract as [OpenNetworkStub],
 * but speaks XML for all interactions — demonstrating Kensa's `Language.Xml` rendering.
 */
class FibreVisionStub(val port: Int = findAvailablePort()) : AutoCloseable {

    private val registry = TrackingRegistry()
    private val httpClient = JavaHttpClient()
    private val orderCounter = AtomicInteger(0)

    private val feasibilityScenarios = ConcurrentHashMap<String, FeasibilityScenario>()
    private val feasibilityRequests  = ConcurrentHashMap<String, String>()
    private val feasibilityResponses = ConcurrentHashMap<String, String>()

    private val orderScenarios  = ConcurrentHashMap<String, OrderScenario>()
    private val orderRequests   = ConcurrentHashMap<String, String>()
    private val orderResponses  = ConcurrentHashMap<String, String>()

    private val server = routes(
        "/api/feasibility/enquiry" bind POST to ::handleFeasibility,
        "/api/orders"              bind POST to ::handleOrder,
    ).asServer(SunHttp(port))

    fun start()          = apply { server.start() }
    override fun close() { server.stop() }

    // --- Registration ---

    fun register(trackingId: TrackingId, interactions: CapturedInteractions) =
        registry.register(trackingId, interactions)

    fun unregister(trackingId: TrackingId) = registry.unregister(trackingId)

    // --- Priming ---

    fun primeFeasibility(trackingId: TrackingId, scenario: FeasibilityScenario) {
        feasibilityScenarios[trackingId.toString()] = scenario
    }

    fun primeOrder(trackingId: TrackingId, scenario: OrderScenario) {
        orderScenarios[trackingId.toString()] = scenario
    }

    // --- State inspection ---

    fun feasibilityRequestFor(trackingId: TrackingId): String? = feasibilityRequests[trackingId.toString()]
    fun feasibilityResponseFor(trackingId: TrackingId): String? = feasibilityResponses[trackingId.toString()]
    fun orderRequestFor(trackingId: TrackingId): String? = orderRequests[trackingId.toString()]
    fun orderResponseFor(trackingId: TrackingId): String? = orderResponses[trackingId.toString()]

    // --- Handlers ---

    private fun handleFeasibility(request: Request): Response {
        val tid = request.header(TrackingId.HEADER) ?: return Response(INTERNAL_SERVER_ERROR)
        val interactions = registry.forRequest(request)
        val requestBody = request.bodyString()

        feasibilityRequests[tid] = requestBody
        interactions.capture(
            from(TelecomsParty.FeasibilityService)
                .to(TelecomsParty.FibreVision)
                .with(request, "Feasibility Enquiry Request")
        )

        val scenario = feasibilityScenarios[tid] ?: return Response(INTERNAL_SERVER_ERROR)
        val (response, responseBody) = buildFeasibilityResponse(scenario)

        feasibilityResponses[tid] = responseBody
        interactions.capture(
            from(TelecomsParty.FibreVision)
                .to(TelecomsParty.FeasibilityService)
                .with(response, "Feasibility Enquiry Response")
        )

        return response
    }

    private fun buildFeasibilityResponse(scenario: FeasibilityScenario): Pair<Response, String> =
        when (scenario) {
            is FeasibilityScenario.Serviceable -> {
                val profilesXml = scenario.profiles.joinToString("\n") { p ->
                    """    <Profile>
        <Type>${p.type}</Type>
        <DownloadSpeed>${p.downloadSpeed}</DownloadSpeed>
        <UploadSpeed>${p.uploadSpeed}</UploadSpeed>
        <Description>${p.description}</Description>
    </Profile>"""
                }
                val body = """<?xml version="1.0" encoding="UTF-8"?>
<FeasibilityResponse>
    <Status>SERVICEABLE</Status>
    <Profiles>
$profilesXml
    </Profiles>
</FeasibilityResponse>"""
                Response(OK).header("Content-Type", "application/xml").body(body) to body
            }
            FeasibilityScenario.NotServiceable -> {
                val body = """<?xml version="1.0" encoding="UTF-8"?>
<FeasibilityResponse>
    <Status>NOT_SERVICEABLE</Status>
    <Reason>No infrastructure available at this postcode</Reason>
</FeasibilityResponse>"""
                Response(OK).header("Content-Type", "application/xml").body(body) to body
            }
            FeasibilityScenario.SupplierError -> {
                val body = """<?xml version="1.0" encoding="UTF-8"?>
<Error><Message>FibreVision system unavailable</Message></Error>"""
                Response(INTERNAL_SERVER_ERROR).header("Content-Type", "application/xml").body(body) to body
            }
        }

    private fun handleOrder(request: Request): Response {
        val tid = request.header(TrackingId.HEADER) ?: return Response(INTERNAL_SERVER_ERROR)
        val interactions = registry.forRequest(request)
        val requestBody = request.bodyString()

        orderRequests[tid] = requestBody
        interactions.capture(
            from(TelecomsParty.OrderService)
                .to(TelecomsParty.FibreVision)
                .with(request, "Place Order Request")
        )

        val scenario = orderScenarios[tid] ?: return Response(INTERNAL_SERVER_ERROR)
        val orderRef = "FV-${orderCounter.incrementAndGet().toString().padStart(5, '0')}"

        val responseBody = """<?xml version="1.0" encoding="UTF-8"?>
<OrderResponse>
    <OrderRef>$orderRef</OrderRef>
    <Status>PENDING</Status>
</OrderResponse>"""
        val response = Response(OK).header("Content-Type", "application/xml").body(responseBody)
        orderResponses[tid] = responseBody
        interactions.capture(
            from(TelecomsParty.FibreVision)
                .to(TelecomsParty.OrderService)
                .with(response, "Order Accepted — Pending")
        )

        val callbackUrl = requestBody.extractXml("NotificationCallbackUrl")
        if (callbackUrl != null) {
            fireNotificationsAsync(orderRef, tid, callbackUrl, scenario, interactions)
        }

        return response
    }

    private fun fireNotificationsAsync(
        orderRef: String,
        tid: String,
        callbackUrl: String,
        scenario: OrderScenario,
        interactions: CapturedInteractions,
    ) {
        val (statuses, delayMs) = when (scenario) {
            is OrderScenario.Accepted -> scenario.notifications.map { it.name } to scenario.notificationDelayMs
            OrderScenario.Rejected    -> listOf("REJECTED") to 50L
        }

        thread(isDaemon = true, name = "fv-notify-$orderRef") {
            for (status in statuses) {
                Thread.sleep(delayMs)
                val body = """<?xml version="1.0" encoding="UTF-8"?>
<OrderNotification>
    <OrderRef>$orderRef</OrderRef>
    <Status>$status</Status>
</OrderNotification>"""
                val notification = Request(POST, callbackUrl)
                    .header(TrackingId.HEADER, tid)
                    .header("Content-Type", "application/xml")
                    .body(body)
                interactions.capture(
                    from(TelecomsParty.FibreVision)
                        .to(TelecomsParty.OrderService)
                        .with(notification, "Order Notification — $status")
                )
                try {
                    httpClient(notification)
                } catch (_: Exception) { /* test torn down */ }
            }
        }
    }

    private fun String.extractXml(tag: String): String? =
        Regex("<$tag>(.*?)</$tag>").find(this)?.groupValues?.getOrNull(1)

    companion object {
        fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
