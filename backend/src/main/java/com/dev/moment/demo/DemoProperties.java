package com.dev.moment.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tài khoản demo (US-51, D-027): bật bằng MOMENT_DEMO_ENABLED; email và mật khẩu đọc từ biến môi trường, không viết
 * cứng trong code. Mật khẩu này cố ý công khai trên trang đăng nhập, nên chỉ dùng cho tài khoản demo.
 */
@ConfigurationProperties("moment.demo")
record DemoProperties(boolean enabled, String email, String password) {
}
