package com.dev.moment.friend;

import com.dev.moment.common.ratelimit.RateLimit;
import com.dev.moment.common.ratelimit.RateLimiter;
import com.dev.moment.common.security.CurrentUser;
import com.dev.moment.user.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mời và bạn bè (danh-sach-api.md mục 3.3). */
@RestController
@RequestMapping("/api/v1")
class FriendController {

    private final FriendService friendService;
    private final InviteService inviteService;
    private final RateLimiter rateLimiter;

    FriendController(FriendService friendService, InviteService inviteService, RateLimiter rateLimiter) {
        this.friendService = friendService;
        this.inviteService = inviteService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/invites/me")
    InviteService.Invite myInvite(@AuthenticationPrincipal Jwt jwt) {
        return inviteService.myInvite(CurrentUser.id(jwt));
    }

    @PostMapping("/invites/me/rotate")
    InviteService.Invite rotate(@AuthenticationPrincipal Jwt jwt) {
        return inviteService.rotate(CurrentUser.id(jwt));
    }

    /** Công khai để trang mời hiện người mời cả khi người xem chưa đăng nhập. */
    @GetMapping("/invites/{code}")
    InviterResponse inviter(@PathVariable String code, HttpServletRequest request) {
        rateLimiter.consume(RateLimit.INVITE_PER_IP, request.getRemoteAddr());
        UserSummary inviter = friendService.findInviter(code);
        return new InviterResponse(new InviterView(inviter.username(), inviter.displayName(), inviter.avatarUrl()));
    }

    @PostMapping("/invites/{code}/accept")
    AcceptResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String code, HttpServletRequest request) {
        rateLimiter.consume(RateLimit.INVITE_PER_IP, request.getRemoteAddr());
        FriendService.AcceptResult result = friendService.accept(CurrentUser.id(jwt), code);
        return new AcceptResponse(result.friend(), result.alreadyFriends());
    }

    @GetMapping("/friends")
    FriendListResponse friends(@AuthenticationPrincipal Jwt jwt) {
        List<FriendItem> items = friendService.listFriends(CurrentUser.id(jwt)).stream()
                .map(friend -> new FriendItem(friend.user().id(), friend.user().username(), friend.user().displayName(),
                        friend.user().avatarUrl(), friend.friendsSince()))
                .toList();
        return new FriendListResponse(items, items.size(), FriendService.FRIEND_LIMIT);
    }

    @DeleteMapping("/friends/{userId}")
    ResponseEntity<Void> unfriend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        friendService.unfriend(CurrentUser.id(jwt), userId);
        return ResponseEntity.noContent().build();
    }

    record InviterView(String username, String displayName, String avatarUrl) {
    }

    record InviterResponse(InviterView inviter) {
    }

    record AcceptResponse(UserSummary friend, boolean alreadyFriends) {
    }

    record FriendItem(UUID id, String username, String displayName, String avatarUrl, Instant friendsSince) {
    }

    record FriendListResponse(List<FriendItem> items, int count, int limit) {
    }
}
