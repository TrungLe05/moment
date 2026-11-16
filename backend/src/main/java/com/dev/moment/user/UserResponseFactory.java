package com.dev.moment.user;

import com.dev.moment.friend.FriendService;
import org.springframework.stereotype.Component;

/**
 * Dựng {@link UserResponse} cho controller. Tách khỏi UserService vì cần số bạn bè từ FriendService,
 * trong khi FriendService lại dùng UserService; gộp chung sẽ thành phụ thuộc vòng.
 */
@Component
public class UserResponseFactory {

    private final FriendService friendService;

    UserResponseFactory(FriendService friendService) {
        this.friendService = friendService;
    }

    public UserResponse toResponse(UserAccount account) {
        // Ảnh đại diện (presigned URL) thêm cùng T-116.
        return new UserResponse(account.id(), account.email(), account.username(), account.displayName(), null,
                account.locale(), account.needsOnboarding(), friendService.countFriends(account.id()),
                FriendService.FRIEND_LIMIT, account.demo());
    }
}
