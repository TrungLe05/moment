package com.dev.moment.common;

import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.ProblemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật dùng chung: access token JWT qua header Authorization (T-113).
 * Mở health check và nhóm Auth công khai (trừ logout); mọi đường khác bắt xác thực,
 * lỗi trả theo định dạng Problem Details.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemWriter problemWriter) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.write(request, response, ErrorCode.UNAUTHENTICATED)))
                .cors(Customizer.withDefaults())
                // API không giữ session; access token đi qua header Authorization nên không cần CSRF token.
                // Cookie refresh token có SameSite=Strict và Path=/api/v1/auth (danh-sach-api.md mục 2).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.write(request, response, ErrorCode.UNAUTHENTICATED))
                        // Không có quyền thì trả 404 như quy ước, không để lộ tài nguyên có tồn tại.
                        .accessDeniedHandler((request, response, exception) ->
                                problemWriter.write(request, response, ErrorCode.NOT_FOUND)));
        return http.build();
    }

    // Chặn Spring Boot tự tạo user mặc định và in mật khẩu của nó ra log.
    // Người dùng thật lấy từ database khi làm xác thực (T-111, T-113).
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
