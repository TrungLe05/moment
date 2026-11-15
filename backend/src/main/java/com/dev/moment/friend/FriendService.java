package com.dev.moment.friend;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Điểm vào của tính năng bạn bè cho các gói khác. */
@Service
public class FriendService {

    /** BR-06: mỗi người tối đa 15 bạn. */
    public static final int FRIEND_LIMIT = 15;

    private final FriendshipRepository friendshipRepository;

    public FriendService(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional(readOnly = true)
    public int countFriends(UUID userId) {
        return (int) friendshipRepository.countByUser(userId);
    }
}
