# Nhiệm vụ

Cập nhật: 16/11/2026 · Liên quan: [Tiến độ](tien-do.md), [Quyết định và câu hỏi mở](quyet-dinh.md), [Kế hoạch tổng quan](../00-tong-quan/ke-hoach-tong-quan.md), [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md)

File này là danh sách việc duy nhất của dự án. Mỗi việc có mã `T-xxx` cố định, không đánh lại số; việc bỏ thì đổi trạng thái chứ không xóa dòng. Mã story (`US-xx`) và quy tắc (`BR-xx`) trỏ về đặc tả MVP.

## Quy ước

| Trạng thái | Nghĩa |
| --- | --- |
| Chưa làm | Đã lên kế hoạch, chưa bắt đầu |
| Đang làm | Đang thực hiện; mỗi lúc chỉ nên có 1–2 việc ở trạng thái này |
| Chờ | Bị chặn; cột Ghi chú nói chờ gì (thường là một câu hỏi `Q-xxx`) |
| Xong | Đã làm và kiểm chứng (test pass, tài liệu đã cập nhật) |
| Bỏ | Không làm nữa; Ghi chú nói lý do |

Ưu tiên lấy từ đặc tả: **Must** bắt buộc cho MVP, **Should** làm nếu kịp. Đánh số theo giai đoạn: `T-0xx` chuẩn bị, `T-1xx` MVP, `T-2xx` trở đi cho các giai đoạn sau.

## Đang làm

| Mã | Việc | Ghi chú |
| --- | --- | --- |
| — | Chưa có việc nào đang làm | Việc kế tiếp đề xuất: chốt Q-009, rồi T-103, T-106 |

## Giai đoạn 0 · Chuẩn bị

| Mã | Việc | Trạng thái | Ghi chú |
| --- | --- | --- | --- |
| T-001 | Viết kế hoạch tổng quan | Xong | [ke-hoach-tong-quan.md](../00-tong-quan/ke-hoach-tong-quan.md) |
| T-002 | Đặc tả MVP bản nháp 1 | Xong | [dac-ta-mvp.md](../01-san-pham/dac-ta-mvp.md) |
| T-003 | Thiết kế database bản nháp 1 | Xong | [thiet-ke-database.md](../02-ky-thuat/thiet-ke-database.md) |
| T-004 | Danh sách API bản nháp 1 | Xong | [danh-sach-api.md](../02-ky-thuat/danh-sach-api.md) |
| T-005 | Bản mẫu giao diện 5 màn hình | Xong | Bố cục đạt; font và glassmorphism tách thành T-010, T-011 |
| T-006 | Lập bộ file quản lý nhiệm vụ, tiến độ, quyết định | Xong | Thư mục `05-quan-ly/` và `CLAUDE.md` |
| T-007 | Chốt 8 giả định A1–A8 của đặc tả | Xong | D-014 → D-021 |
| T-008 | Xử lý các điểm lệch giữa tài liệu | Xong | D-023 → D-030 |
| T-009 | Chốt ADR 0003 (hàng đợi bằng PostgreSQL) | Xong | D-022 |
| T-010 | Chọn lại font, hiển thị tốt tiếng Việt có dấu | Chưa làm | [Định hướng giao diện](../03-giao-dien/dinh-huong-giao-dien.md) |
| T-011 | Thử glassmorphism trên bản mẫu, kèm phương án nền đặc | Chưa làm | Tương phản chữ tối thiểu 4.5:1 |
| T-012 | Chọn tên miền và nhà cung cấp VPS | Chưa làm | Chờ Q-010; chặn T-166, không chặn việc viết code |
| T-013 | Ước lượng thời gian cho giai đoạn 1 | Chưa làm | Kế hoạch tổng quan hẹn làm sau khi có đặc tả |
| T-014 | Nâng đặc tả, database, API lên bản chốt | Xong | Bản chốt 1 |
| T-015 | Viết `ARCHITECTURE.md` | Xong | Các mục [Đề xuất] chốt ở T-101 → T-104 |
| T-016 | Bổ sung quy tắc làm việc và quy ước code vào `CLAUDE.md` | Xong | D-006 |

## Giai đoạn 1 · MVP (web desktop + PWA)

### Nền tảng

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-101 | Khởi tạo git repo, `.gitignore`, `.gitattributes` cố định line ending | Must | Xong | Chủ dự án đã `git init` và push lần đầu lên `main`; quy ước nhánh ở D-012 |
| T-102 | Khung backend Spring Boot (Web, Security, Data JPA, Validation, Flyway), profile `api` và `worker` | Must | Xong | Profile mặc định `api`; profile `worker` bật lịch hỏi hàng đợi, cổng 8090; Dockerfile có stage `worker` cài ffmpeg (D-034) |
| T-103 | Khung frontend Next.js `output: 'export'`, Tailwind, shadcn/ui, i18n vi/en | Must | Xong | shadcn/ui, `I18nProvider` (ngôn ngữ trình duyệt, ngôn ngữ tài khoản), client API chung, `PageGate` |
| T-104 | Docker Compose cho dev: PostgreSQL 16, RustFS, Mailpit | Must | Xong | `compose.yaml`; đã chạy: 4 service lên, bucket riêng tư, ghi/đọc S3 được (D-008, D-009, D-010) |
| T-105 | Migration `V1__init.sql` theo thiết kế database | Must | Xong | 11 bảng; test kiểm tra bảng, unique email, định dạng username |
| T-106 | Hạ tầng chung backend: lỗi RFC 9457, `X-Request-Id`, `Accept-Language`, CORS theo danh sách origin | Must | Xong | Gói `common`: `ErrorCode`, `GlobalExceptionHandler`, `RequestIdFilter`, `Clock`, `GraphemeCounter`, `@MaxGraphemes`; 18 test pass |
| T-107 | Lớp interface nền tảng ở frontend (`CameraService`, `NotificationService`, `ShareService`, `TokenStore`, `GoogleSignIn`), bản web | Must | Xong | `src/platform/`; `NotificationService` bản web chưa hỗ trợ (MVP không dùng) |
| T-108 | Thiết lập kiểm thử: unit, integration với Testcontainers (PostgreSQL, RustFS), công cụ E2E | Must | Đang làm | Backend Testcontainers PostgreSQL và RustFS, frontend Vitest + Testing Library đã chạy trong CI; công cụ E2E chưa chọn |
| T-109 | CI/CD GitHub Actions: test backend, build frontend, đẩy image backend lên Docker Hub | Must | Xong | D-011; job backend và frontend xanh trên GitHub ở lần push đầu (chủ dự án xác nhận); job đẩy image tạm tắt (D-013) |
| T-110 | Bật lại job đẩy image backend lên Docker Hub khi release v1 | Must | Chờ | D-013; chờ có chức năng ổn định. Cần biến `DOCKERHUB_USERNAME`, secret `DOCKERHUB_TOKEN` |

### E1 · Tài khoản

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-111 | Đăng ký email + mật khẩu, xác thực email (API và giao diện) | Must | Xong | US-01, US-02, BR-01 |
| T-112 | Đăng nhập email + mật khẩu, khóa 15 phút sau 5 lần sai | Must | Xong | US-03 |
| T-113 | JWT access + refresh token xoay vòng theo family, cookie (web) / body (native), đăng xuất | Must | Xong | US-08, API mục 2 |
| T-114 | Đăng nhập Google bằng ID token | Must | Xong | US-04, BR-03, D-023; trường hợp email chưa xác thực chờ xác nhận Q-022 |
| T-115 | Quên và đặt lại mật khẩu, thu hồi mọi phiên | Must | Xong | US-05 |
| T-116 | Onboarding: username, tên hiển thị, ảnh đại diện 256×256 | Must | Đang làm | US-06, BR-02, D-025. Username, tên hiển thị (API và giao diện), `PROFILE_INCOMPLETE` xong; ảnh đại diện làm sau khi có object storage (T-131) |
| T-117 | Đổi ngôn ngữ giao diện, lưu vào tài khoản | Must | Xong | US-07, D-023; trang cài đặt |
| T-118 | Xóa tài khoản và toàn bộ dữ liệu | Should | Chưa làm | US-09, D-026 |
| T-119 | Giới hạn tần suất (Bucket4j, trong bộ nhớ) | Must | Xong | API mục 6; giới hạn theo IP dùng địa chỉ kết nối, khi có reverse proxy cần cấu hình forwarded header (T-164) |

### E2 · Bạn bè

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-121 | Mã mời, link, QR, tạo mã mới | Must / Should | Xong | US-10 (Must), US-14 (Should), BR-04 |
| T-122 | Trang mời và kết bạn, giới hạn 15 có khóa theo thứ tự id | Must | Xong | US-11, BR-05, BR-06 |
| T-123 | Danh sách bạn bè (x/15) và hủy kết bạn | Must | Xong | US-12, US-13, BR-07 |

### E3 · Soạn và đăng khoảnh khắc

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-131 | API tạo khoảnh khắc, cấp presigned URL, `complete`, `retry` | Must | Xong | API mục 3.4, 5; object storage qua AWS SDK v2, test với RustFS thật |
| T-132 | Media worker: lấy việc từ `media_jobs`, ffmpeg cho ảnh và video, thumbnail, thử lại 3 lần | Must | Xong | ADR 0003, DB mục 6, D-033; 22 test với ffmpeg, PostgreSQL, RustFS thật; có cả việc `DELETE_OBJECTS` |
| T-133 | Chụp ảnh và quay video 5 giây bằng camera | Must | Chưa làm | US-20, US-21 |
| T-134 | Tải ảnh/video từ máy, chọn đoạn 5 giây, nhãn "tải lên" | Must | Chưa làm | US-22, BR-09, BR-10 |
| T-135 | Trình soạn lớp trang trí: caption, sticker, biểu ngữ, đếm theo grapheme | Must | Chưa làm | US-23, US-24, BR-11, ADR 0002 |
| T-136 | Xuất bản ghép từ canvas, tải lên với ảnh xem trước làm mờ (không thanh tiến trình), thử lại khi mất mạng | Must | Chưa làm | US-28 (D-005) |
| T-137 | Chọn chế độ chia sẻ/lưu giữ và người nhận | Must | Chưa làm | US-26, US-27, BR-12 |

### E4 · Xem và tương tác

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-141 | `MomentAccessPolicy` dùng chung và API chi tiết khoảnh khắc | Must | Xong | BR-13, BR-19, BR-20; 12 test (chủ, người nhận, không phải người nhận, người lạ, hủy kết bạn, mốc 24 giờ, lưu giữ). `myReaction` và `reactionCount` điền ở T-143 |
| T-142 | Feed API và giao diện vuốt dọc, đồng hồ "còn X giờ" | Must | Xong | US-30, US-31, D-035; 10 test backend, 10 test giao diện. Nút chụp ở trạng thái feed trống chờ T-133; vẽ đủ lớp trang trí trên video chờ T-135 (tạm hiện caption); thanh reaction ở T-143 |
| T-143 | Reaction: thả, đổi, gỡ; chủ xem ai thả gì | Must | Xong | US-32, US-33, BR-21 → BR-24, D-028, D-036; 10 test backend, 6 test giao diện; gói `reaction` hỏi quyền xem qua `MomentAccessPolicy` |
| T-144 | Chuyển sang lưu giữ và xóa khoảnh khắc | Must | Chưa làm | US-34, US-35, BR-16, BR-18 |

### E5 · Kho lưu giữ

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-151 | API kho và giao diện hai tab "Của tôi" / "Được gửi", chi tiết | Must | Chưa làm | US-40 → US-42 |

### E6 · Ứng dụng và vận hành

| Mã | Việc | Ưu tiên | Trạng thái | Ghi chú |
| --- | --- | --- | --- | --- |
| T-161 | Job định kỳ: hết hạn, dọn nháp, dọn token, dọn hàng đợi | Must | Chưa làm | DB mục 9, D-029 |
| T-162 | PWA: manifest, icon, service worker chỉ cache giao diện tĩnh | Must | Chưa làm | US-50 |
| T-163 | Script seed tài khoản demo | Must | Chưa làm | US-51, D-027 |
| T-164 | Docker Compose production, Cloudflare R2, backup PostgreSQL hằng ngày | Must | Chưa làm | US-52; phục vụ frontend và TLS chờ Q-021 |
| T-165 | E2E luồng đăng ký → kết bạn → đăng → reaction | Must | Chưa làm | Đặc tả mục 5 |
| T-166 | Triển khai lên VPS | Must | Chưa làm | Chặn bởi T-012 |

## Giai đoạn 2–8

Chỉ chia nhỏ khi bắt đầu giai đoạn đó. Phạm vi từng giai đoạn ở mục "Lộ trình" của [kế hoạch tổng quan](../00-tong-quan/ke-hoach-tong-quan.md).

| Mã | Giai đoạn | Trạng thái |
| --- | --- | --- |
| T-200 | 2 · Kết nối: chat 1-1 realtime, đáp bằng khoảnh khắc, thông báo đẩy web | Chưa làm |
| T-300 | 3 · Ký ức: kho polaroid, yêu thích, ngày này năm trước, photobooth | Chưa làm |
| T-400 | 4 · Thời gian: hộp thời gian, tổng kết cuối năm | Chưa làm |
| T-500 | 5 · Mở rộng desktop: widget Document PiP, gắn nhạc Spotify, Tauri (tùy) | Chưa làm |
| T-600 | 6 · Premium: giới hạn theo gói, bộ trang trí riêng, thanh toán thử | Chưa làm |
| T-700 | 7 · Android: spike trên máy thật, đóng gói Capacitor | Chưa làm |
| T-800 | 8 · iOS và widget màn hình chính | Chưa làm |
