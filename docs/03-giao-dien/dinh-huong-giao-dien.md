# Định hướng giao diện

Giao diện chia làm hai không gian để khác biệt giữa chia sẻ và lưu giữ hiện ra ngay khi nhìn. Bản mẫu 5 màn hình (feed, trình soạn, kho lưu giữ, ngày này năm trước, widget desktop): https://claude.ai/artifact/2mx7iFqeANEgVFoyfP4ryx. Bố cục đã đạt.

## Hai không gian

| Không gian | Màn hình | Phong cách |
| --- | --- | --- |
| Chia sẻ | Feed, trình soạn, chat, widget | Nền tối, ảnh là trung tâm, một màu nhấn cam đào |
| Lưu giữ | Kho, ngày này năm trước, photobooth | Giấy kem, khung polaroid, chữ viết tay cho caption |

## Việc còn mở

- **Font:** bộ hiện tại (Be Vietnam Pro, Patrick Hand, Lora) chưa đẹp, cần chọn lại. Font mới phải hiển thị tốt tiếng Việt có dấu.
- **Glassmorphism:** hướng muốn thử, hợp nhất với các lớp đè trên ảnh ở không gian tối (nhãn tên, đồng hồ 24h, caption, thanh reaction). Chữ trên kính phải đủ tương phản (tối thiểu 4.5:1); cần phương án nền đặc cho máy yếu.

## Quy tắc đã chốt

- Caption trên ảnh giới hạn theo số dòng và tự thu nhỏ cỡ chữ; đếm ký tự theo ký tự hiển thị (`Intl.Segmenter` ở client, `BreakIterator` ở server).
- Ghi chú dài nằm dưới ảnh, thu gọn với "xem thêm" — để sau MVP (D-030).
- Lưu ảnh gốc + các lớp trang trí dạng JSON, kèm một bản ghép sẵn để hiển thị nhanh; về sau trang trí lại được từ ảnh gốc.
- Thiết kế mobile-first (khung 390 px) dù chạy desktop trước.
