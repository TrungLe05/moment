package com.dev.moment.friend;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.user.UserService;
import com.dev.moment.user.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Điểm vào của tính năng bạn bè cho controller và các gói khác (E2). */
@Service
public class FriendService {

    /** BR-06: mỗi người tối đa 15 bạn. */
    public static final int FRIEND_LIMIT = 15;

    // Tham số cho chuỗi detail của FRIEND_LIMIT_REACHED: cho biết bên nào đã đủ 15 (danh-sach-api.md mục 3.3).
    private static final int LIMIT_SIDE_ME = 0;
    private static final int LIMIT_SIDE_INVITER = 1;

    private final FriendshipRepository friendshipRepository;
    private final InviteService inviteService;
    private final UserService userService;
    private final Clock clock;

    public FriendService(FriendshipRepository friendshipRepository, InviteService inviteService,
            UserService userService, Clock clock) {
        this.friendshipRepository = friendshipRepository;
        this.inviteService = inviteService;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int countFriends(UUID userId) {
        return (int) friendshipRepository.countByUser(userId);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID left, UUID right) {
        UserPair pair = UserPair.of(left, right);
        return friendshipRepository.existsById(new Friendship.FriendshipId(pair.first(), pair.second()));
    }

    /** Id các bạn hiện tại, dùng chốt người nhận khi đăng khoảnh khắc (BR-12). */
    @Transactional(readOnly = true)
    public Set<UUID> friendIds(UUID userId) {
        return friendshipRepository.findAllByUser(userId).stream()
                .map(friendship -> friendship.otherThan(userId))
                .collect(Collectors.toSet());
    }

    /** Trang mời (US-11): người mời theo mã; mã sai hoặc đã đổi → INVITE_INVALID. */
    @Transactional(readOnly = true)
    public UserSummary findInviter(String code) {
        return inviteService.findInviterId(code)
                .flatMap(userService::findSummary)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));
    }

    /**
     * Kết bạn qua mã mời (US-11). BR-05: thành bạn ngay, không cần duyệt.
     * BR-06: khóa hai dòng users theo thứ tự id cố định rồi mới đếm, để hai lời mời cùng lúc
     * không đẩy một người lên quá 15 bạn và hai transaction ngược chiều không deadlock.
     */
    @Transactional
    public AcceptResult accept(UUID meId, String code) {
        UUID inviterId = inviteService.findInviterId(code)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));
        if (inviterId.equals(meId)) {
            throw new ApiException(ErrorCode.INVITE_SELF);
        }
        UserPair pair = UserPair.of(meId, inviterId);
        try {
            userService.lockInOrder(pair.inLockOrder());
        } catch (ApiException exception) {
            // Người mời vừa xóa tài khoản: với trang mời, link coi như hết hiệu lực.
            if (exception.code() == ErrorCode.NOT_FOUND) {
                throw new ApiException(ErrorCode.INVITE_INVALID);
            }
            throw exception;
        }

        UserSummary inviter = userService.findSummary(inviterId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));
        Friendship.FriendshipId id = new Friendship.FriendshipId(pair.first(), pair.second());
        // BR-06: đã là bạn thì không báo lỗi, trả trạng thái hiện tại.
        if (friendshipRepository.existsById(id)) {
            return new AcceptResult(inviter, true);
        }
        if (friendshipRepository.countByUser(meId) >= FRIEND_LIMIT) {
            throw ApiException.withArgs(ErrorCode.FRIEND_LIMIT_REACHED, LIMIT_SIDE_ME);
        }
        if (friendshipRepository.countByUser(inviterId) >= FRIEND_LIMIT) {
            throw ApiException.withArgs(ErrorCode.FRIEND_LIMIT_REACHED, LIMIT_SIDE_INVITER);
        }
        friendshipRepository.save(new Friendship(pair, clock.instant()));
        return new AcceptResult(inviter, false);
    }

    /** US-12: danh sách bạn, mới kết bạn trước. */
    @Transactional(readOnly = true)
    public List<Friend> listFriends(UUID meId) {
        List<Friendship> friendships = friendshipRepository.findAllByUser(meId);
        Map<UUID, UserSummary> summaries = userService.findSummaries(
                friendships.stream().map(friendship -> friendship.otherThan(meId)).toList());
        return friendships.stream()
                .filter(friendship -> summaries.containsKey(friendship.otherThan(meId)))
                .map(friendship -> new Friend(summaries.get(friendship.otherThan(meId)), friendship.getCreatedAt()))
                .toList();
    }

    /** US-13, BR-07: hủy có hiệu lực với cả hai bên ngay; không phải bạn → NOT_FOUND. */
    @Transactional
    public void unfriend(UUID meId, UUID otherId) {
        UserPair pair = UserPair.of(meId, otherId);
        if (friendshipRepository.deletePair(pair.first(), pair.second()) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    public record AcceptResult(UserSummary friend, boolean alreadyFriends) {
    }

    public record Friend(UserSummary user, Instant friendsSince) {
    }
}
