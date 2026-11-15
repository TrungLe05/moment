-- V1: schema đầu tiên theo thiết kế database bản chốt 1 (docs/02-ky-thuat/thiet-ke-database.md, mục 3).
-- Không sửa file này sau khi đã chạy; thay đổi schema viết thành migration mới.

-- Người dùng
CREATE TABLE users (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             varchar(254) NOT NULL,
    email_verified_at timestamptz,
    password_hash     varchar(100),              -- NULL nếu chỉ đăng nhập Google
    username          varchar(20),               -- NULL cho tới khi xong bước chọn username (US-06)
    display_name      varchar(40),
    avatar_key        varchar(300),              -- khóa file trong object storage
    locale            varchar(5)  NOT NULL DEFAULT 'vi' CHECK (locale IN ('vi', 'en')),
    plan              varchar(16) NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE')),
    is_demo           boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_username_format CHECK (username ~ '^[a-z0-9._]{3,20}$')
);
CREATE UNIQUE INDEX users_email_uk    ON users (lower(email));
CREATE UNIQUE INDEX users_username_uk ON users (lower(username));

-- Danh tính đăng nhập bên ngoài (Google)
CREATE TABLE auth_identities (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   varchar(16)  NOT NULL CHECK (provider IN ('GOOGLE')),
    subject    varchar(255) NOT NULL,             -- claim "sub" của Google
    email      varchar(254) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT auth_identities_provider_subject_uk UNIQUE (provider, subject)
);
CREATE INDEX auth_identities_user_idx ON auth_identities (user_id);

-- Refresh token, xoay vòng theo "family" để phát hiện token bị dùng lại
CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   uuid NOT NULL,
    token_hash  char(64)    NOT NULL UNIQUE,      -- SHA-256 hex
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    user_agent  varchar(255),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_idx   ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);

-- Token gửi qua email: xác thực email, đặt lại mật khẩu
CREATE TABLE email_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    varchar(20) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash char(64)    NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_tokens_user_idx ON email_tokens (user_id, purpose);

-- Mã mời: đúng một mã đang hoạt động cho mỗi người (BR-04)
CREATE TABLE invite_codes (
    user_id    uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    code       varchar(16) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Quan hệ bạn bè: mỗi cặp lưu một dòng, user_a_id < user_b_id
CREATE TABLE friendships (
    user_a_id  uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b_id  uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_a_id, user_b_id),
    CONSTRAINT friendships_order CHECK (user_a_id < user_b_id)
);
CREATE INDEX friendships_user_b_idx ON friendships (user_b_id);

-- Khoảnh khắc
CREATE TABLE moments (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    media_type     varchar(8)  NOT NULL CHECK (media_type IN ('PHOTO', 'VIDEO')),
    source         varchar(8)  NOT NULL CHECK (source IN ('CAMERA', 'UPLOAD')),
    mode           varchar(8)  NOT NULL CHECK (mode IN ('SHARED', 'KEPT')),
    audience       varchar(8)  NOT NULL CHECK (audience IN ('ALL', 'SELECTED')),
    status         varchar(12) NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT', 'PROCESSING', 'READY', 'FAILED', 'EXPIRED')),
    caption        text,
    layers         jsonb       NOT NULL DEFAULT '[]'::jsonb,   -- lớp trang trí (ADR 0002)
    layers_version smallint    NOT NULL DEFAULT 1,
    trim_start_ms  integer     CHECK (trim_start_ms >= 0),     -- chỉ cho video tải lên dài hơn 5 giây
    duration_ms    integer,
    width          integer,
    height         integer,
    failure_reason varchar(200),
    created_at     timestamptz NOT NULL DEFAULT now(),         -- lúc chủ bấm gửi (BR-13)
    expires_at     timestamptz NOT NULL,                       -- created_at + 24 giờ
    kept_at        timestamptz,
    processed_at   timestamptz,
    -- Giới hạn an toàn theo độ dài chuỗi; giới hạn theo grapheme (BR-11) kiểm tra ở tầng ứng dụng
    CONSTRAINT moments_caption_len CHECK (char_length(caption) <= 400),
    CONSTRAINT moments_kept        CHECK ((mode = 'KEPT') = (kept_at IS NOT NULL))
);
CREATE INDEX moments_owner_created_idx ON moments (owner_id, created_at DESC, id DESC);
CREATE INDEX moments_owner_kept_idx    ON moments (owner_id, created_at DESC, id DESC)
    WHERE mode = 'KEPT' AND status = 'READY';
CREATE INDEX moments_shared_expiry_idx ON moments (expires_at)
    WHERE mode = 'SHARED' AND status IN ('PROCESSING', 'READY', 'FAILED');
CREATE INDEX moments_draft_idx         ON moments (created_at) WHERE status = 'DRAFT';

-- Người nhận, chốt tại thời điểm đăng (BR-12); created_at chép từ moments để sắp xếp feed
CREATE TABLE moment_recipients (
    moment_id    uuid NOT NULL REFERENCES moments (id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (moment_id, recipient_id)
);
CREATE INDEX moment_recipients_feed_idx ON moment_recipients (recipient_id, created_at DESC, moment_id DESC);

-- Các file của một khoảnh khắc
CREATE TABLE moment_media (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    moment_id    uuid NOT NULL REFERENCES moments (id) ON DELETE CASCADE,
    variant      varchar(12) NOT NULL CHECK (variant IN ('ORIGINAL', 'COMPOSED', 'DISPLAY', 'VIDEO', 'THUMBNAIL')),
    object_key   varchar(300) NOT NULL UNIQUE,
    content_type varchar(64)  NOT NULL,
    size_bytes   bigint,
    width        integer,
    height       integer,
    duration_ms  integer,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT moment_media_variant_uk UNIQUE (moment_id, variant)
);

-- Reaction: tối đa một reaction mỗi người trên một khoảnh khắc (BR-21); server chuẩn hóa emoji trước khi lưu (D-028)
CREATE TABLE reactions (
    moment_id  uuid NOT NULL REFERENCES moments (id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji      varchar(16) NOT NULL CHECK (emoji IN ('❤️', '😍', '🔥', '😂', '😮', '😢')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (moment_id, user_id)
);

-- Hàng đợi việc nền: xử lý media, xóa file (ADR 0003)
CREATE TABLE media_jobs (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type       varchar(20) NOT NULL CHECK (type IN ('PROCESS_MOMENT', 'DELETE_OBJECTS')),
    moment_id  uuid,                              -- không khóa ngoại: khoảnh khắc có thể đã bị xóa
    payload    jsonb       NOT NULL DEFAULT '{}'::jsonb,
    status     varchar(10) NOT NULL DEFAULT 'PENDING'
               CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts   smallint    NOT NULL DEFAULT 0,
    run_after  timestamptz NOT NULL DEFAULT now(),
    locked_at  timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX media_jobs_pick_idx ON media_jobs (run_after) WHERE status = 'PENDING';
