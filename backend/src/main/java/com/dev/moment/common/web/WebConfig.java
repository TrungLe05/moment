package com.dev.moment.common.web;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Cấu hình web dùng chung: ngôn ngữ, CORS, đồng hồ. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig {

    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    // Accept-Language chỉ nhận vi hoặc en; ngôn ngữ khác hoặc thiếu header thì dùng en (US-07).
    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(VIETNAMESE, Locale.ENGLISH));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    // Origin lấy từ cấu hình để sau này thêm origin của app Capacitor mà không sửa code (kế hoạch, quy tắc 6).
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept-Language", "X-Client-Type", RequestIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(RequestIdFilter.HEADER, "Retry-After"));
        // Cần cho cookie refresh token của web (danh-sach-api.md mục 2).
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    // Mọi logic thời gian (24 giờ, hạn token) lấy giờ từ Clock để test điều khiển được.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
