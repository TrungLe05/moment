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

### Giả định của đặc tả MVP

Nguồn: [Đặc tả MVP, mục 7](../01-san-pham/dac-ta-mvp.md#7-giả-định-cần-xác-nhận).

| Mã | Câu hỏi | Đề xuất | Phương án khác | Chặn |
| --- | --- | --- | --- | --- |
| Q-001 | (A1) MVP có kho lưu giữ đơn giản không? | Có (E5) | Bỏ E5, đợi kho polaroid ở giai đoạn 3 | T-151 |
| Q-002 | (A2) Mở link mời và bấm "Kết bạn" là thành bạn ngay? | Có, không cần duyệt | Gửi lời mời, chủ link duyệt | T-122 |
| Q-003 | (A3) Có cho chuyển lưu giữ về chia sẻ không? | Không | Cho chuyển lại khi còn trong 24 giờ | T-144 |
| Q-004 | (A4) Có cho sửa khoảnh khắc sau khi đăng không? | Không | Cho sửa caption và ghi chú | T-131 |
| Q-005 | (A5) Reaction: một reaction/người/khoảnh khắc, bộ 6 emoji cố định? | Có | Nhiều reaction, emoji tự do | T-143 |
| Q-006 | (A6) Đăng nhập Google trùng email đã xác thực thì tự liên kết? | Có, khi `email_verified = true` | Báo lỗi, bắt đăng nhập mật khẩu rồi liên kết | T-114 |
| Q-007 | (A7) Hủy rồi kết bạn lại thì thấy lại khoảnh khắc cũ? | Có | Mất vĩnh viễn | T-123 |
| Q-008 | (A8) Khoảnh khắc của chính mình hiện trong feed? | Có | Chỉ hiện của bạn bè | T-142 |

### Kỹ thuật và vận hành

| Mã | Câu hỏi | Đề xuất | Chặn |
| --- | --- | --- | --- |
| Q-009 | Chấp nhận ADR 0003 (hàng đợi bằng bảng PostgreSQL)? Hiện ở trạng thái "Đề xuất" | Chấp nhận | T-009, T-102, T-132 |
| Q-010 | Tên miền và nhà cung cấp VPS? | Chưa có đề xuất | T-012, T-166 |
| Q-011 | Dùng dịch vụ gì để gửi email (xác thực, đặt lại mật khẩu) khi triển khai? Phần dev đã chốt Mailpit (D-008); backend gửi qua SMTP chuẩn nên production chỉ đổi cấu hình | Chưa có đề xuất; cần chốt trước T-166 | T-164, T-166 |
| Q-021 | Production phục vụ frontend tĩnh, TLS và chuyển tiếp `/api` bằng gì? Tài liệu chưa ghi | Chưa có đề xuất | T-164 |

### Điểm lệch giữa các tài liệu

Claude phát hiện khi rà tài liệu ngày 16/11/2026. Chưa sửa tài liệu nào; chờ chủ dự án chọn hướng.

| Mã | Điểm lệch | Đề xuất của Claude | Chặn |
| --- | --- | --- | --- |
| Q-012 | US-07 nói ngôn ngữ mặc định theo trình duyệt (khác vi/en → en), nhưng `users.locale` mặc định `'vi'` và `POST /auth/google` không nhận `locale`, nên người dùng Google mới luôn là `vi` | Thêm `locale` (tùy chọn) vào body `/auth/google`; giữ default DB chỉ là giá trị dự phòng | T-114, T-117 |
| Q-013 | Thiết kế database ghi "10 bảng" nhưng định nghĩa 11 bảng (tính cả `media_jobs`) | Sửa câu mở đầu thành 11 bảng | T-014 |
| Q-014 | `POST /me/avatar/upload-url` trả `uploadId`, nhưng database không có chỗ lưu upload đang chờ để `complete` đối chiếu | Mã hóa `uploadId` thành chuỗi ký (chứa userId và object key, có hạn), không cần bảng mới | T-116 |
| Q-015 | `DELETE /me` cho tài khoản chỉ có Google yêu cầu "đăng nhập lại trong 5 phút gần nhất", nhưng chưa có chỗ lưu thời điểm xác thực | Thêm claim `auth_time` vào access token khi đăng nhập/đăng nhập Google | T-118 |
| Q-016 | Tài khoản demo: đặc tả chỉ cấm đổi mật khẩu và xóa tài khoản. Chưa rõ người xem demo có được đổi username, tạo mã mời mới, hủy kết bạn, xóa khoảnh khắc của dữ liệu mẫu không, và bao lâu dựng lại dữ liệu một lần | Cho thao tác bình thường, chỉ khóa những gì đặc tả đã ghi; script seed chạy lại hằng ngày | T-163 |
| Q-017 | Emoji `❤️` gồm 2 code point (có U+FE0F). Client gửi `❤` không có U+FE0F sẽ bị `CHECK` của bảng `reactions` từ chối | Server chuẩn hóa emoji trước khi kiểm tra; ghi rõ trong API mục 3.5 | T-143 |
| Q-018 | Khoảnh khắc lưu giữ bị `FAILED` không bao giờ hết hạn và không có job dọn, nên file gốc nằm lại mãi nếu chủ không thử lại hoặc xóa | Job dọn khoảnh khắc `FAILED` quá 7 ngày (mọi chế độ), hoặc hiển thị nhắc chủ trong kho | T-161 |
| Q-019 | Đặc tả đã bỏ ghi chú dài (D-004), nhưng các tài liệu khác vẫn còn: cột `moments.note` và ràng buộc `moments_note_len` (database), trường `note` trong `POST /moments` và đối tượng trả về (API), dòng "ghi chú dài bên dưới" và "ghi chú" trong phạm vi MVP (kế hoạch tổng quan), quy tắc "Ghi chú dài nằm dưới ảnh" (định hướng giao diện) | Bỏ `note` khỏi database và API ở MVP; ghi chú ở kế hoạch và giao diện chuyển thành "để sau MVP". Cần chủ dự án đồng ý sửa 4 file này | T-014, T-105, T-131 |

## Đã chốt

### Quyết định lớn (ADR)

| Mã | Quyết định | Ngày | ADR |
| --- | --- | --- | --- |
| D-001 | Làm web desktop trước, code sẵn sàng đóng gói Capacitor | 15/11/2026 | [0001](../04-quyet-dinh/0001-web-desktop-truoc-san-sang-capacitor.md) |
| D-002 | Lưu ảnh gốc và lớp trang trí JSON tách riêng | 15/11/2026 | [0002](../04-quyet-dinh/0002-luu-anh-goc-va-lop-trang-tri-json.md) |

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
