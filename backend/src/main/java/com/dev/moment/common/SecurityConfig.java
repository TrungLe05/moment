package com.dev.moment.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Cấu hình bảo mật tạm của khung repo (T-102).
 * Chỉ mở health check cho Docker và người vận hành; mọi đường khác vẫn bắt xác thực.
 * Xác thực JWT, CORS và cookie refresh token làm ở T-106, T-113.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
                // API không có trang đăng nhập: chưa xác thực thì trả 401 thay vì 403 mặc định.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    // Chặn Spring Boot tự tạo user mặc định và in mật khẩu của nó ra log.
    // Người dùng thật lấy từ database khi làm xác thực (T-111, T-113).
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
