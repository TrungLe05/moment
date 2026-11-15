# 0001. Làm web desktop trước, viết code sẵn sàng đóng gói Capacitor

Trạng thái: Đã chấp nhận · Ngày: 15/11/2026

## Bối cảnh

Sản phẩm thiên về dùng trên điện thoại, nhưng dự án do một người làm trên máy cấu hình yếu, không chạy được Flutter và máy ảo Android. Đóng gói bằng Capacitor là khả thi nhưng kéo theo nhiều việc native (camera, đăng nhập Google, thông báo đẩy, build iOS cần Mac).

## Quyết định

Làm web desktop (PWA) trước. Android ở giai đoạn 7, iOS ở giai đoạn 8, đều bằng Capacitor. Ngay từ MVP, code tuân theo 8 quy tắc ở mục "Chiến lược đa nền tảng" của kế hoạch tổng quan, trong đó quan trọng nhất:

- Next.js chạy `output: 'export'`, không dùng tính năng phía server của Next.js.
- Mọi thứ phụ thuộc nền tảng đi qua interface (`CameraService`, `TokenStore`, `GoogleSignIn`...).
- Đăng nhập Google bằng ID token gửi lên backend, không dùng redirect `oauth2Login`.

## Hệ quả

- MVP ra nhanh hơn, không cần Android Studio hay Mac.
- Trải nghiệm trên điện thoại ở MVP chỉ ở mức dùng được (giới hạn camera, thông báo của Safari iOS).
- Không dùng được SSR, nên trang chia sẻ công khai (nếu có sau này) không có SEO; chấp nhận được vì nội dung là riêng tư.
