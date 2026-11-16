package com.dev.moment.moment;

import com.dev.moment.common.ratelimit.RateLimit;
import com.dev.moment.common.ratelimit.RateLimiter;
import com.dev.moment.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Khoảnh khắc (danh-sach-api.md mục 3.4): tạo, báo tải xong, xử lý lại. */
@RestController
@RequestMapping("/api/v1/moments")
class MomentController {

    private final MomentService momentService;
    private final RateLimiter rateLimiter;

    MomentController(MomentService momentService, RateLimiter rateLimiter) {
        this.momentService = momentService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    ResponseEntity<MomentResponse.Created> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMomentRequest request) {
        rateLimiter.consume(RateLimit.MOMENT_CREATE_PER_USER, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(momentService.create(CurrentUser.id(jwt), request));
    }

    @PostMapping("/{id}/complete")
    ResponseEntity<MomentResponse> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.accepted().body(momentService.complete(CurrentUser.id(jwt), id));
    }

    @PostMapping("/{id}/retry")
    ResponseEntity<MomentResponse> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.accepted().body(momentService.retry(CurrentUser.id(jwt), id));
    }
}
