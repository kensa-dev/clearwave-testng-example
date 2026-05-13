package com.clearwave;

import com.clearwave.order.NotificationStatus;
import com.clearwave.order.OrderRequest;
import com.clearwave.order.OrderResponse;
import com.clearwave.order.SupplierNotification;
import com.clearwave.stubs.OrderScenario;
import com.clearwave.support.ClearwaveTestNgListener;
import com.clearwave.support.TelecomsParty;
import com.clearwave.support.TrackingId;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kensa.Action;
import dev.kensa.ActionContext;
import dev.kensa.GivensContext;
import dev.kensa.Notes;
import dev.kensa.StateCollector;
import dev.kensa.hamcrest.WithHamcrest;
import dev.kensa.render.Language;
import dev.kensa.state.CapturedInteractionBuilder;
import dev.kensa.testng.KensaTest;
import dev.kensa.util.Attributes;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.http4k.core.Method;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;

import static com.clearwave.order.NotificationStatus.*;
import static com.clearwave.support.ClearwaveTestNgListener.*;
import static com.clearwave.support.JavaTelecomsCapturedOutputs.*;
import static com.clearwave.support.JavaTelecomsFixtures.*;

@Notes("""
        **Order Service** orchestrates provisioning across two suppliers for a combined voice + broadband package.

        Notification lifecycle (per supplier):
        - ACKNOWLEDGED — Supplier has received and accepted the order
        - COMMITTED — Resources reserved; engineer visit booked
        - DELAYED — Engineer visit rescheduled
        - COMPLETED — Service is live
        - REJECTED — Order cannot be fulfilled
        """)
@Listeners(ClearwaveTestNgListener.class)
class OrderServiceJavaTest implements KensaTest, WithHamcrest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void voiceAndBroadbandOrderIsSuccessfullyCompleted() {
        given(openNetworkWillCompleteTheOrder());
        and(fibreVisionWillCompleteTheOrder());

        whenever(aVoiceAndBroadbandOrderIsPlaced());

        then(theOrderConfirmation(), shouldBePending());
        thenEventually(Duration.ofSeconds(10), allNotifications(), shouldShowBothSuppliersCompletedSuccessfully(
            fixtures(VOICE_SUPPLIER),
            fixtures(BROADBAND_SUPPLIER)
        ));
    }

    @Test
    void orderIsDelayedThenCompleted() {
        given(openNetworkWillCompleteTheOrder());
        and(fibreVisionWillDelayThenCompleteTheOrder());

        whenever(aVoiceAndBroadbandOrderIsPlaced());

        then(theOrderConfirmation(), shouldBePending());
        thenEventually(Duration.ofSeconds(10), fibreVisionNotifications(), shouldShowDelayedThenCompleted(
            fixtures(BROADBAND_SUPPLIER)
        ));
    }

    @Test
    void orderIsRejectedByFibreVision() {
        given(openNetworkWillCompleteTheOrder());
        and(fibreVisionWillRejectTheOrder());

        whenever(aVoiceAndBroadbandOrderIsPlaced());

        then(theOrderConfirmation(), shouldBePending());
        thenEventually(Duration.ofSeconds(10), fibreVisionNotifications(), shouldBeRejected(
            fixtures(BROADBAND_SUPPLIER)
        ));
    }

    // --- Givens ---

    private Action<GivensContext> openNetworkWillCompleteTheOrder() {
        return ctx -> openNetworkStub.primeOrder(ctx.getFixtures().get(TRACKING_ID), OrderScenario.Companion.getCompleted());
    }

    private Action<GivensContext> fibreVisionWillCompleteTheOrder() {
        return ctx -> fibreVisionStub.primeOrder(ctx.getFixtures().get(TRACKING_ID), OrderScenario.Companion.getCompleted());
    }

    private Action<GivensContext> fibreVisionWillDelayThenCompleteTheOrder() {
        return ctx -> fibreVisionStub.primeOrder(ctx.getFixtures().get(TRACKING_ID), OrderScenario.Companion.getDelayed());
    }

    private Action<GivensContext> fibreVisionWillRejectTheOrder() {
        return ctx -> fibreVisionStub.primeOrder(ctx.getFixtures().get(TRACKING_ID), OrderScenario.Rejected.INSTANCE);
    }

    // --- Action ---

    private Action<ActionContext> aVoiceAndBroadbandOrderIsPlaced() {
        return ctx -> {
            try {
                var tid = ctx.getFixtures().get(TRACKING_ID);
                openNetworkStub.register(tid, ctx.getInteractions());
                fibreVisionStub.register(tid, ctx.getInteractions());

                var orderRequest = new OrderRequest(
                    ctx.getFixtures().get(CUSTOMER_ID),
                    ctx.getFixtures().get(SERVICE_ADDRESS),
                    ctx.getFixtures().get(VOICE_PROFILE),
                    ctx.getFixtures().get(BROADBAND_PROFILE),
                    ctx.getFixtures().get(APPOINTMENT_SLOT),
                    "http://localhost:" + orderService.getPort() + "/api/notifications/" + tid
                );

                var requestBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(orderRequest);
                var httpRequest = request(Method.POST, "http://localhost:" + orderService.getPort() + "/api/orders")
                    .header(TrackingId.HEADER, tid.toString())
                    .header("Content-Type", "application/json")
                    .body(requestBody);

                ctx.getInteractions().capture(
                    CapturedInteractionBuilder.from(TelecomsParty.Customer)
                        .to(TelecomsParty.OrderService)
                        .with(requestBody, "Place Order Request")
                        .with(Attributes.of("language", Language.Json))
                        .underTest(true)
                );

                var response = httpClient.invoke(httpRequest);
                ctx.getOutputs().put(ORDER_CONFIRMATION, mapper.readValue(response.bodyString(), OrderResponse.class));

                ctx.getInteractions().capture(
                    CapturedInteractionBuilder.from(TelecomsParty.OrderService)
                        .to(TelecomsParty.Customer)
                        .with(response, "Order Confirmation — PENDING")
                );

                ctx.getInteractions().captureTimePassing("Awaiting supplier notifications");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    // --- State Collectors ---

    private StateCollector<OrderResponse> theOrderConfirmation() {
        return ctx -> ctx.getOutputs().get(ORDER_CONFIRMATION);
    }

    private StateCollector<List<SupplierNotification>> allNotifications() {
        return ctx -> orderService.notificationsFor(ctx.getFixtures().get(TRACKING_ID).toString());
    }

    private StateCollector<List<SupplierNotification>> fibreVisionNotifications() {
        return ctx -> orderService.notificationsFor(ctx.getFixtures().get(TRACKING_ID).toString())
            .stream().filter(n -> n.getSupplier().equals("FibreVision")).toList();
    }

    // --- Assertions ---

    private Matcher<OrderResponse> shouldBePending() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof OrderResponse r && "PENDING".equals(r.getStatus());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("order status PENDING");
            }
        };
    }

    private Matcher<List<SupplierNotification>> shouldShowBothSuppliersCompletedSuccessfully(
        String voiceSupplier, String broadbandSupplier
    ) {
        return new BaseMatcher<>() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(Object item) {
                if (!(item instanceof List<?> notifications)) return false;
                var completedLifecycle = List.of(ACKNOWLEDGED, COMMITTED, COMPLETED);
                var voiceStatuses = ((List<SupplierNotification>) notifications).stream()
                    .filter(n -> n.getSupplier().equals(voiceSupplier)).map(SupplierNotification::getStatus).toList();
                var broadbandStatuses = ((List<SupplierNotification>) notifications).stream()
                    .filter(n -> n.getSupplier().equals(broadbandSupplier)).map(SupplierNotification::getStatus).toList();
                return notifications.size() == 6
                    && voiceStatuses.equals(completedLifecycle)
                    && broadbandStatuses.equals(completedLifecycle);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("both " + voiceSupplier + " and " + broadbandSupplier + " completed successfully");
            }
        };
    }

    private Matcher<List<SupplierNotification>> shouldShowDelayedThenCompleted(String supplier) {
        return new BaseMatcher<>() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(Object item) {
                if (!(item instanceof List<?> notifications)) return false;
                var expected = List.of(ACKNOWLEDGED, COMMITTED, DELAYED, COMPLETED);
                var actual = ((List<SupplierNotification>) notifications).stream().map(SupplierNotification::getStatus).toList();
                return actual.equals(expected);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(supplier + ": ACKNOWLEDGED → COMMITTED → DELAYED → COMPLETED");
            }
        };
    }

    private Matcher<List<SupplierNotification>> shouldBeRejected(String supplier) {
        return new BaseMatcher<>() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(Object item) {
                if (!(item instanceof List<?> notifications)) return false;
                return notifications.size() == 1
                    && ((List<SupplierNotification>) notifications).get(0).getStatus() == NotificationStatus.REJECTED;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("single REJECTED notification from " + supplier);
            }
        };
    }
}
