package com.clearwave.support;

import com.clearwave.feasibility.FeasibilityResponse;
import com.clearwave.order.OrderResponse;
import dev.kensa.outputs.CapturedOutput;

/**
 * Java-friendly aliases for {@link TelecomsCapturedOutputs}.
 * Import statically to access output keys with idiomatic SCREAMING_SNAKE_CASE names.
 */
public final class JavaTelecomsCapturedOutputs {

    private JavaTelecomsCapturedOutputs() {}

    public static final CapturedOutput<FeasibilityResponse> FEASIBILITY_RESULT =
        TelecomsCapturedOutputs.INSTANCE.getFeasibilityResult();

    public static final CapturedOutput<OrderResponse> ORDER_CONFIRMATION =
        TelecomsCapturedOutputs.INSTANCE.getOrderConfirmation();
}
