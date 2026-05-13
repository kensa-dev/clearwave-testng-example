package com.clearwave;

import com.clearwave.domain.LineProfile;
import com.clearwave.feasibility.FeasibilityRequest;
import com.clearwave.feasibility.FeasibilityResponse;
import com.clearwave.stubs.FeasibilityScenario;
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

import java.util.List;

import static com.clearwave.support.ClearwaveTestNgListener.*;
import static com.clearwave.support.JavaTelecomsCapturedOutputs.*;
import static com.clearwave.support.JavaTelecomsFixtures.*;

@Notes("""
        **Feasibility Service** queries both suppliers for every postcode regardless of the service package requested.
        Profiles from both suppliers are merged and sorted by download speed (fastest first).

        Supplier integrations:
        - OpenNetwork — voice and broadband, JSON protocol
        - FibreVision — broadband only, XML protocol
        """)
@Listeners(ClearwaveTestNgListener.class)
class FeasibilityServiceJavaTest implements KensaTest, WithHamcrest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void addressIsServiceableByBothSuppliers() {
        given(bothSuppliersAreServiceable());

        whenever(aFeasibilityCheckIsRequestedForTheServiceAddress());

        then(theFeasibilityResult(), shouldReturnServiceableResultWith(
            3,
            fixtures(VOICE_DOWNLOAD_SPEED),
            fixtures(VOICE_SUPPLIER)
        ));
    }

    @Test
    void addressIsNotServiceableByOpenNetwork() {
        given(openNetworkHasNoCoverage());
        and(fibreVisionIsServiceable());

        whenever(aFeasibilityCheckIsRequestedForTheServiceAddress());

        then(theFeasibilityResult(), shouldReturnServiceableResultWith(
            1,
            fixtures(BROADBAND_DOWNLOAD_SPEED),
            fixtures(BROADBAND_SUPPLIER)
        ));
    }

    @Test
    void addressIsNotServiceableByEitherSupplier() {
        given(openNetworkHasNoCoverage());
        and(fibreVisionHasNoCoverage());

        whenever(aFeasibilityCheckIsRequestedForTheServiceAddress());

        then(theFeasibilityResult(), shouldNotBeServiceable());
    }

    @Test
    void openNetworkSupplierErrorIsHandledGracefully() {
        given(openNetworkIsUnavailable());
        and(fibreVisionIsServiceable());

        whenever(aFeasibilityCheckIsRequestedForTheServiceAddress());

        then(theFeasibilityResult(), shouldReturnServiceableResultWith(
            1,
            fixtures(BROADBAND_DOWNLOAD_SPEED),
            fixtures(BROADBAND_SUPPLIER)
        ));
    }

    // --- Givens ---

    private Action<GivensContext> bothSuppliersAreServiceable() {
        return ctx -> {
            var tid = ctx.getFixtures().get(TRACKING_ID);
            openNetworkStub.primeFeasibility(tid, new FeasibilityScenario.Serviceable(List.of(
                new LineProfile("FTTP", ctx.getFixtures().get(VOICE_DOWNLOAD_SPEED), ctx.getFixtures().get(VOICE_UPLOAD_SPEED), "Full Fibre 900", ctx.getFixtures().get(VOICE_SUPPLIER)),
                new LineProfile("FTTP", 500, 75, "Full Fibre 500", ctx.getFixtures().get(VOICE_SUPPLIER))
            )));
            fibreVisionStub.primeFeasibility(tid, new FeasibilityScenario.Serviceable(List.of(
                new LineProfile("FTTC", ctx.getFixtures().get(BROADBAND_DOWNLOAD_SPEED), ctx.getFixtures().get(BROADBAND_UPLOAD_SPEED), "Superfast 80", ctx.getFixtures().get(BROADBAND_SUPPLIER))
            )));
        };
    }

    private Action<GivensContext> openNetworkHasNoCoverage() {
        return ctx -> openNetworkStub.primeFeasibility(ctx.getFixtures().get(TRACKING_ID), FeasibilityScenario.NotServiceable.INSTANCE);
    }

    private Action<GivensContext> fibreVisionIsServiceable() {
        return ctx -> fibreVisionStub.primeFeasibility(ctx.getFixtures().get(TRACKING_ID), new FeasibilityScenario.Serviceable(List.of(
            new LineProfile("FTTC", ctx.getFixtures().get(BROADBAND_DOWNLOAD_SPEED), ctx.getFixtures().get(BROADBAND_UPLOAD_SPEED), "Superfast 80", ctx.getFixtures().get(BROADBAND_SUPPLIER))
        )));
    }

    private Action<GivensContext> fibreVisionHasNoCoverage() {
        return ctx -> fibreVisionStub.primeFeasibility(ctx.getFixtures().get(TRACKING_ID), FeasibilityScenario.NotServiceable.INSTANCE);
    }

    private Action<GivensContext> openNetworkIsUnavailable() {
        return ctx -> openNetworkStub.primeFeasibility(ctx.getFixtures().get(TRACKING_ID), FeasibilityScenario.SupplierError.INSTANCE);
    }

    // --- Action ---

    private Action<ActionContext> aFeasibilityCheckIsRequestedForTheServiceAddress() {
        return ctx -> {
            try {
                var tid = ctx.getFixtures().get(TRACKING_ID);
                openNetworkStub.register(tid, ctx.getInteractions());
                fibreVisionStub.register(tid, ctx.getInteractions());

                var requestBody = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new FeasibilityRequest(ctx.getFixtures().get(SERVICE_ADDRESS), List.of("VOICE", "BROADBAND")));

                var httpRequest = request(Method.POST, "http://localhost:" + feasibilityService.getPort() + "/api/feasibility")
                    .header(TrackingId.HEADER, tid.toString())
                    .header("Content-Type", "application/json")
                    .body(requestBody);

                ctx.getInteractions().capture(
                    CapturedInteractionBuilder.from(TelecomsParty.Customer)
                        .to(TelecomsParty.FeasibilityService)
                        .with(requestBody, "Feasibility Request")
                        .with(Attributes.of("language", Language.Json))
                        .underTest(true)
                );

                var response = httpClient.invoke(httpRequest);
                ctx.getOutputs().put(FEASIBILITY_RESULT, mapper.readValue(response.bodyString(), FeasibilityResponse.class));

                ctx.getInteractions().capture(
                    CapturedInteractionBuilder.from(TelecomsParty.FeasibilityService)
                        .to(TelecomsParty.Customer)
                        .with(response, "Feasibility Response")
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    // --- State Collector ---

    private StateCollector<FeasibilityResponse> theFeasibilityResult() {
        return ctx -> ctx.getOutputs().get(FEASIBILITY_RESULT);
    }

    // --- Assertions ---

    private Matcher<FeasibilityResponse> shouldReturnServiceableResultWith(
        int profileCount, int fastestDownloadSpeed, String fastestSupplier
    ) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                if (!(item instanceof FeasibilityResponse result)) return false;
                return result.getServiceable()
                    && result.getProfiles().size() == profileCount
                    && result.getProfiles().get(0).getDownloadSpeed() == fastestDownloadSpeed
                    && result.getProfiles().get(0).getSupplier().equals(fastestSupplier);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                    profileCount + " profile(s), fastest " + fastestDownloadSpeed + " Mbps from " + fastestSupplier);
            }
        };
    }

    private Matcher<FeasibilityResponse> shouldNotBeServiceable() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                if (!(item instanceof FeasibilityResponse result)) return false;
                return !result.getServiceable() && result.getProfiles().isEmpty();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("not serviceable with no profiles");
            }
        };
    }
}
