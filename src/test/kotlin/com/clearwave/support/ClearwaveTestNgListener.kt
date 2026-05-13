package com.clearwave.support

import com.clearwave.feasibility.FeasibilityService
import com.clearwave.order.OrderService
import com.clearwave.stubs.FibreVisionStub
import com.clearwave.stubs.OpenNetworkStub
import dev.kensa.Kensa.konfigure
import dev.kensa.PackageDisplay
import dev.kensa.Tab
import dev.kensa.fixture.FixtureRegistry.registerFixtures
import dev.kensa.outputs.CapturedOutputsRegistry.registerCapturedOutputs
import dev.kensa.sentence.Acronym.Companion.of
import dev.kensa.withRenderers
import org.http4k.client.OkHttp
import org.testng.ISuite
import org.testng.ISuiteListener

class ClearwaveTestNgListener : ISuiteListener {

    override fun onStart(suite: ISuite) {
        registerFixtures(TelecomsFixtures)
        registerFixtures(JavaTelecomsFixtures::class.java)
        registerCapturedOutputs(TelecomsCapturedOutputs)
        konfigure {
            packageDisplay     = PackageDisplay.HideCommonPackages
            packageDisplayRoot = "com.clearwave"
            autoOpenTab        = Tab.SequenceDiagram
            acronyms(
                of("FTTP", "Fibre to the Premises"),
                of("FTTC", "Fibre to the Cabinet"),
                of("CLI",  "Calling Line Identity"),
                of("SLA",  "Service Level Agreement"),
                of("PSTN", "Public Switched Telephone Network"),
            )
            withRenderers {
                interactionRenderer(RequestRenderer)
                interactionRenderer(ResponseRenderer)
            }
        }
        openNetworkStub.start()
        fibreVisionStub.start()
        feasibilityService.start()
        orderService.start()
    }

    override fun onFinish(suite: ISuite) {
        orderService.close()
        feasibilityService.close()
        fibreVisionStub.close()
        openNetworkStub.close()
    }

    companion object {
        // Ports reserved upfront so services can be wired together at construction time
        private val openNetworkPort  = FeasibilityService.findAvailablePort()
        private val fibreVisionPort  = FeasibilityService.findAvailablePort()
        private val feasibilityPort  = FeasibilityService.findAvailablePort()
        private val orderServicePort = OrderService.findAvailablePort()

        @JvmField val openNetworkStub = OpenNetworkStub(openNetworkPort)
        @JvmField val fibreVisionStub = FibreVisionStub(fibreVisionPort)

        @JvmField val feasibilityService = FeasibilityService(
            port           = feasibilityPort,
            openNetworkUrl = "http://localhost:$openNetworkPort",
            fibreVisionUrl = "http://localhost:$fibreVisionPort",
        )

        @JvmField val orderService = OrderService(
            port           = orderServicePort,
            openNetworkUrl = "http://localhost:$openNetworkPort",
            fibreVisionUrl = "http://localhost:$fibreVisionPort",
        )

        @JvmField val httpClient = OkHttp()

        /** Java-friendly factory: avoids `new Request(...)` on an abstract http4k type. */
        @JvmStatic fun request(method: org.http4k.core.Method, url: String): org.http4k.core.Request =
            org.http4k.core.Request(method, url)
    }
}
