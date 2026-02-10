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
    ticket_close_at TIMESTAMP NOT NULL,
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

-- 사용자
INSERT INTO users (email, password, name, phone, role) VALUES
    ('test1@test.com', 'password123', '테스트유저1', '010-1234-5678', 'USER'),
    ('test2@test.com', 'password123', '테스트유저2', '010-2345-6789', 'USER'),
    ('test3@test.com', 'password123', '테스트유저3', '010-3456-7890', 'USER'),
    ('admin@test.com', 'admin123', '관리자', '010-0000-0000', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- 공연
INSERT INTO concerts (title, artist, venue) VALUES
    ('2026 아이유 콘서트', '아이유', '잠실종합운동장'),
    ('2026 블랙핑크 월드투어', 'BLACKPINK', '올림픽공원 체조경기장')
ON CONFLICT DO NOTHING;

-- 공연 회차
INSERT INTO schedules (concert_id, date_time, total_seats, ticket_open_at, ticket_close_at, status) VALUES
    (1, '2026-03-15 19:00:00', 1000, '2026-02-09 20:00:00', '2026-03-15 18:00:00', 'OPEN'),
    (2, '2026-04-20 18:00:00', 1500, '2026-02-20 20:00:00', '2026-04-20 17:00:00', 'UPCOMING'),
    (1, '2026-01-10 19:00:00', 800, '2025-12-01 20:00:00', '2026-01-10 18:00:00', 'CLOSED')
ON CONFLICT DO NOTHING;

-- 등급 설정
INSERT INTO schedule_grades (schedule_id, grade, seat_count, price) VALUES
    (1, 'VIP', 100, 150000),
    (1, 'R', 200, 120000),
    (1, 'S', 300, 90000),
    (1, 'A', 400, 60000),
    (2, 'VIP', 150, 200000),
    (2, 'R', 300, 150000),
    (2, 'S', 400, 100000),
    (2, 'A', 650, 70000),
    (3, 'VIP', 80, 150000),
    (3, 'R', 150, 120000),
    (3, 'S', 250, 90000),
    (3, 'A', 320, 60000)
ON CONFLICT (schedule_id, grade) DO NOTHING;

-- 좌석 
INSERT INTO seats (schedule_id, grade, seat_number, price, status) VALUES
    (1, 'VIP', '1', 150000, 'AVAILABLE'),
    (1, 'VIP', '2', 150000, 'HELD'),
    (1, 'VIP', '3', 150000, 'SOLD'),
    (1, 'VIP', '4', 150000, 'AVAILABLE'),
    (1, 'VIP', '5', 150000, 'AVAILABLE'),
    (1, 'VIP', '6', 150000, 'HELD'),
    (1, 'VIP', '7', 150000, 'SOLD'),
    (1, 'VIP', '8', 150000, 'AVAILABLE'),
    (1, 'VIP', '9', 150000, 'AVAILABLE'),
    (1, 'VIP', '10', 150000, 'AVAILABLE'),
    (1, 'R', '1', 120000, 'HELD'),
    (1, 'R', '2', 120000, 'AVAILABLE'),
    (1, 'R', '3', 120000, 'AVAILABLE'),
    (1, 'R', '4', 120000, 'SOLD'),
    (1, 'R', '5', 120000, 'AVAILABLE'),
    (1, 'R', '6', 120000, 'AVAILABLE'),
    (1, 'R', '7', 120000, 'HELD'),
    (1, 'R', '8', 120000, 'AVAILABLE'),
    (1, 'R', '9', 120000, 'SOLD'),
    (1, 'R', '10', 120000, 'AVAILABLE'),
    (1, 'S', '1', 90000, 'AVAILABLE'),
    (1, 'S', '2', 90000, 'SOLD'),
    (1, 'S', '3', 90000, 'AVAILABLE'),
    (1, 'S', '4', 90000, 'HELD'),
    (1, 'S', '5', 90000, 'AVAILABLE'),
    (1, 'S', '6', 90000, 'AVAILABLE'),
    (1, 'S', '7', 90000, 'SOLD'),
    (1, 'S', '8', 90000, 'AVAILABLE'),
    (1, 'S', '9', 90000, 'HELD'),
    (1, 'S', '10', 90000, 'AVAILABLE'),
    (1, 'A', '1', 60000, 'SOLD'),
    (1, 'A', '2', 60000, 'AVAILABLE'),
    (1, 'A', '3', 60000, 'AVAILABLE'),
    (1, 'A', '4', 60000, 'HELD'),
    (1, 'A', '5', 60000, 'AVAILABLE'),
    (1, 'A', '6', 60000, 'AVAILABLE'),
    (1, 'A', '7', 60000, 'SOLD'),
    (1, 'A', '8', 60000, 'AVAILABLE'),
    (1, 'A', '9', 60000, 'AVAILABLE'),
    (1, 'A', '10', 60000, 'HELD'),
    (2, 'VIP', '1', 200000, 'AVAILABLE'),
    (2, 'VIP', '2', 200000, 'HELD'),
    (2, 'VIP', '3', 200000, 'SOLD'),
    (2, 'R', '1', 150000, 'AVAILABLE'),
    (2, 'R', '2', 150000, 'SOLD'),
    (2, 'R', '3', 150000, 'HELD'),
    (2, 'S', '1', 100000, 'AVAILABLE'),
    (2, 'S', '2', 100000, 'HELD'),
    (2, 'A', '1', 70000, 'SOLD'),
    (2, 'A', '2', 70000, 'AVAILABLE'),
    (3, 'VIP', '1', 150000, 'HELD'),
    (3, 'VIP', '2', 150000, 'AVAILABLE'),
    (3, 'R', '1', 120000, 'SOLD'),
    (3, 'R', '2', 120000, 'AVAILABLE'),
    (3, 'S', '1', 90000, 'AVAILABLE'),
    (3, 'A', '1', 60000, 'HELD');
