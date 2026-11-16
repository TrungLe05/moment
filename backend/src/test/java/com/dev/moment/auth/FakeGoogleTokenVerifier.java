package com.dev.moment.auth;

import java.util.Optional;

/** Bản giả của {@link GoogleTokenVerifier}; đặt cùng gói vì interface chỉ dùng trong gói auth. */
public class FakeGoogleTokenVerifier implements GoogleTokenVerifier {

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        String[] parts = idToken.split("\\|");
        if (parts.length != 4 || !parts[0].equals("fake-google")) {
            return Optional.empty();
        }
        return Optional.of(new GoogleIdentity(parts[1], parts[2], Boolean.parseBoolean(parts[3])));
    }
}
