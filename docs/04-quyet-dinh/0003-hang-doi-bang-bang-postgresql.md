# 0003. Hàng đợi việc nền bằng bảng PostgreSQL

Trạng thái: Đề xuất · Ngày: 16/11/2026

## Bối cảnh

Xử lý media (ffmpeg) và xóa file cần chạy nền, có thử lại. Hệ thống chạy trên một VPS nhỏ; thêm RabbitMQ hay Redis là thêm một dịch vụ phải vận hành và tốn RAM.

## Quyết định

Dùng bảng `media_jobs` trong PostgreSQL làm hàng đợi. Worker lấy việc bằng `SELECT ... FOR UPDATE SKIP LOCKED`, thử lại tối đa 3 lần với thời gian chờ tăng dần. Worker là cùng ứng dụng Spring Boot chạy với profile `worker` trong một container riêng có cài ffmpeg.

## Hệ quả

- Không thêm dịch vụ mới; việc nền nằm cùng transaction với dữ liệu (tạo khoảnh khắc và tạo việc trong một transaction).
- Đủ cho tải của một nhóm nhỏ; nếu tải lớn hơn nhiều thì chuyển sang message broker.
- Cần job dọn việc cũ và trả lại việc bị kẹt ở trạng thái `RUNNING`.
