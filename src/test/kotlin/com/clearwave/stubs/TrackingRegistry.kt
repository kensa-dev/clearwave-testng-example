package com.clearwave.stubs

import com.clearwave.support.TrackingId
import dev.kensa.state.CapturedInteractions
import dev.kensa.state.SetupStrategy
import org.http4k.core.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes [CapturedInteractions] to the correct test invocation by tracking ID.
 *
 * Each stub holds one registry. Before each `whenever` action the test registers its
 * [TrackingId] → [CapturedInteractions] mapping. When the stub's HTTP handler receives
 * a request it extracts the `X-Tracking-Id` header and looks up the matching interactions
 * — enabling safe parallel test execution.
 */
class TrackingRegistry {

    private val map = ConcurrentHashMap<String, CapturedInteractions>()

    fun register(trackingId: TrackingId, interactions: CapturedInteractions) {
        map[trackingId.value.toString()] = interactions
    }

    fun unregister(trackingId: TrackingId) {
        map.remove(trackingId.value.toString())
    }

    fun forRequest(request: Request): CapturedInteractions {
        val id = request.header(TrackingId.HEADER)
            ?: error("Stub received request without ${TrackingId.HEADER} header")
        return map[id] ?: CapturedInteractions(SetupStrategy.Ignored)
    }
}
