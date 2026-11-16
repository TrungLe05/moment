package com.dev.moment.reaction;

import com.dev.moment.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reaction (danh-sach-api.md mục 3.5). */
@RestController
@RequestMapping("/api/v1/moments/{id}")
class ReactionController {

    private final ReactionService reactionService;

    ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PutMapping("/reaction")
    ReactionResponses.Mine react(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ReactionResponses.ReactRequest request) {
        return new ReactionResponses.Mine(reactionService.react(CurrentUser.id(jwt), id, request.emoji()));
    }

    @DeleteMapping("/reaction")
    ResponseEntity<Void> unreact(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        reactionService.unreact(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reactions")
    ReactionResponses.Items list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return new ReactionResponses.Items(reactionService.list(CurrentUser.id(jwt), id));
    }
}
