package com.dev.moment.moment;

import com.dev.moment.common.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Feed 24 giờ (danh-sach-api.md mục 3.4): limit mặc định 10, tối đa 30. */
@RestController
@RequestMapping("/api/v1/feed")
class FeedController {

    private final MomentService momentService;

    FeedController(MomentService momentService) {
        this.momentService = momentService;
    }

    @GetMapping
    MomentResponse.Page feed(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit) {
        return momentService.feed(CurrentUser.id(jwt), cursor, limit);
    }
}
