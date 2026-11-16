import { apiClient } from "./index";

// Mời và bạn bè (danh-sach-api.md mục 3.3).

export type UserSummary = {
  id: string;
  username: string | null;
  displayName: string | null;
  avatarUrl: string | null;
};

export type Invite = { code: string; url: string };

export type Inviter = { username: string | null; displayName: string | null; avatarUrl: string | null };

export type Friend = UserSummary & { friendsSince: string };

export type FriendList = { items: Friend[]; count: number; limit: number };

export const friendsApi = {
  myInvite: () => apiClient.request<Invite>("/invites/me", { auth: true }),

  rotateInvite: () => apiClient.request<Invite>("/invites/me/rotate", { method: "POST", auth: true }),

  // Công khai: trang mời gọi được khi chưa đăng nhập.
  inviter: async (code: string) =>
    (await apiClient.request<{ inviter: Inviter }>(`/invites/${encodeURIComponent(code)}`)).inviter,

  accept: (code: string) =>
    apiClient.request<{ friend: UserSummary; alreadyFriends: boolean }>(
      `/invites/${encodeURIComponent(code)}/accept`,
      { method: "POST", auth: true },
    ),

  list: () => apiClient.request<FriendList>("/friends", { auth: true }),

  unfriend: (userId: string) =>
    apiClient.request<void>(`/friends/${encodeURIComponent(userId)}`, { method: "DELETE", auth: true }),
};
