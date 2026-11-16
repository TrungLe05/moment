/** Thông báo đẩy. MVP chưa dùng; Web Push vào giai đoạn 2, FCM/APNs ở bản native. */
export interface NotificationService {
  isSupported(): boolean;
  requestPermission(): Promise<"granted" | "denied" | "unsupported">;
}

export class UnsupportedNotificationService implements NotificationService {
  isSupported(): boolean {
    return false;
  }

  requestPermission(): Promise<"unsupported"> {
    return Promise.resolve("unsupported");
  }
}
