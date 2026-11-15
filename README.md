# Khoảnh khắc (moment)

Khoảnh khắc là mạng xã hội nhỏ cho nhóm bạn thân. Người dùng chụp nhanh một cảnh đẹp để bạn bè cùng thấy, rồi chọn giữ lại những khoảnh khắc đáng nhớ để chúng quay lại sau này. Đây là dự án portfolio cá nhân, chạy trên web desktop trước và mở rộng sang Android, iOS ở các giai đoạn sau.

## Công nghệ

- Backend: Java 25, Spring Boot 4.1 (Web, Security, Data JPA), PostgreSQL 16, Flyway, Maven
- Frontend: Node.js 24, Next.js 16 (export tĩnh), Tailwind CSS, shadcn/ui
- Lưu trữ file: RustFS (chuẩn S3) khi phát triển, Cloudflare R2 khi triển khai
- Xử lý media: ffmpeg
- Email khi phát triển: Mailpit
- Triển khai: Docker Compose trên một VPS; CI/CD bằng GitHub Actions, image backend trên Docker Hub

## Cấu trúc thư mục

```
moment/
├── README.md                     # File này
├── compose.yaml                  # Hạ tầng dev: PostgreSQL, RustFS, Mailpit
├── .env.example                  # Mẫu biến môi trường
├── .github/workflows/ci.yml      # CI/CD
├── backend/                      # Spring Boot
├── frontend/                     # Next.js
└── docs/
    ├── README.md                 # Mục lục tài liệu
    ├── 00-tong-quan/
    │   └── ke-hoach-tong-quan.md # Tầm nhìn, quyết định, lộ trình
    ├── 01-san-pham/
    │   └── dac-ta-mvp.md         # Quy tắc nghiệp vụ, user story
    ├── 02-ky-thuat/
    │   ├── thiet-ke-database.md  # Bảng, quan hệ, index
    │   └── danh-sach-api.md      # REST API, luồng upload, mã lỗi
    ├── 03-giao-dien/
    │   └── dinh-huong-giao-dien.md
    └── 04-quyet-dinh/            # Ghi nhận quyết định kiến trúc (ADR)
```

## Chạy môi trường dev

Cần: Docker Desktop, JDK 25, Node.js 24.

```bash
cp .env.example .env          # rồi đổi POSTGRES_PASSWORD, S3_SECRET_KEY
docker compose up -d          # PostgreSQL :5432, RustFS :9000 (console :9001), Mailpit :8025
```

Backend (đọc `.env` ở gốc repo, cần đặt `JWT_SIGNING_KEY` trước; health check ở http://localhost:8080/actuator/health; email xem ở Mailpit http://localhost:8025):

```bash
cd backend && ./mvnw spring-boot:run
```

Frontend (http://localhost:3000):

```bash
cd frontend && npm install && npm run dev
```

Kiểm tra trước khi push: `./mvnw verify` trong `backend/` (cần Docker cho Testcontainers); `npm run lint`, `npm run typecheck`, `npm run build` trong `frontend/`.

Job đẩy image backend lên Docker Hub đang tạm tắt đến release v1. Khi bật lại, cần tạo trong GitHub → Settings → Secrets and variables → Actions: biến `DOCKERHUB_USERNAME` và secret `DOCKERHUB_TOKEN` (Docker Hub access token quyền Read & Write).
