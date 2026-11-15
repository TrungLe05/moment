# Quyết định và câu hỏi mở

Cập nhật: 16/11/2026 · Liên quan: [Nhiệm vụ](nhiem-vu.md), [Tiến độ](tien-do.md), [ADR](../04-quyet-dinh/README.md)

File này có hai phần: câu hỏi đang chờ chốt (`Q-xxx`) và sổ quyết định nhỏ (`D-xxx`). Mã không đánh lại số.

## Quy ước

- **Quyết định lớn** (kiến trúc, công nghệ, thứ khó đảo ngược) ghi thành ADR trong [`04-quyet-dinh/`](../04-quyet-dinh/README.md). Sổ ở đây chỉ ghi một dòng trỏ tới ADR.
- **Quyết định nhỏ** (quy tắc nghiệp vụ, giới hạn, quy ước) ghi vào sổ `D-xxx`, kèm tài liệu nguồn đã được cập nhật.
- **Câu hỏi mở** ghi vào `Q-xxx`. Khi chủ dự án trả lời: chuyển câu hỏi sang mục "Đã chốt" kèm mã `D-xxx` mới, sửa tài liệu nguồn (đặc tả, database, API) cho khớp, rồi mở các việc đang chờ trong [nhiệm vụ](nhiem-vu.md).
- Claude không tự chốt câu hỏi mở. Khi tài liệu và code mâu thuẫn, ghi thành câu hỏi mới và dừng lại hỏi.

## Câu hỏi đang chờ chốt

Cột "Đề xuất" là phương án đang ghi trong tài liệu (hoặc Claude đề xuất nếu tài liệu chưa có). Trả lời "theo đề xuất" là đủ để chốt.

### Kỹ thuật và vận hành

| Mã | Câu hỏi | Đề xuất | Chặn |
| --- | --- | --- | --- |
| Q-010 | Tên miền và nhà cung cấp VPS? | Chưa có đề xuất | T-012, T-166 |
| Q-011 | Dùng dịch vụ gì để gửi email (xác thực, đặt lại mật khẩu) khi triển khai? Phần dev đã chốt Mailpit (D-008); backend gửi qua SMTP chuẩn nên production chỉ đổi cấu hình | Chưa có đề xuất; cần chốt trước T-166 | T-164, T-166 |
| Q-021 | Production phục vụ frontend tĩnh, TLS và chuyển tiếp `/api` bằng gì? Tài liệu chưa ghi | Chưa có đề xuất | T-164 |
| Q-022 | BR-03 không nói trường hợp đăng nhập Google trùng email của một tài khoản email **chưa xác thực**. Nếu liên kết thẳng, người đăng ký giả email đó (biết mật khẩu) sẽ vào được tài khoản sau khi chủ thật dùng Google | Đã làm tạm theo đề xuất: xác thực tài khoản đó và bỏ mật khẩu cũ (`UserService.claimUnverifiedAccountByGoogle`). Phương án khác: báo lỗi, bắt xác thực email trước | T-114 (đã làm, cần xác nhận) |

## Đã chốt

### Quyết định lớn (ADR)

| Mã | Quyết định | Ngày | ADR |
| --- | --- | --- | --- |
| D-001 | Làm web desktop trước, code sẵn sàng đóng gói Capacitor | 15/11/2026 | [0001](../04-quyet-dinh/0001-web-desktop-truoc-san-sang-capacitor.md) |
| D-002 | Lưu ảnh gốc và lớp trang trí JSON tách riêng | 15/11/2026 | [0002](../04-quyet-dinh/0002-luu-anh-goc-va-lop-trang-tri-json.md) |
| D-022 | (Q-009) Hàng đợi việc nền bằng bảng PostgreSQL `media_jobs`, worker là profile `worker` | 16/11/2026 | [0003](../04-quyet-dinh/0003-hang-doi-bang-bang-postgresql.md) |

Các quyết định sản phẩm đã chốt trước ngày 16/11/2026 (nền tảng, 24 giờ, video 5 giây, tối đa 15 bạn, chat 1-1 để sau...) nằm ở bảng "Các quyết định đã chốt" của [kế hoạch tổng quan](../00-tong-quan/ke-hoach-tong-quan.md#các-quyết-định-đã-chốt); không chép lại ở đây để tránh hai nguồn lệch nhau.

### Quyết định nhỏ

| Mã | Quyết định | Ngày | Tài liệu nguồn |
| --- | --- | --- | --- |
| D-003 | Quản lý dự án bằng ba file trong `docs/05-quan-ly/` (nhiệm vụ, tiến độ, quyết định) và `CLAUDE.md` ở gốc repo; mã `T-`, `Q-`, `D-` cố định | 16/11/2026 | File này, [CLAUDE.md](../../CLAUDE.md) |
| D-004 | Bỏ US-25 (ghi chú dài dưới ảnh) khỏi MVP; bỏ luôn US-36 ("xem thêm" cho ghi chú) vì chỉ phục vụ ghi chú. Ghi chú dài chuyển vào "Ngoài phạm vi MVP" | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md); các tài liệu khác chờ Q-019 |
| D-005 | US-28: trong lúc tải lên và xử lý, khoảnh khắc hiện bằng ảnh xem trước bị làm mờ, rõ nét khi sẵn sàng; không dùng thanh tiến trình | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md), [ARCHITECTURE.md](../../ARCHITECTURE.md) |
| D-006 | Quy tắc làm việc và quy ước code backend, frontend, database, kiểm thử, git ghi trong `CLAUDE.md`; trong đó access token chỉ giữ trong bộ nhớ, thời gian lấy từ `Clock` inject | 16/11/2026 | [CLAUDE.md](../../CLAUDE.md) |
| D-007 | (Q-020) Java 25 LTS, Spring Boot 4.1.x, Maven (wrapper); Node.js 24 LTS, Next.js 16. Gói gốc backend `com.dev.moment` (groupId `com.dev`, artifactId `moment`) | 16/11/2026 | [ARCHITECTURE.md](../../ARCHITECTURE.md), [README.md](../../README.md) |
| D-008 | (Q-011, phần dev) Email khi dev dùng Mailpit trong Compose (SMTP `:1025`, giao diện `:8025`); dịch vụ email production còn mở ở Q-011 | 16/11/2026 | [ARCHITECTURE.md](../../ARCHITECTURE.md), `compose.yaml` |
| D-009 | Compose dev đặt ở `compose.yaml` gốc repo, chỉ chạy hạ tầng (PostgreSQL, RustFS, Mailpit), cổng chỉ mở trên `127.0.0.1`; backend và frontend chạy trên máy. Một file `.env` ở gốc dùng chung cho Compose và backend (mẫu `.env.example`). Compose production để ở `deploy/` (T-164) | 16/11/2026 | [ARCHITECTURE.md](../../ARCHITECTURE.md), [README.md](../../README.md) |
| D-010 | Object storage khi dev và trong Testcontainers dùng RustFS (`rustfs/rustfs`, chuẩn S3) thay MinIO, vì MinIO đã ngừng phát hành image `minio/minio`, `minio/mc` trên Docker Hub. Production vẫn là Cloudflare R2; code chỉ dùng giao thức S3 | 16/11/2026 | [ARCHITECTURE.md](../../ARCHITECTURE.md), [README.md](../../README.md), [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md), [Kế hoạch tổng quan](../00-tong-quan/ke-hoach-tong-quan.md), [CLAUDE.md](../../CLAUDE.md) |
| D-011 | CI/CD bằng GitHub Actions (`.github/workflows/ci.yml`): test backend, lint/kiểm tra kiểu/build frontend trên mỗi PR và push; push `main` hoặc tag `v*` thì đẩy image backend lên Docker Hub (`<DOCKERHUB_USERNAME>/moment-backend`); bước này đang tạm tắt theo D-013. Chưa tự triển khai lên VPS | 16/11/2026 | [ARCHITECTURE.md](../../ARCHITECTURE.md), [README.md](../../README.md) |
| D-012 | Quy ước git: `main` là nhánh chính; `develop` gom PR từ các nhánh làm việc tách từ `develop`; nhánh có lỗi thì tách nhánh sửa từ chính nó, PR vào lại nhánh đó rồi mới PR vào `develop`; code ổn định thì tách `release/<phiên bản>`. Commit message, tiêu đề và mô tả PR viết tiếng Anh; commit message và mô tả PR không có trailer/footer kiểu Claude Code (kể cả `Co-Authored-By`); push bằng `git config` sẵn có của chủ dự án; Claude chỉ commit/push/tạo PR khi được cho phép. Comment giải thích code viết tiếng Việt | 16/11/2026 | [CLAUDE.md](../../CLAUDE.md) |
| D-013 | Tạm tắt job đẩy image backend lên Docker Hub (comment trong `ci.yml`) vì chưa có chức năng ổn định; bật lại khi chuẩn bị release v1 (T-110). CI build và test vẫn chạy | 16/11/2026 | `.github/workflows/ci.yml`, [ARCHITECTURE.md](../../ARCHITECTURE.md), [README.md](../../README.md) |
| D-014 | (Q-001, A1) MVP có kho lưu giữ đơn giản (E5) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-015 | (Q-002, A2) Mở link mời và bấm "Kết bạn" là thành bạn ngay, không cần duyệt (BR-05) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-016 | (Q-003, A3) Không chuyển lưu giữ về chia sẻ (BR-16) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-017 | (Q-004, A4) Không sửa khoảnh khắc sau khi đăng (BR-17) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-018 | (Q-005, A5) Một reaction mỗi người trên một khoảnh khắc, bộ 6 emoji cố định (BR-21) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-019 | (Q-006, A6) Đăng nhập Google trùng email đã xác thực thì tự liên kết, chỉ khi `email_verified = true` (BR-03) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-020 | (Q-007, A7) Hủy rồi kết bạn lại thì thấy lại khoảnh khắc cũ (BR-07) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-021 | (Q-008, A8) Khoảnh khắc của chính mình hiện trong feed (US-30) | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-023 | (Q-012) `POST /auth/google` nhận thêm `locale` tùy chọn; default `'vi'` của `users.locale` chỉ là giá trị dự phòng | 16/11/2026 | [Danh sách API](../02-ky-thuat/danh-sach-api.md) |
| D-024 | (Q-013) Thiết kế database có 11 bảng (tính cả `media_jobs`) | 16/11/2026 | [Thiết kế database](../02-ky-thuat/thiet-ke-database.md) |
| D-025 | (Q-014) `uploadId` của ảnh đại diện là chuỗi ký bởi server (chứa userId, object key, hạn dùng), không cần bảng mới | 16/11/2026 | [Danh sách API](../02-ky-thuat/danh-sach-api.md) |
| D-026 | (Q-015) Access token có claim `auth_time` (thời điểm xác thực bằng mật khẩu hoặc Google) để kiểm tra "đăng nhập lại trong 5 phút" khi `DELETE /me` | 16/11/2026 | [Danh sách API](../02-ky-thuat/danh-sach-api.md) |
| D-027 | (Q-016) Tài khoản demo thao tác bình thường, chỉ khóa đổi mật khẩu và xóa tài khoản; script seed chạy lại hằng ngày | 16/11/2026 | [Đặc tả MVP](../01-san-pham/dac-ta-mvp.md) |
| D-028 | (Q-017) Server chuẩn hóa emoji trước khi kiểm tra (`❤` không có U+FE0F thành `❤️`) | 16/11/2026 | [Danh sách API](../02-ky-thuat/danh-sach-api.md) |
| D-029 | (Q-018) Job dọn khoảnh khắc `FAILED` tạo quá 7 ngày (mọi chế độ), kèm việc xóa file | 16/11/2026 | [Thiết kế database](../02-ky-thuat/thiet-ke-database.md) |
| D-030 | (Q-019) Bỏ `note` khỏi database và API ở MVP; ghi chú dài trong kế hoạch và định hướng giao diện chuyển thành "để sau MVP" | 16/11/2026 | [Thiết kế database](../02-ky-thuat/thiet-ke-database.md), [Danh sách API](../02-ky-thuat/danh-sach-api.md), [Kế hoạch tổng quan](../00-tong-quan/ke-hoach-tong-quan.md), [Định hướng giao diện](../03-giao-dien/dinh-huong-giao-dien.md) |
