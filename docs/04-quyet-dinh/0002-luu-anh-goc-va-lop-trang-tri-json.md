# 0002. Lưu ảnh gốc và lớp trang trí JSON tách riêng

Trạng thái: Đã chấp nhận · Ngày: 15/11/2026

## Bối cảnh

Khoảnh khắc có caption, sticker, biểu ngữ đè lên ảnh. Giai đoạn 3 cần trang trí lại khoảnh khắc lưu giữ (photobooth, filter), và widget cần hiển thị nhanh một ảnh đã có sẵn trang trí.

## Quyết định

- Lưu ảnh gốc (`ORIGINAL`) và các lớp trang trí dưới dạng JSON (`moments.layers`), tọa độ theo tỉ lệ 0–1.
- Với ảnh, client xuất thêm bản ghép sẵn (`COMPOSED`) từ canvas; worker tạo `DISPLAY` và `THUMBNAIL` từ bản ghép.
- Với video, không ghép lớp vào video; client vẽ lớp đè lên lúc phát.

## Hệ quả

- Trang trí lại được từ ảnh gốc về sau, gỡ được caption cũ.
- Mỗi khoảnh khắc ảnh tốn thêm dung lượng cho ảnh gốc; chấp nhận vì khoảnh khắc chia sẻ bị xóa sau 24 giờ.
- Client và server phải thống nhất cấu trúc JSON; đổi cấu trúc thì tăng `layers_version`.
- Video hiển thị ở nơi không vẽ lớp (ví dụ chia sẻ ra ngoài) sẽ thiếu trang trí; xử lý ở giai đoạn sau nếu cần.
