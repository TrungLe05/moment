# Hướng dẫn cho Claude · dự án Khoảnh khắc (moment)

Mạng xã hội nhỏ cho nhóm bạn thân: chia sẻ khoảnh khắc 24 giờ, chọn lưu giữ những khoảnh khắc đáng nhớ. Dự án portfolio, một người phát triển. Backend Java Spring Boot + PostgreSQL, frontend Next.js export tĩnh, chạy web desktop trước. Chi tiết ở [README.md](README.md), [ARCHITECTURE.md](ARCHITECTURE.md) và [docs/](docs/README.md).

## Đầu mỗi phiên

1. Đọc mục "Trạng thái hiện tại" và mục nhật ký mới nhất trong [docs/05-quan-ly/tien-do.md](docs/05-quan-ly/tien-do.md).
2. Xem mục "Đang làm" trong [docs/05-quan-ly/nhiem-vu.md](docs/05-quan-ly/nhiem-vu.md).
3. Xem các câu hỏi còn mở trong [docs/05-quan-ly/quyet-dinh.md](docs/05-quan-ly/quyet-dinh.md). Không làm việc đang "Chờ" một câu hỏi chưa chốt.
4. Trước khi sửa code của một tính năng, đọc phần liên quan trong đặc tả MVP, thiết kế database, danh sách API.

## Trong phiên

- Mỗi việc làm gắn với một mã `T-xxx`. Việc mới phát sinh thì thêm dòng mới vào `nhiem-vu.md`, không đánh lại số.
- Đổi trạng thái việc ngay khi bắt đầu ("Đang làm") và khi xong ("Xong"). Chỉ ghi "Xong" khi có bằng chứng: build và test pass, tài liệu liên quan đã cập nhật.
- Gặp mâu thuẫn giữa code và tài liệu, hoặc giữa hai tài liệu: ghi thành `Q-xxx` trong `quyet-dinh.md`, dừng lại hỏi chủ dự án, không tự chọn bên nào.
- Câu hỏi được trả lời: chuyển sang "Đã chốt" thành `D-xxx`, sửa tài liệu nguồn cho khớp, mở các việc đang chờ nó.
- Quyết định kiến trúc lớn viết thành ADR mới trong `docs/04-quyet-dinh/`. ADR đã chấp nhận không sửa nội dung; muốn đổi thì viết ADR mới và đánh dấu ADR cũ "Bị thay thế".

## Cuối phiên

- Thêm một mục vào đầu "Nhật ký phiên làm việc" trong `tien-do.md`: đã làm gì, quyết định gì, còn dở gì, việc kế tiếp.
- Cập nhật "Trạng thái hiện tại" và bảng "Theo giai đoạn".
- Cập nhật dòng "Cập nhật: <ngày>" ở đầu các file đã sửa.

## Quy tắc làm việc

- **Thứ tự ưu tiên khi xung đột:** yêu cầu rõ ràng của chủ dự án cho việc hiện tại → bảo mật và toàn vẹn dữ liệu → đúng hành vi → kiến trúc và quy ước sẵn có → dễ bảo trì → hiệu năng → phong cách.
- **Tài liệu là nguồn sự thật.** Hành vi theo [đặc tả MVP](docs/01-san-pham/dac-ta-mvp.md); bảng theo [thiết kế database](docs/02-ky-thuat/thiet-ke-database.md); endpoint, mã lỗi theo [danh sách API](docs/02-ky-thuat/danh-sach-api.md); cấu trúc theo [ARCHITECTURE.md](ARCHITECTURE.md). Đổi hành vi thì sửa tài liệu trong cùng commit với code.
- **Thay đổi tối thiểu.** Chỉ sửa đúng phạm vi việc. Không refactor, đổi tên, format lại, nâng phiên bản, thêm dependency hay thêm pattern mới (event bus, cache, thư viện state...) khi chưa hỏi. Không viết lại cả file khi chỉ cần sửa một phần.
- **Không bịa.** Không đoán tên class, cột, endpoint, khóa cấu hình, biến môi trường, phiên bản thư viện. Kiểm tra trong repo hoặc tài liệu chính thức; không kiểm được thì nói rõ.
- **Ngoài phạm vi.** Thấy vấn đề không liên quan thì ghi thành `T-xxx` hoặc `Q-xxx` và báo, không tự sửa, trừ khi nó chặn việc hiện tại hoặc là lỗ hổng bảo mật.
- **Hỏi trước khi:** push, viết lại lịch sử git, xóa file, `reset --hard`, `clean`, đổi line ending hoặc format hàng loạt, sửa `.env` hay cấu hình môi trường, tắt hoặc nới bất kỳ kiểm tra xác thực, phân quyền, validation hay test nào.
- **Trung thực về trạng thái.** Chỉ báo "xong", "test pass", "đã commit" khi có kết quả lệnh vừa chạy. Không chạy được build hoặc test thì nói rõ lý do.

## Quy ước chung

- Tài liệu và comment viết tiếng Việt; file UTF-8, line ending LF.
- Mã tham chiếu: `US-xx` (user story), `BR-xx` (quy tắc nghiệp vụ), `A-x` (giả định) trong đặc tả MVP; `T-xxx` nhiệm vụ; `Q-xxx` câu hỏi mở; `D-xxx` quyết định đã chốt.
- Code tuân theo 8 quy tắc ở mục "Chiến lược đa nền tảng" của [kế hoạch tổng quan](docs/00-tong-quan/ke-hoach-tong-quan.md#chiến-lược-đa-nền-tảng); các quy ước frontend bên dưới là cách áp dụng chúng.
- Comment giải thích *tại sao*, không lặp lại code. Code hiện thực một quy tắc nghiệp vụ thì ghi mã của nó, ví dụ `// BR-06: khóa hai user theo thứ tự id để tránh deadlock`.
- Tên nói rõ ý định; tránh `data`, `temp`, `obj`, `info`. Tên trong code bằng tiếng Anh, theo thuật ngữ của tài liệu: moment, kept, shared, recipient, layer, reaction, invite.
- Không viết cứng hay commit secret (mật khẩu, khóa ký JWT, khóa object storage, client secret). Cấu hình đọc từ biến môi trường; chỉ commit file mẫu như `.env.example`. Thấy secret trong code thì báo ngay.

## Quy ước backend (Java, Spring Boot)

- Chia gói theo tính năng như [ARCHITECTURE.md mục 3](ARCHITECTURE.md#3-cấu-trúc-repo). Controller mỏng → Service (nghiệp vụ, transaction) → Repository. Tính năng này gọi tính năng khác qua service, không dùng thẳng repository của nhau.
- Entity JPA không ra khỏi tầng service; request và response là DTO (ưu tiên Java `record`). Tên trường JSON `camelCase` đúng như danh sách API.
- Validation hai lớp: Bean Validation trên DTO cho định dạng, service kiểm tra quy tắc nghiệp vụ. Độ dài chữ đếm theo grapheme bằng `BreakIterator`, không dùng `String.length()`.
- Lỗi: ném exception nghiệp vụ mang mã lỗi trong [bảng mã lỗi](docs/02-ky-thuat/danh-sach-api.md#7-bảng-mã-lỗi); một `@RestControllerAdvice` duy nhất chuyển thành Problem Details. Mã lỗi mới phải thêm vào bảng trước. Không có catch rỗng; không trả stack trace cho client.
- Quyền xem: mọi chỗ trả khoảnh khắc, URL file hay reaction đều đi qua `MomentAccessPolicy`. Không có quyền thì `404`, không `403`.
- Transaction: `@Transactional` ở service cho thao tác nhiều bước (kết bạn, tạo khoảnh khắc cùng việc nền). Không gọi object storage, Google hay ffmpeg bên trong transaction database.
- Truy vấn: chỉ dùng tham số bind (JPA, `:param`), không nối chuỗi SQL. Danh sách luôn có giới hạn và phân trang con trỏ; để ý N+1.
- Thời gian dùng `Instant`, lấy từ `Clock` được inject để test được logic 24 giờ. Không gọi `Instant.now()` rải rác.
- Log bằng SLF4J, kèm request id qua MDC. Không log mật khẩu, token, presigned URL, caption hay nội dung riêng tư.
- Gọi dịch vụ ngoài (Google, email, object storage) phải có timeout; chỉ thử lại lỗi tạm thời, có giới hạn số lần và thời gian chờ tăng dần.

## Quy ước frontend (TypeScript, Next.js)

- TypeScript `strict`; không dùng `any` hay `as any` để tắt lỗi, dùng `unknown` rồi thu hẹp kiểu.
- Không dùng tính năng phía server của Next.js (SSR, Server Actions, API Routes, middleware): dự án chạy `output: 'export'`.
- Gọi API chỉ qua client chung trong `src/lib/api/`; xử lý lỗi theo trường `code`, không theo `title` hay `message`.
- Camera, thông báo, chia sẻ, token, đăng nhập Google chỉ dùng qua interface trong `src/platform/`, không gọi API trình duyệt trực tiếp trong component.
- Access token chỉ giữ trong bộ nhớ, không ghi `localStorage` hay `sessionStorage`. Service worker chỉ cache giao diện tĩnh, không cache dữ liệu riêng tư.
- Mọi chuỗi hiển thị lấy từ `src/i18n/`, có đủ bản `vi` và `en`; không viết cứng chuỗi trong component.
- Component một trách nhiệm: không trộn gọi API, nghiệp vụ, state và hiển thị trong một component. Validation ở client chỉ để trải nghiệm, server luôn kiểm tra lại. Đếm ký tự bằng `Intl.Segmenter`.
- Giao diện mobile-first (khung 390 px). Chữ tương phản tối thiểu 4.5:1, mọi nút có nhãn, dùng được bằng bàn phím.

## Database và API

- Schema chỉ đổi qua migration Flyway mới `V<số>__<mo_ta>.sql`. Không sửa migration đã chạy. Cập nhật `thiet-ke-database.md` cùng commit.
- Không đổi đường dẫn, cấu trúc request/response, mã HTTP, mã lỗi hay cách xác thực của endpoint mà không sửa `danh-sach-api.md` trước.
- File tải lên: kiểm tra loại, dung lượng theo BR-10 ở cả lúc tạo và lúc `complete`; không tin phần mở rộng hay tên file client gửi; khóa file luôn do server sinh.

## Kiểm thử

- Code mới và bản sửa lỗi phải có test: trường hợp đúng, dữ liệu sai, biên, phân quyền. Mỗi quy tắc `BR-xx` có ít nhất một test, tên test nói hành vi, ví dụ `shouldRejectFriendshipWhenEitherSideHas15Friends`.
- Integration test chạy với PostgreSQL và object storage chuẩn S3 (RustFS, D-010) thật qua Testcontainers, không dùng database nhúng thay thế.
- Sửa lỗi: tái hiện → tìm nguyên nhân → sửa nhỏ nhất → thêm test chống tái phát.
- Trước khi báo xong: chạy build và test của phần đã sửa, xem lại diff, bỏ code debug, import thừa, thay đổi ngoài phạm vi.

## Git

- Conventional Commits `type(scope): mô tả ngắn`, mô tả viết thường, không dấu chấm cuối; mỗi commit một mục đích. Author `TrungLe05`.
- Trước khi commit: xem `git diff --stat` và `git diff --ignore-cr-at-eol`; `git add` theo đường dẫn cụ thể, không `git add -A`.
- Không commit secret, file build, `node_modules/`, `target/`, `.env`.
