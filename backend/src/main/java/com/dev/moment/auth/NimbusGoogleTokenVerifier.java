package com.dev.moment.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Xác minh ID token bằng khóa công khai của Google (JWKS): chữ ký RS256, iss, aud bằng client id
 * của dự án, hạn dùng (danh-sach-api.md mục 3.1). Khóa được Nimbus cache nên không gọi Google mỗi lần.
 */
@Component
class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusGoogleTokenVerifier.class);
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final NimbusJwtDecoder decoder;
    private final boolean configured;

    NimbusGoogleTokenVerifier(AuthProperties properties, Clock clock) {
        String clientId = properties.googleClientId();
        this.configured = !clientId.isEmpty();
        // Gọi dịch vụ ngoài phải có timeout (CLAUDE.md).
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        this.decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUERS::contains),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(clientId))));
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        if (!configured) {
            log.warn("Chưa cấu hình GOOGLE_CLIENT_ID nên từ chối đăng nhập Google");
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            String email = jwt.getClaimAsString("email");
            if (email == null) {
                return Optional.empty();
            }
            return Optional.of(new GoogleIdentity(jwt.getSubject(), email,
                    Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))));
        } catch (JwtException exception) {
            // Không log token; chỉ ghi loại lỗi để tra cứu.
            log.info("Google ID token bị từ chối: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
