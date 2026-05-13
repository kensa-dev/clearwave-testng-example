package com.clearwave.feasibility

import com.clearwave.domain.LineProfile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import java.net.ServerSocket

/**
 * Clearwave feasibility service. Fans out feasibility enquiries to two suppliers:
 *
 * - **OpenNetwork** — voice and broadband (JSON API)
 * - **FibreVision** — broadband only (XML API)
 *
 * The `X-Tracking-Id` header is forwarded on all outbound supplier calls so that
 * test stubs can correlate interactions with the correct test invocation.
 */
class FeasibilityService(
    val port: Int = findAvailablePort(),
    private val openNetworkUrl: String,
    private val fibreVisionUrl: String,
    private val httpClient: HttpHandler = JavaHttpClient(),
) : AutoCloseable {

    private val mapper = jacksonObjectMapper()

    private val server = routes(
        "/api/feasibility" bind POST to ::handleFeasibilityCheck
    ).asServer(SunHttp(port))

    fun start() = apply { server.start() }
    override fun close() { server.stop() }

    private fun handleFeasibilityCheck(request: Request): Response {
        val trackingId = request.header("X-Tracking-Id")
            ?: return Response(BAD_REQUEST).body("Missing X-Tracking-Id header")

        val feasibilityRequest = mapper.readValue<FeasibilityRequest>(request.bodyString())

        val openNetworkProfiles = queryOpenNetwork(trackingId, feasibilityRequest)
        val fibreVisionProfiles = queryFibreVision(trackingId, feasibilityRequest)
        val allProfiles = (openNetworkProfiles + fibreVisionProfiles)
            .sortedByDescending { it.downloadSpeed }

        val response = FeasibilityResponse(
            address = feasibilityRequest.address,
            serviceable = allProfiles.isNotEmpty(),
            profiles = allProfiles,
        )

        return Response(OK)
            .header("Content-Type", "application/json")
            .body(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response))
    }

    private fun queryOpenNetwork(trackingId: String, req: FeasibilityRequest): List<LineProfile> {
        val body = mapper.writeValueAsString(
            OpenNetworkFeasibilityRequest(req.address.postcode, req.services)
        )
        val response = httpClient(
            Request(POST, "$openNetworkUrl/api/feasibility/check")
                .header("X-Tracking-Id", trackingId)
                .header("Content-Type", "application/json")
                .body(body)
        )
        if (!response.status.successful) return emptyList()
        return mapper.readValue<OpenNetworkFeasibilityResponse>(response.bodyString())
            .profiles.map { it.toLineProfile() }
    }

    private fun queryFibreVision(trackingId: String, req: FeasibilityRequest): List<LineProfile> {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<FeasibilityEnquiry>
    <Postcode>${req.address.postcode}</Postcode>
    <ServiceType>BROADBAND</ServiceType>
</FeasibilityEnquiry>"""
        val response = httpClient(
            Request(POST, "$fibreVisionUrl/api/feasibility/enquiry")
                .header("X-Tracking-Id", trackingId)
                .header("Content-Type", "application/xml")
                .body(xml)
        )
        if (!response.status.successful) return emptyList()
        return parseFibreVisionResponse(response.bodyString())
    }

    private fun parseFibreVisionResponse(xml: String): List<LineProfile> {
        val status = xml.extractXml("Status") ?: return emptyList()
        if (status != "SERVICEABLE") return emptyList()
        return Regex("<Profile>(.*?)</Profile>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { match ->
                val p = match.groupValues[1]
                LineProfile(
                    type         = p.extractXml("Type") ?: "UNKNOWN",
                    downloadSpeed = p.extractXml("DownloadSpeed")?.toIntOrNull() ?: 0,
                    uploadSpeed   = p.extractXml("UploadSpeed")?.toIntOrNull() ?: 0,
                    description  = p.extractXml("Description") ?: "",
                    supplier     = "FibreVision",
                )
            }.toList()
    }

    private fun String.extractXml(tag: String): String? =
        Regex("<$tag>(.*?)</$tag>").find(this)?.groupValues?.getOrNull(1)

    companion object {
        fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
