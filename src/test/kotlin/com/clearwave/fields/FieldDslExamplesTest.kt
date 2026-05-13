package com.clearwave.fields

import com.clearwave.domain.LineProfile
import com.clearwave.feasibility.FeasibilityRequest
import com.clearwave.feasibility.FeasibilityResponse
import com.clearwave.fields.FeasibilityResponseFields.anAddressPostcode
import com.clearwave.fields.FeasibilityResponseFields.fastestProfileDownloadSpeed
import com.clearwave.fields.FeasibilityResponseFields.fastestProfileSupplier
import com.clearwave.fields.FeasibilityResponseFields.fastestProfileType
import com.clearwave.fields.FeasibilityResponseFields.fastestProfileUploadSpeed
import com.clearwave.fields.FeasibilityResponseFields.profileCount
import com.clearwave.fields.FeasibilityResponseFields.serviceable
import com.clearwave.fields.FibreVisionResponseFields.firstProfileDownloadSpeed
import com.clearwave.fields.FibreVisionResponseFields.firstProfileType
import com.clearwave.fields.FibreVisionResponseFields.profileTypes
import com.clearwave.fields.FibreVisionResponseFields.status
import com.clearwave.stubs.FeasibilityScenario
import com.clearwave.support.ClearwaveTestNgListener.Companion.feasibilityService
import com.clearwave.support.ClearwaveTestNgListener.Companion.fibreVisionStub
import com.clearwave.support.ClearwaveTestNgListener.Companion.httpClient
import com.clearwave.support.ClearwaveTestNgListener.Companion.openNetworkStub
import com.clearwave.support.ClearwaveTest
import com.clearwave.support.TelecomsCapturedOutputs.FeasibilityResult
import com.clearwave.support.TelecomsFixtures.broadbandDownloadSpeed
import com.clearwave.support.TelecomsFixtures.broadbandSupplier
import com.clearwave.support.TelecomsFixtures.broadbandUploadSpeed
import com.clearwave.support.TelecomsFixtures.postcode
import com.clearwave.support.TelecomsFixtures.serviceAddress
import com.clearwave.support.TelecomsFixtures.trackingId
import com.clearwave.support.TelecomsFixtures.voiceDownloadSpeed
import com.clearwave.support.TelecomsFixtures.voiceSupplier
import com.clearwave.support.TelecomsFixtures.voiceUploadSpeed
import com.clearwave.support.TelecomsParty
import com.clearwave.support.TrackingId
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.GivensContext
import dev.kensa.Notes
import dev.kensa.RenderedHintStrategy.HintFromProperty
import dev.kensa.RenderedValueStrategy.UseIdentifierName
import dev.kensa.RenderedValueWithHint
import dev.kensa.Sources
import dev.kensa.StateCollector
import dev.kensa.kotest.testsupport.field.json.JsonField
import dev.kensa.kotest.testsupport.field.json.JsonMapField
import dev.kensa.kotest.testsupport.field.matching
import dev.kensa.kotest.testsupport.field.of
import dev.kensa.kotest.testsupport.field.withListOf
import dev.kensa.kotest.testsupport.field.xml.XmlField
import dev.kensa.kotest.testsupport.field.xml.XmlListField
import dev.kensa.kotest.testsupport.field.xml.XmlSetField
import dev.kensa.render.Language
import dev.kensa.state.CapturedInteractionBuilder.Companion.from
import dev.kensa.util.Attributes
import io.kotest.matchers.and
import io.kotest.matchers.collections.containExactlyInAnyOrder
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.testng.annotations.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

@Notes("""
Showcases the **field DSL** from `kensa-kotest-test-support` — JSON and XML assertions that
read like English at the call-site:

- `anAddressPostcode of fixtures[postcode]`
- `status of "SERVICEABLE"`
- `profileTypes.withListOf("FTTC")`

JSON fields use JSONPointers; XML fields use XPath. Field declarations live in
[FeasibilityResponseFields] and [FibreVisionResponseFields] alongside this test.

Hover any field name in the rendered sentence to see its underlying path —
wired through `@RenderedValueWithHint`.
""")
@RenderedValueWithHint(JsonField::class,    valueStrategy = UseIdentifierName, hintStrategy = HintFromProperty, hintParam = "path")
@RenderedValueWithHint(JsonMapField::class, valueStrategy = UseIdentifierName, hintStrategy = HintFromProperty, hintParam = "path")
@RenderedValueWithHint(XmlField::class,     valueStrategy = UseIdentifierName, hintStrategy = HintFromProperty, hintParam = "path")
@RenderedValueWithHint(XmlListField::class, valueStrategy = UseIdentifierName, hintStrategy = HintFromProperty, hintParam = "path")
@RenderedValueWithHint(XmlSetField::class,  valueStrategy = UseIdentifierName, hintStrategy = HintFromProperty, hintParam = "path")
@Sources(FeasibilityResponseFields::class, FibreVisionResponseFields::class)
class FieldDslExamplesTest : ClearwaveTest() {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `feasibility response shape — asserted via JsonField DSL`() {
        given(bothSuppliersAreServiceable())

        whenever(aFeasibilityCheckIsRequestedForTheServiceAddress())

        then(theFeasibilityResponseAsJson(),
            (anAddressPostcode of fixtures[postcode])
                .and(serviceable of true)
                .and(profileCount of 3)
                .and(fastestProfileType of "FTTP")
                .and(fastestProfileSupplier of fixtures[voiceSupplier])
                .and(fastestProfileDownloadSpeed of fixtures[voiceDownloadSpeed])
                .and(fastestProfileUploadSpeed of fixtures[voiceUploadSpeed])
        )
    }

    @Test
    fun `FibreVision XML response — asserted via XmlField DSL`() {
        given(bothSuppliersAreServiceable())

        whenever(aFeasibilityCheckIsRequestedForTheServiceAddress())

        then(theFibreVisionResponseDocument(),
            (status of "SERVICEABLE")
                .and(firstProfileType of "FTTC")
                .and(firstProfileDownloadSpeed of fixtures[broadbandDownloadSpeed])
                .and(profileTypes matching containExactlyInAnyOrder("FTTC"))
        )
    }

    private fun bothSuppliersAreServiceable() = Action<GivensContext> { (fixtures) ->
        val tid = fixtures[trackingId]
        openNetworkStub.primeFeasibility(tid, FeasibilityScenario.Serviceable(listOf(
            LineProfile("FTTP", fixtures[voiceDownloadSpeed], fixtures[voiceUploadSpeed], "Full Fibre 900", fixtures[voiceSupplier]),
            LineProfile("FTTP", 500, 75, "Full Fibre 500", fixtures[voiceSupplier]),
        )))
        fibreVisionStub.primeFeasibility(tid, FeasibilityScenario.Serviceable(listOf(
            LineProfile("FTTC", fixtures[broadbandDownloadSpeed], fixtures[broadbandUploadSpeed], "Superfast 80", fixtures[broadbandSupplier]),
        )))
    }

    private fun aFeasibilityCheckIsRequestedForTheServiceAddress() = Action<ActionContext> { (fixtures, interactions) ->
        val tid = fixtures[trackingId]
        openNetworkStub.register(tid, interactions)
        fibreVisionStub.register(tid, interactions)

        val requestBody = mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(FeasibilityRequest(fixtures[serviceAddress]))

        val httpRequest = Request(POST, "http://localhost:${feasibilityService.port}/api/feasibility")
            .header(TrackingId.HEADER, tid.toString())
            .header("Content-Type", "application/json")
            .body(requestBody)

        interactions.capture(
            from(TelecomsParty.Customer)
                .to(TelecomsParty.FeasibilityService)
                .with(requestBody, "Feasibility Request")
                .with(Attributes.of("language", Language.Json))
                .underTest(true)
        )

        val response = httpClient(httpRequest)
        outputs[FeasibilityResult] = mapper.readValue<FeasibilityResponse>(response.bodyString())

        interactions.capture(
            from(TelecomsParty.FeasibilityService)
                .to(TelecomsParty.Customer)
                .with(response, "Feasibility Response")
        )
    }

    private fun theFeasibilityResponseAsJson() = StateCollector { mapper.valueToTree<JsonNode>(outputs[FeasibilityResult]) }

    private fun theFibreVisionResponseDocument() = StateCollector<Document> {
        val xml = fibreVisionStub.feasibilityResponseFor(fixtures[trackingId])
            ?: error("FibreVision did not capture a feasibility response for this test")
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }
}
