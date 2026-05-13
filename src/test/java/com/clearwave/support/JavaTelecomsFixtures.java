package com.clearwave.support;

import com.clearwave.domain.AppointmentSlot;
import com.clearwave.domain.LineProfile;
import com.clearwave.domain.ServiceAddress;
import dev.kensa.fixture.Fixture;
import dev.kensa.fixture.FixtureContainer;

import java.time.LocalDate;

/**
 * Java-friendly aliases for {@link TelecomsFixtures}.
 * Import statically to reference fixture keys with idiomatic SCREAMING_SNAKE_CASE names.
 * Implements {@link FixtureContainer} so Kensa resolves SCREAMING_SNAKE_CASE names
 * in Java test sentences alongside the camelCase names from {@link TelecomsFixtures}.
 */
public final class JavaTelecomsFixtures implements FixtureContainer {

    private JavaTelecomsFixtures() {}

    // --- Correlation ---
    public static final Fixture<TrackingId>       TRACKING_ID           = TelecomsFixtures.INSTANCE.getTrackingId();

    // --- Customer ---
    public static final Fixture<String>           CUSTOMER_ID           = TelecomsFixtures.INSTANCE.getCustomerId();

    // --- Address ---
    public static final Fixture<String>           POSTCODE              = TelecomsFixtures.INSTANCE.getPostcode();
    public static final Fixture<String>           ADDRESS_LINE_1        = TelecomsFixtures.INSTANCE.getAddressLine1();
    public static final Fixture<String>           TOWN                  = TelecomsFixtures.INSTANCE.getTown();
    public static final Fixture<String>           COUNTY                = TelecomsFixtures.INSTANCE.getCounty();
    public static final Fixture<ServiceAddress>   SERVICE_ADDRESS       = TelecomsFixtures.INSTANCE.getServiceAddress();

    // --- Voice profile ---
    public static final Fixture<String>           VOICE_SUPPLIER        = TelecomsFixtures.INSTANCE.getVoiceSupplier();
    public static final Fixture<Integer>          VOICE_DOWNLOAD_SPEED  = TelecomsFixtures.INSTANCE.getVoiceDownloadSpeed();
    public static final Fixture<Integer>          VOICE_UPLOAD_SPEED    = TelecomsFixtures.INSTANCE.getVoiceUploadSpeed();
    public static final Fixture<LineProfile>      VOICE_PROFILE         = TelecomsFixtures.INSTANCE.getVoiceProfile();

    // --- Broadband profile ---
    public static final Fixture<String>           BROADBAND_SUPPLIER        = TelecomsFixtures.INSTANCE.getBroadbandSupplier();
    public static final Fixture<Integer>          BROADBAND_DOWNLOAD_SPEED  = TelecomsFixtures.INSTANCE.getBroadbandDownloadSpeed();
    public static final Fixture<Integer>          BROADBAND_UPLOAD_SPEED    = TelecomsFixtures.INSTANCE.getBroadbandUploadSpeed();
    public static final Fixture<LineProfile>      BROADBAND_PROFILE         = TelecomsFixtures.INSTANCE.getBroadbandProfile();

    // --- Appointment ---
    public static final Fixture<LocalDate>        APPOINTMENT_DATE      = TelecomsFixtures.INSTANCE.getAppointmentDate();
    public static final Fixture<String>           APPOINTMENT_TIME_SLOT = TelecomsFixtures.INSTANCE.getAppointmentTimeSlot();
    public static final Fixture<AppointmentSlot>  APPOINTMENT_SLOT      = TelecomsFixtures.INSTANCE.getAppointmentSlot();
}
