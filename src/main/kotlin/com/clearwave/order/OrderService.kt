package com.clearwave.order

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Clearwave order service. Places telecom service orders with suppliers and
 * receives asynchronous status notifications via a callback endpoint.
 *
 * - Orders including a voice profile are placed with **OpenNetwork** (JSON).
 * - Orders including a broadband profile are placed with **FibreVision** (XML).
 *
 * Suppliers call back `POST /api/notifications/{trackingId}` (JSON or XML)
 * as the order progresses through its lifecycle.
 */
class OrderService(
    val port: Int = findAvailablePort(),
    private val openNetworkUrl: String,
    private val fibreVisionUrl: String,
    private val httpClient: HttpHandler = JavaHttpClient(),
) : AutoCloseable {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val notifications = ConcurrentHashMap<String, MutableList<SupplierNotification>>()

    private val server = routes(
        "/api/orders"                           bind POST to ::handlePlaceOrder,
        "/api/notifications/{trackingId}"       bind POST to ::handleNotification,
    ).asServer(SunHttp(port))

    fun start() = apply { server.start() }
    override fun close() { server.stop() }

    /** Retrieve all notifications received for a given tracking ID. */
    fun notificationsFor(trackingId: String): List<SupplierNotification> =
        notifications.getOrDefault(trackingId, emptyList()).toList()

    private fun handlePlaceOrder(request: Request): Response {
        val trackingId = request.header("X-Tracking-Id")
            ?: return Response(BAD_REQUEST).body("Missing X-Tracking-Id header")

        val orderRequest = mapper.readValue<OrderRequest>(request.bodyString())
        val orderId = "CW-${UUID.randomUUID().toString().take(8).uppercase()}"
        notifications[trackingId] = mutableListOf()

        val callbackUrl = "http://localhost:$port/api/notifications/$trackingId"

        var openNetworkRef: String? = null
        var fibreVisionRef: String? = null

        if (orderRequest.voiceProfile != null) {
            openNetworkRef = placeOpenNetworkOrder(trackingId, orderRequest, callbackUrl)
        }
        if (orderRequest.broadbandProfile != null) {
            fibreVisionRef = placeFibreVisionOrder(trackingId, orderRequest, callbackUrl)
        }

        val response = OrderResponse(
            orderId = orderId,
            status = "PENDING",
            openNetworkRef = openNetworkRef,
            fibreVisionRef = fibreVisionRef,
        )

        return Response(ACCEPTED)
            .header("Content-Type", "application/json")
            .body(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response))
    }

    private fun placeOpenNetworkOrder(trackingId: String, req: OrderRequest, callbackUrl: String): String? {
        val profile = req.voiceProfile ?: return null
        val body = mapper.writeValueAsString(
            OpenNetworkOrderRequest(
                postcode = req.address.postcode,
                profileType = profile.type,
                downloadSpeed = profile.downloadSpeed,
                appointmentDate = req.appointmentSlot?.date?.toString(),
                appointmentSlot = req.appointmentSlot?.timeSlot,
                notificationCallbackUrl = callbackUrl,
            )
        )
        val response = httpClient(
            Request(POST, "$openNetworkUrl/api/orders")
                .header("X-Tracking-Id", trackingId)
                .header("Content-Type", "application/json")
                .body(body)
        )
        if (!response.status.successful) return null
        return mapper.readValue<OpenNetworkOrderResponse>(response.bodyString()).orderRef
    }

    private fun placeFibreVisionOrder(trackingId: String, req: OrderRequest, callbackUrl: String): String? {
        val profile = req.broadbandProfile ?: return null
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<ServiceOrder>
    <Postcode>${req.address.postcode}</Postcode>
    <AddressLine1>${req.address.addressLine1}</AddressLine1>
    <Town>${req.address.town}</Town>
    <County>${req.address.county}</County>
    <ProfileType>${profile.type}</ProfileType>
    <DownloadSpeed>${profile.downloadSpeed}</DownloadSpeed>
    <AppointmentDate>${req.appointmentSlot?.date ?: ""}</AppointmentDate>
    <AppointmentSlot>${req.appointmentSlot?.timeSlot ?: ""}</AppointmentSlot>
    <NotificationCallbackUrl>$callbackUrl</NotificationCallbackUrl>
</ServiceOrder>"""
        val response = httpClient(
            Request(POST, "$fibreVisionUrl/api/orders")
                .header("X-Tracking-Id", trackingId)
                .header("Content-Type", "application/xml")
                .body(xml)
        )
        if (!response.status.successful) return null
        return response.bodyString().extractXml("OrderRef")
    }

    private fun handleNotification(request: Request): Response {
        val trackingId = request.path("trackingId")
            ?: return Response(BAD_REQUEST).body("Missing trackingId path parameter")

        val contentType = request.header("Content-Type") ?: ""
        val notification = if (contentType.contains("xml")) {
            parseFibreVisionNotification(request.bodyString())
        } else {
            parseOpenNetworkNotification(request.bodyString())
        } ?: return Response(BAD_REQUEST).body("Could not parse notification")

        notifications.getOrPut(trackingId) { mutableListOf() }.add(notification)

        return Response(OK)
    }

    private fun parseOpenNetworkNotification(body: String): SupplierNotification? {
        return try {
            val n = mapper.readValue<OpenNetworkNotification>(body)
            SupplierNotification(
                supplierRef = n.orderRef,
                supplier = "OpenNetwork",
                status = NotificationStatus.valueOf(n.status),
                message = n.message,
            )
        } catch (_: Exception) { null }
    }

    private fun parseFibreVisionNotification(xml: String): SupplierNotification? {
        return try {
            SupplierNotification(
                supplierRef = xml.extractXml("OrderRef") ?: return null,
                supplier = "FibreVision",
                status = NotificationStatus.valueOf(xml.extractXml("Status") ?: return null),
                message = xml.extractXml("Message"),
            )
        } catch (_: Exception) { null }
    }

    private fun String.extractXml(tag: String): String? =
        Regex("<$tag>(.*?)</$tag>").find(this)?.groupValues?.getOrNull(1)

    companion object {
        fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
