package com.clearwave.stubs

import com.clearwave.domain.LineProfile

/**
 * Primed scenario for feasibility stub responses.
 */
sealed class FeasibilityScenario {

    /** Address is serviceable — stub returns the given profiles. */
    data class Serviceable(val profiles: List<LineProfile>) : FeasibilityScenario()

    /** Address is not serviceable — stub returns an empty profile list with a reason. */
    data object NotServiceable : FeasibilityScenario()

    /** Supplier is unavailable — stub returns a 5xx error. */
    data object SupplierError : FeasibilityScenario()
}
