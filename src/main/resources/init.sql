-- =============================================
-- FairTicket ERD v3.1
-- =============================================

-- 사용자
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 공연
CREATE TABLE IF NOT EXISTS concerts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(255),
    venue VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 공연 회차
CREATE TABLE IF NOT EXISTS schedules (
    id BIGSERIAL PRIMARY KEY,
    concert_id BIGINT NOT NULL REFERENCES concerts(id),
    date_time TIMESTAMP NOT NULL,
    total_seats INT NOT NULL,
    ticket_open_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 등급 설정
CREATE TABLE IF NOT EXISTS schedule_grades (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedules(id),
    grade VARCHAR(10) NOT NULL,
    seat_count INT NOT NULL,
    price INT NOT NULL,
    UNIQUE(schedule_id, grade)
);

-- 좌석
CREATE TABLE IF NOT EXISTS seats (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedules(id),
    grade VARCHAR(10) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    price INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 예약
CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    schedule_id BIGINT NOT NULL REFERENCES schedules(id),
    grade VARCHAR(10) NOT NULL,
    track_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    quantity INT NOT NULL DEFAULT 1,
    needs_confirm BOOLEAN DEFAULT FALSE,
    confirm_deadline TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, schedule_id, track_type)
);

-- 예약-좌석
CREATE TABLE IF NOT EXISTS reservation_seats (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    seat_id BIGINT REFERENCES seats(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_seats_seat_id
    ON reservation_seats(seat_id) WHERE seat_id IS NOT NULL;

-- 결제
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    merchant_uid VARCHAR(100) UNIQUE NOT NULL,
    imp_uid VARCHAR(100) UNIQUE,
    pg_tid VARCHAR(100),
    amount INT NOT NULL,
    method VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 인덱스
-- =============================================
CREATE INDEX IF NOT EXISTS idx_schedules_concert ON schedules(concert_id);
CREATE INDEX IF NOT EXISTS idx_schedule_grades_schedule ON schedule_grades(schedule_id);
CREATE INDEX IF NOT EXISTS idx_seats_schedule ON seats(schedule_id);
CREATE INDEX IF NOT EXISTS idx_seats_status ON seats(status);
CREATE INDEX IF NOT EXISTS idx_reservations_user ON reservations(user_id);
CREATE INDEX IF NOT EXISTS idx_reservations_schedule ON reservations(schedule_id);
CREATE INDEX IF NOT EXISTS idx_reservations_status ON reservations(status);
CREATE INDEX IF NOT EXISTS idx_reservation_seats_reservation ON reservation_seats(reservation_id);
CREATE INDEX IF NOT EXISTS idx_payments_reservation ON payments(reservation_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- =============================================
-- 테스트 데이터
-- =============================================

-- 사용자 (BCrypt: password123 / admin123)
INSERT INTO users (email, password, name, phone, role) VALUES
    ('test1@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '테스트유저1', '010-1234-5678', 'USER'),
    ('test2@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '테스트유저2', '010-2345-6789', 'USER'),
    ('test3@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '테스트유저3', '010-3456-7890', 'USER'),
    ('admin@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '관리자', '010-0000-0000', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- 공연
INSERT INTO concerts (title, artist, venue) VALUES
    ('2025 아이유 콘서트', '아이유', '잠실종합운동장')
ON CONFLICT DO NOTHING;

-- 공연 회차
INSERT INTO schedules (concert_id, date_time, total_seats, ticket_open_at, status) VALUES
    (1, '2025-03-15 19:00:00', 1000, '2025-02-15 20:00:00', 'UPCOMING')
ON CONFLICT DO NOTHING;

-- 등급 설정
INSERT INTO schedule_grades (schedule_id, grade, seat_count, price) VALUES
    (1, 'VIP', 100, 150000),
    (1, 'R', 200, 120000),
    (1, 'S', 300, 90000),
    (1, 'A', 400, 60000)
ON CONFLICT (schedule_id, grade) DO NOTHING;
