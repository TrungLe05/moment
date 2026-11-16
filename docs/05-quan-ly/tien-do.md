# Tiến độ

Cập nhật: 16/11/2026 · Liên quan: [Nhiệm vụ](nhiem-vu.md), [Quyết định và câu hỏi mở](quyet-dinh.md)

## Trạng thái hiện tại

Dự án đang ở giai đoạn 0 (chuẩn bị), đã bắt đầu phần nền tảng của giai đoạn 1. Bộ tài liệu thiết kế đã có bản nháp 1. Đã có hạ tầng dev (Compose: PostgreSQL, RustFS, Mailpit), `.env.example`, khung backend Spring Boot và frontend Next.js tối thiểu, CI GitHub Actions (job đẩy image lên Docker Hub tạm tắt đến release v1). Repo đã push lần đầu lên `main`, CI xanh; quy ước nhánh `main` / `develop` / nhánh làm việc / `release/*` ở D-012.

Đặc tả, database, API đã lên bản chốt 1 (T-014). E1 tài khoản đã có API và giao diện (đăng ký, xác thực email, đăng nhập email/Google, phiên xoay vòng, quên mật khẩu, chọn username, đổi ngôn ngữ, đăng xuất); còn ảnh đại diện (T-116) và xóa tài khoản (T-118). Code nằm trên 4 PR nối tiếp nhau, chưa merge vào `develop`. Còn mở: tên miền, VPS (Q-010), email production (Q-011), cách phục vụ frontend ở production (Q-021); cả ba chỉ chặn bước triển khai.

Đã có tạo khoảnh khắc (T-131) và media worker xử lý ảnh, video bằng ffmpeg (T-132, profile `worker`).

**Việc kế tiếp:** quyền xem và chi tiết khoảnh khắc (T-141), feed (T-142), ảnh đại diện (T-116).

## Theo giai đoạn

| Giai đoạn | Trạng thái | Việc xong / tổng | Ghi chú |
| --- | --- | --- | --- |
| 0 · Chuẩn bị | Đang làm | 12 / 16 | Còn font (T-010), glassmorphism (T-011), VPS (T-012), ước lượng (T-013) |
| 1 · MVP | Đang làm | 21 / 40 | Nền tảng, E1, E2 gần xong; tạo và xử lý khoảnh khắc xong (T-131, T-132); T-116 còn ảnh đại diện, T-118 chưa làm; T-110 chờ release v1. Chưa ước lượng thời gian (T-013) |
| 2 → 8 | Chưa bắt đầu | — | Chia nhỏ khi tới giai đoạn |

Cách đếm: số việc trạng thái "Xong" trên tổng số việc không ở trạng thái "Bỏ" trong [nhiệm vụ](nhiem-vu.md). Cập nhật bảng này mỗi khi đổi trạng thái việc.

## Nhật ký phiên làm việc

Mới nhất ở trên. Mỗi phiên ghi: đã làm gì, quyết định gì, còn dở gì, việc kế tiếp. Ghi trung thực: việc chưa kiểm chứng thì nói chưa kiểm chứng.

### 16/11/2026 · T-132 media worker

- Viết test trước (22 test, ffmpeg + PostgreSQL + RustFS thật) rồi code. Ảnh: `DISPLAY` 1440 px và `THUMBNAIL` 480 px từ `COMPOSED`, không phóng to. Video: H.264 + AAC, cạnh ngắn 720, đúng 5 giây từ `trimStartMs`, thumbnail từ khung đầu; video gốc xóa qua việc `DELETE_OBJECTS`.
- Hàng đợi: lấy việc bằng `FOR UPDATE SKIP LOCKED` (có test hai worker), lỗi tạm thời thử lại sau 15 giây rồi 60 giây, lần thứ 3 lỗi thì `FAILED`; lỗi của file đánh dấu `FAILED` ngay (D-033). Tự review PR #10: sửa WebM không ghi thời lượng (video quay trên trình duyệt) bị coi là hỏng, chỉ coi là lỗi của file khi ffprobe đọc không được, giới hạn 50 megapixel, chờ ffmpeg dừng hẳn khi quá giờ, test kiểm tra đúng điểm bắt đầu cắt. Khoảnh khắc bị xóa hay hết hạn giữa chừng thì không sống lại, file vừa tạo bị dọn.
- Bảo mật ffmpeg: ép demuxer theo Content-Type, chỉ cho giao thức `file`, bỏ metadata, có timeout.
- Profile: mặc định `api`, `worker` bật lịch hỏi hàng đợi (T-102); Dockerfile có stage `worker` cài ffmpeg; CI cài ffmpeg bằng apt (D-034). Đã chạy thử jar với profile `worker` trên Compose: health UP, việc chèn tay được xử lý trong vòng 3 giây. 116 test backend pass.
- Máy dev đã cài ffmpeg 9.0.2 qua scoop.
- Chưa làm: dọn việc `RUNNING` bị kẹt (T-161), compose production chạy container worker (T-164), giao diện hiện `failureReason` (T-137).

### 16/11/2026 · T-131 tạo khoảnh khắc

- Tự review PR #8 bằng code-review: sửa tạo mã mời lần đầu bị đua (ON CONFLICT DO NOTHING), người mời bị xóa trả INVITE_INVALID, bỏ tải lại thừa; đã merge #8. PR #7 để chủ dự án merge bypass (GitGuardian bắt nhầm).
- Mô hình hóa code bằng graphify (phần code): 1.321 nút, 3.464 cạnh, 82 cụm; kết quả ở `graphify-out/` (không commit).
- Viết test trước (20 test) rồi code: `POST /moments`, `complete`, `retry`; presigned PUT có ký Content-Type và dung lượng; kiểm tra file bằng HEAD ngoài transaction; chuyển trạng thái có điều kiện; tạo việc `PROCESS_MOMENT` cùng transaction. Testcontainers thêm RustFS thật (khóa sinh ngẫu nhiên). 94 test backend pass.
- Chưa làm: giao diện soạn và đăng (T-133 → T-137), worker xử lý (T-132).

### 16/11/2026 · E2 bạn bè

- PR #2 → #6 đã merge vào nhánh `docs/T-014-finalize-specs` thay vì `develop` (GitHub tự đổi base); mở PR #7 đưa vào `develop`.
- Chốt link mời dạng `/invite?code=` (D-032).
- Backend: mã mời 8 ký tự, tạo mới vô hiệu mã cũ (BR-04); kết bạn ngay (BR-05); khóa hai dòng users theo thứ tự uuid của PostgreSQL rồi đếm (BR-06), có test hai lời mời chạy song song; danh sách bạn một truy vấn user (không N+1); hủy kết bạn (BR-07). Tách `UserResponseFactory` để bỏ phụ thuộc vòng UserService ↔ FriendService. 73 test backend pass.
- Frontend: trang Bạn bè (link, QR, chia sẻ, tạo link mới có xác nhận, danh sách x/15, hủy có xác nhận), trang mời công khai, giữ `next` qua đăng nhập, đăng ký, onboarding. 23 test frontend pass.
- Chạy thử trên trình duyệt: mở link mời khi chưa đăng nhập → đăng nhập → quay lại trang mời → kết bạn → danh sách 1/15 → hủy → 0/15.
- Chưa làm: quay lại trang mời sau bước xác thực email (link email không mang `next`).


### 16/11/2026 · Sửa cảnh báo GitGuardian về giá trị giả trong test

- GitGuardian báo 5 chỗ ở PR #3 và 2 chỗ ở PR #4: khóa JWT và mật khẩu viết cứng trong test, không có secret thật.
- Theo quy ước nhánh (D-012), tách nhánh fix từ từng nhánh lỗi: PR #5 vào nhánh của #3 (khóa JWT lấy từ `${random.value}`, mật khẩu test sinh lúc chạy), PR #6 vào nhánh của #4 (mật khẩu test sinh lúc chạy). 57 test backend, 16 test frontend pass.
- Commit gốc trong #3, #4 vẫn chứa chuỗi cũ nên check GitGuardian của hai PR đó còn đỏ cho tới khi chủ dự án xử lý cảnh báo trên GitGuardian (hoặc viết lại lịch sử nhánh).
- Thứ tự merge: #1 → #2 → #5 vào nhánh #3 → #3 → #6 vào nhánh #4 → #4.

### 16/11/2026 · Nền tảng backend, API và giao diện tài khoản E1

- PR #2: migration `V1__init.sql` (T-105), hạ tầng chung `common` (T-106). PR #3: API tài khoản (T-111 → T-115, T-117, T-119, một phần T-116), 57 test backend pass. PR #4: nền tảng frontend (T-103, T-107) và giao diện tài khoản, 16 test frontend pass. Bốn PR nối tiếp nhau (#2 trên #1, #3 trên #2, #4 trên #3).
- Chạy thử thật: đăng ký trên trình duyệt → email tới Mailpit → mở link xác thực → đăng nhập → chọn username → tải lại trang vẫn giữ phiên (cookie refresh qua CORS) → đổi ngôn ngữ → đăng xuất. Trên máy dev cổng 8080 bị chương trình khác dùng nên đã chạy backend ở 8081.
- Ghi Q-022 (Google trùng email chưa xác thực), đã làm tạm theo phương án an toàn. Ghi D-031 (dependency đã duyệt).
- Commit cập nhật tài liệu API của PR #3 tách riêng khỏi commit code (cùng PR), lệch quy ước "cùng commit".
- Còn dở: ảnh đại diện (T-116), xóa tài khoản (T-118), công cụ E2E (T-108).

### 16/11/2026 · Chốt câu hỏi mở, nâng tài liệu lên bản chốt 1

- Chủ dự án chốt Q-001 → Q-009 và Q-012 → Q-019 theo đề xuất (D-014 → D-030); ADR 0003 chuyển "Đã chấp nhận".
- Sửa đặc tả (bỏ nhãn giả định, quy tắc tài khoản demo), database (11 bảng, bỏ `note`, job dọn `FAILED`), API (`locale` cho `/auth/google`, `uploadId` ký, `auth_time`, chuẩn hóa emoji, bỏ `note`), kế hoạch tổng quan, định hướng giao diện. T-007, T-008, T-009, T-014 xong.
- Còn mở: Q-010, Q-011, Q-021 (chỉ chặn triển khai).

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
