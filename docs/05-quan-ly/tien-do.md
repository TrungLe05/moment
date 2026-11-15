# Tiến độ

Cập nhật: 16/11/2026 · Liên quan: [Nhiệm vụ](nhiem-vu.md), [Quyết định và câu hỏi mở](quyet-dinh.md)

## Trạng thái hiện tại

Dự án đang ở giai đoạn 0 (chuẩn bị), đã bắt đầu phần nền tảng của giai đoạn 1. Bộ tài liệu thiết kế đã có bản nháp 1. Đã có hạ tầng dev (Compose: PostgreSQL, RustFS, Mailpit), `.env.example`, khung backend Spring Boot và frontend Next.js tối thiểu, CI GitHub Actions (job đẩy image lên Docker Hub tạm tắt đến release v1). Repo đã push lần đầu lên `main`, CI xanh; quy ước nhánh `main` / `develop` / nhánh làm việc / `release/*` ở D-012.

Đang chặn: 8 giả định của đặc tả (Q-001 → Q-008), ADR 0003 (Q-009, chặn profile `api`/`worker`), 8 điểm lệch giữa tài liệu (Q-012 → Q-019). Tên miền, VPS (Q-010), email production (Q-011) và cách phục vụ frontend ở production (Q-021) chỉ chặn bước triển khai.

**Việc kế tiếp:** chủ dự án tạo nhánh `develop`; trả lời Q-001 → Q-019 và Q-021; Claude cập nhật tài liệu lên bản chốt (T-014), làm tiếp T-103, T-106.

## Theo giai đoạn

| Giai đoạn | Trạng thái | Việc xong / tổng | Ghi chú |
| --- | --- | --- | --- |
| 0 · Chuẩn bị | Đang làm | 8 / 16 | Còn chốt giả định, sửa điểm lệch, font, glassmorphism, ước lượng |
| 1 · MVP | Đang làm | 3 / 40 | T-101, T-104, T-109 xong; T-102 chờ Q-009; T-110 chờ release v1. Chưa ước lượng thời gian (T-013) |
| 2 → 8 | Chưa bắt đầu | — | Chia nhỏ khi tới giai đoạn |

Cách đếm: số việc trạng thái "Xong" trên tổng số việc không ở trạng thái "Bỏ" trong [nhiệm vụ](nhiem-vu.md). Cập nhật bảng này mỗi khi đổi trạng thái việc.

## Nhật ký phiên làm việc

Mới nhất ở trên. Mỗi phiên ghi: đã làm gì, quyết định gì, còn dở gì, việc kế tiếp. Ghi trung thực: việc chưa kiểm chứng thì nói chưa kiểm chứng.

### 16/11/2026 · Tạm tắt đẩy image, chốt quy ước git

- Chủ dự án đã push lần đầu lên `main`; job backend và frontend xanh, job đẩy image lên Docker Hub lỗi. Theo yêu cầu, comment job đó lại đến khi release v1 (D-013, T-110); T-101 và T-109 chuyển "Xong".
- CI chạy thêm khi push lên `develop` và `release/**`, theo mô hình nhánh mới. Workflow qua actionlint; bản sửa này chưa chạy trên GitHub.
- Ghi quy ước git vào `CLAUDE.md` (D-012): mô hình nhánh, commit message, tiêu đề và mô tả PR tiếng Anh, commit và mô tả PR không có trailer Claude Code (kể cả `Co-Authored-By`), push bằng `git config` sẵn có; comment giải thích code viết tiếng Việt.
- Tên nhánh làm việc `<type>/<T-xxx>-<mo-ta-ngan>` là Claude đề xuất, chủ dự án có thể đổi.
- Việc kế tiếp: chủ dự án tạo nhánh `develop` và commit bản sửa này; chốt Q-009.

### 16/11/2026 · Dựng hạ tầng dev, khung repo tối thiểu, CI/CD

- Chủ dự án chốt: Java 25 + Spring Boot 4.1 + Node 24 + Next.js 16, gói `com.dev.moment` (D-007, đóng Q-020); Mailpit cho email dev (D-008, Q-011 thu hẹp còn production); image đẩy lên Docker Hub (D-011).
- Phát hiện MinIO đã ngừng phát hành image trên Docker Hub; chủ dự án chọn RustFS thay cho dev và Testcontainers (D-010). Đã sửa README, ARCHITECTURE, đặc tả, kế hoạch tổng quan, CLAUDE.md.
- Tạo `.gitignore`, `.gitattributes` (LF, `.cmd` CRLF), `.env.example`, `compose.yaml` (D-009). Đã chạy: postgres, rustfs, mailpit healthy, `storage-init` tạo bucket `moment`, ghi/đọc/xóa object qua S3 được, truy cập ẩn danh bị 403 (T-104 xong).
- Khung backend từ Spring Initializr (Boot 4.1.2): `application.yml` đọc `.env`, `SecurityConfig` tạm (mở health, còn lại 401, không tạo user mặc định), `Dockerfile` nhiều tầng chạy user không phải root. `./mvnw verify`: 3 test pass với PostgreSQL thật qua Testcontainers. Image chạy được, health `UP`, `/api/v1/me` trả 401.
- Khung frontend từ create-next-app 16.4.6: `output: 'export'` (bỏ `cacheComponents`, `partialPrefetching` của mẫu vì không chạy với export), bỏ font Geist (chọn ở T-010), `src/i18n/messages.ts` vi/en. Lint, typecheck, build pass. `npm audit` báo 7 lỗ hổng mức moderate trong dependency dev của ESLint, chưa xử lý.
- CI/CD `.github/workflows/ci.yml` (T-109): qua actionlint, **chưa chạy trên GitHub**.
- Còn dở: profile `api`/`worker` và container worker có ffmpeg (chờ Q-009); shadcn/ui, i18n đầy đủ (T-103); compose production (T-164).
- Việc kế tiếp: chủ dự án `git init`, tạo `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`, push lần đầu và xem CI.

### 16/11/2026 · Sửa đặc tả, viết ARCHITECTURE.md, bổ sung quy ước code

- Đặc tả MVP: bỏ US-25 (ghi chú dài) và US-36 ("xem thêm", chỉ phục vụ ghi chú); bỏ các chỗ nhắc ghi chú trong thuật ngữ, BR-11, BR-17, US-42, A4; thêm "ghi chú dài dưới ảnh" vào ngoài phạm vi (D-004).
- Đặc tả MVP: US-28 đổi từ thanh tiến trình sang ảnh xem trước bị làm mờ, rõ nét khi sẵn sàng (D-005).
- Viết `ARCHITECTURE.md` ở gốc repo (T-015). Cấu trúc repo, gói backend, thư mục frontend, service Compose đang ghi là [Đề xuất].
- Bổ sung quy tắc làm việc và quy ước backend, frontend, database, kiểm thử, git vào `CLAUDE.md` (T-016, D-006).
- Thêm câu hỏi Q-019 (ghi chú còn trong database, API, kế hoạch, giao diện), Q-020 (phiên bản, tên gói), Q-021 (phục vụ frontend và TLS ở production).
- Chưa sửa thiết kế database, danh sách API, kế hoạch tổng quan, định hướng giao diện; chưa thêm `ARCHITECTURE.md` và `05-quan-ly` vào mục lục.
- Việc kế tiếp: chốt Q-001 → Q-021.

### 16/11/2026 · Lập bộ file quản lý dự án

- Rà toàn bộ 11 file tài liệu hiện có.
- Tạo `docs/05-quan-ly/nhiem-vu.md`, `tien-do.md`, `quyet-dinh.md` và `CLAUDE.md` ở gốc (T-006).
- Chia giai đoạn 1 thành 38 việc: 8 việc nền tảng và 30 việc theo 6 epic của đặc tả.
- Ghi 18 câu hỏi mở: 8 giả định của đặc tả, 3 câu kỹ thuật/vận hành, 7 điểm lệch giữa tài liệu mới phát hiện. Chưa sửa tài liệu cũ nào.
- Chưa thêm mục `05-quan-ly` vào mục lục `docs/README.md` và cây thư mục trong `README.md` (chờ chủ dự án đồng ý sửa hai file đó).
- Việc kế tiếp: chốt Q-001 → Q-018.

### 16/11/2026 · Đặc tả, database, API (ghi lại từ ngày trong tài liệu)

- Đặc tả MVP, thiết kế database, danh sách API đạt bản nháp 1. ADR 0003 ở trạng thái "Đề xuất".

### 15/11/2026 · Kế hoạch và bản mẫu (ghi lại từ ngày trong tài liệu)

- Kế hoạch tổng quan đã chốt; bản mẫu giao diện 5 màn hình đạt bố cục. ADR 0001 và 0002 được chấp nhận.
