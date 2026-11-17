package com.dev.moment.moment;

import com.dev.moment.common.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Kho lưu giữ (danh-sach-api.md mục 3.4): `scope` bắt buộc, limit mặc định 30, tối đa 60. */
@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController {

    private final MomentService momentService;

    ArchiveController(MomentService momentService) {
        this.momentService = momentService;
    }

    @GetMapping
    MomentResponse.Page archive(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @Pattern(regexp = "mine|received") String scope,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") @Min(1) @Max(60) int limit) {
        return momentService.archive(CurrentUser.id(jwt), ArchiveScope.of(scope), cursor, limit);
    }
}
