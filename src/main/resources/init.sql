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

-- 공연 회차 (total_seats는 SUM(zones.seat_count) 또는 COUNT(seats)와 동기화해 두는 것을 권장)
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

-- 등급 설정 (등급별 가격). 좌석 수는 zones 합산으로 계산
CREATE TABLE IF NOT EXISTS grades (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedules(id),
    grade VARCHAR(10) NOT NULL,
    price INT NOT NULL,
    UNIQUE(schedule_id, grade)
);

-- 구역 설정 (구역별 등급·좌석 수). 예: VIP-A(200석), S-1(100석)
CREATE TABLE IF NOT EXISTS zones (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedules(id),
    zone VARCHAR(20) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    seat_count INT NOT NULL,
    UNIQUE(schedule_id, zone)
);

-- 좌석 (구역별 좌석 번호)
CREATE TABLE IF NOT EXISTS seats (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedules(id),
    grade VARCHAR(10) NOT NULL,
    zone VARCHAR(20) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    price INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(schedule_id, zone, seat_number)
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
    UNIQUE(user_id, schedule_id)
);

-- 예약-좌석
CREATE TABLE IF NOT EXISTS reservation_seats (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    seat_id BIGINT REFERENCES seats(id),
    seat_number VARCHAR(20),
    zone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_seats_seat_id
    ON reservation_seats(seat_id) WHERE seat_id IS NOT NULL;

-- 결제 (예약당 성공 결제 1건 제한: 동일 reservation_id에 status='COMPLETED'는 1건만 허용)
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_reservation_completed
    ON payments(reservation_id) WHERE status = 'COMPLETED';

-- =============================================
-- 인덱스
-- =============================================
CREATE INDEX IF NOT EXISTS idx_schedules_concert ON schedules(concert_id);
CREATE INDEX IF NOT EXISTS idx_grades_schedule ON grades(schedule_id);
CREATE INDEX IF NOT EXISTS idx_zones_schedule ON zones(schedule_id);
CREATE INDEX IF NOT EXISTS idx_zones_grade ON zones(schedule_id, grade);
CREATE INDEX IF NOT EXISTS idx_seats_schedule ON seats(schedule_id);
CREATE INDEX IF NOT EXISTS idx_seats_schedule_zone ON seats(schedule_id, zone);
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
-- 비밀번호: test1~3 = "password", admin = "admin" (bcrypt)
INSERT INTO users (email, password, name, phone, role) VALUES
    ('test1@test.com', '$2b$10$b4/OZ/TPWsYYHXCFNRhbhuLpYwuTpsuqvcVI7qD6wYbKhqUBd45cC', '테스트유저1', '010-1234-5678', 'USER'),
    ('test2@test.com', '$2b$10$b4/OZ/TPWsYYHXCFNRhbhuLpYwuTpsuqvcVI7qD6wYbKhqUBd45cC', '테스트유저2', '010-2345-6789', 'USER'),
    ('test3@test.com', '$2b$10$b4/OZ/TPWsYYHXCFNRhbhuLpYwuTpsuqvcVI7qD6wYbKhqUBd45cC', '테스트유저3', '010-3456-7890', 'USER'),
    ('admin@test.com', '$2b$10$PBwV7jUmNIf1L7sDJJgWNe82lJclUrxVZlN8jdwf0TGE51J0THE1W', '관리자', '010-0000-0000', 'ADMIN')
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password;

-- 공연
INSERT INTO concerts (title, artist, venue) VALUES
    ('2026 아이유 콘서트', '아이유', '잠실종합운동장'),
    ('2026 블랙핑크 월드투어', 'BLACKPINK', '올림픽공원 체조경기장')
ON CONFLICT DO NOTHING;

-- 공연 회차
-- total_seats = zones 합산: VIP(8×200+11×100=2700) + S(4×100+14×100=1800) + A(6×100=600) = 5100
INSERT INTO schedules (concert_id, date_time, total_seats, ticket_open_at, ticket_close_at, status) VALUES
    (1, '2026-03-15 19:00:00', 5100, '2026-02-09 20:00:00', '2026-03-15 18:00:00', 'OPEN'),
    (2, '2026-04-20 18:00:00', 5100, '2026-02-20 20:00:00', '2026-04-20 17:00:00', 'UPCOMING'),
    (1, '2026-01-10 19:00:00', 5100, '2025-12-01 20:00:00', '2026-01-10 18:00:00', 'CLOSED')
ON CONFLICT DO NOTHING;

-- 등급 설정 (TIMELINE: VIP, S, A)
INSERT INTO grades (schedule_id, grade, price) VALUES
    (1, 'VIP', 120000),
    (1, 'S', 90000),
    (1, 'A', 60000),
    (2, 'VIP', 150000),
    (2, 'S', 100000),
    (2, 'A', 70000),
    (3, 'VIP', 120000),
    (3, 'S', 90000),
    (3, 'A', 60000)
ON CONFLICT (schedule_id, grade) DO NOTHING;

-- 구역 설정 (schedule_id, zone, grade, seat_count). VIP-{A~H,3~13}, S-{1,2,14,15,27~40}, A-{24~26,41~43}
INSERT INTO zones (schedule_id, zone, grade, seat_count) VALUES
    (1, 'A', 'VIP', 200), (1, 'B', 'VIP', 200), (1, 'C', 'VIP', 200), (1, 'D', 'VIP', 200), (1, 'E', 'VIP', 200), (1, 'F', 'VIP', 200), (1, 'G', 'VIP', 200), (1, 'H', 'VIP', 200),
    (1, '3', 'VIP', 100), (1, '4', 'VIP', 100), (1, '5', 'VIP', 100), (1, '6', 'VIP', 100), (1, '7', 'VIP', 100), (1, '8', 'VIP', 100), (1, '9', 'VIP', 100), (1, '10', 'VIP', 100), (1, '11', 'VIP', 100), (1, '12', 'VIP', 100), (1, '13', 'VIP', 100),
    (1, '1', 'S', 100), (1, '2', 'S', 100), (1, '14', 'S', 100), (1, '15', 'S', 100),
    (1, '27', 'S', 100), (1, '28', 'S', 100), (1, '29', 'S', 100), (1, '30', 'S', 100), (1, '31', 'S', 100), (1, '32', 'S', 100), (1, '33', 'S', 100), (1, '34', 'S', 100), (1, '35', 'S', 100), (1, '36', 'S', 100), (1, '37', 'S', 100), (1, '38', 'S', 100), (1, '39', 'S', 100), (1, '40', 'S', 100),
    (1, '24', 'A', 100), (1, '25', 'A', 100), (1, '26', 'A', 100), (1, '41', 'A', 100), (1, '42', 'A', 100), (1, '43', 'A', 100),
    (2, 'A', 'VIP', 200), (2, 'B', 'VIP', 200), (2, 'C', 'VIP', 200), (2, 'D', 'VIP', 200), (2, 'E', 'VIP', 200), (2, 'F', 'VIP', 200), (2, 'G', 'VIP', 200), (2, 'H', 'VIP', 200),
    (2, '3', 'VIP', 100), (2, '4', 'VIP', 100), (2, '5', 'VIP', 100), (2, '6', 'VIP', 100), (2, '7', 'VIP', 100), (2, '8', 'VIP', 100), (2, '9', 'VIP', 100), (2, '10', 'VIP', 100), (2, '11', 'VIP', 100), (2, '12', 'VIP', 100), (2, '13', 'VIP', 100),
    (2, '1', 'S', 100), (2, '2', 'S', 100), (2, '14', 'S', 100), (2, '15', 'S', 100),
    (2, '27', 'S', 100), (2, '28', 'S', 100), (2, '29', 'S', 100), (2, '30', 'S', 100), (2, '31', 'S', 100), (2, '32', 'S', 100), (2, '33', 'S', 100), (2, '34', 'S', 100), (2, '35', 'S', 100), (2, '36', 'S', 100), (2, '37', 'S', 100), (2, '38', 'S', 100), (2, '39', 'S', 100), (2, '40', 'S', 100),
    (2, '24', 'A', 100), (2, '25', 'A', 100), (2, '26', 'A', 100), (2, '41', 'A', 100), (2, '42', 'A', 100), (2, '43', 'A', 100),
    (3, 'A', 'VIP', 200), (3, 'B', 'VIP', 200), (3, 'C', 'VIP', 200), (3, 'D', 'VIP', 200), (3, 'E', 'VIP', 200), (3, 'F', 'VIP', 200), (3, 'G', 'VIP', 200), (3, 'H', 'VIP', 200),
    (3, '3', 'VIP', 100), (3, '4', 'VIP', 100), (3, '5', 'VIP', 100), (3, '6', 'VIP', 100), (3, '7', 'VIP', 100), (3, '8', 'VIP', 100), (3, '9', 'VIP', 100), (3, '10', 'VIP', 100), (3, '11', 'VIP', 100), (3, '12', 'VIP', 100), (3, '13', 'VIP', 100),
    (3, '1', 'S', 100), (3, '2', 'S', 100), (3, '14', 'S', 100), (3, '15', 'S', 100),
    (3, '27', 'S', 100), (3, '28', 'S', 100), (3, '29', 'S', 100), (3, '30', 'S', 100), (3, '31', 'S', 100), (3, '32', 'S', 100), (3, '33', 'S', 100), (3, '34', 'S', 100), (3, '35', 'S', 100), (3, '36', 'S', 100), (3, '37', 'S', 100), (3, '38', 'S', 100), (3, '39', 'S', 100), (3, '40', 'S', 100),
    (3, '24', 'A', 100), (3, '25', 'A', 100), (3, '26', 'A', 100), (3, '41', 'A', 100), (3, '42', 'A', 100), (3, '43', 'A', 100)
ON CONFLICT (schedule_id, zone) DO NOTHING;

-- 좌석 (schedule_id, grade, zone, seat_number). 예시: 구역 A 일부, 구역 1 일부 등
INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status) VALUES
    (1, 'VIP', 'A', '1', 120000, 'AVAILABLE'), (1, 'VIP', 'A', '2', 120000, 'HELD'), (1, 'VIP', 'A', '3', 120000, 'SOLD'), (1, 'VIP', 'A', '4', 120000, 'AVAILABLE'), (1, 'VIP', 'A', '5', 120000, 'AVAILABLE'),
    (1, 'VIP', 'B', '1', 120000, 'AVAILABLE'), (1, 'VIP', 'B', '2', 120000, 'AVAILABLE'), (1, 'VIP', 'B', '3', 120000, 'HELD'),
    (1, 'S', '1', '1', 90000, 'AVAILABLE'), (1, 'S', '1', '2', 90000, 'SOLD'), (1, 'S', '1', '3', 90000, 'AVAILABLE'), (1, 'S', '2', '1', 90000, 'HELD'),
    (1, 'A', '24', '1', 60000, 'SOLD'), (1, 'A', '24', '2', 60000, 'AVAILABLE'), (1, 'A', '41', '1', 60000, 'AVAILABLE'), (1, 'A', '41', '2', 60000, 'HELD'),
    (2, 'VIP', 'A', '1', 150000, 'AVAILABLE'), (2, 'VIP', 'A', '2', 150000, 'SOLD'), (2, 'VIP', 'B', '1', 150000, 'HELD'),
    (2, 'S', '1', '1', 100000, 'AVAILABLE'), (2, 'S', '1', '2', 100000, 'SOLD'), (2, 'A', '24', '1', 70000, 'AVAILABLE'), (2, 'A', '41', '1', 70000, 'HELD'),
    (3, 'VIP', 'A', '1', 120000, 'HELD'), (3, 'VIP', 'B', '1', 120000, 'AVAILABLE'), (3, 'S', '1', '1', 90000, 'AVAILABLE'), (3, 'A', '24', '1', 60000, 'SOLD')
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

-- 좌석 추가 (schedule 1 — 구역별 20석으로 확장)
INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'VIP', 'A', n::text, 120000, 'AVAILABLE' FROM generate_series(6, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'VIP', 'B', n::text, 120000, 'AVAILABLE' FROM generate_series(4, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'VIP', 'C', n::text, 120000, 'AVAILABLE' FROM generate_series(1, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'S', '1', n::text, 90000, 'AVAILABLE' FROM generate_series(4, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'S', '2', n::text, 90000, 'AVAILABLE' FROM generate_series(2, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'A', '24', n::text, 60000, 'AVAILABLE' FROM generate_series(3, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
SELECT 1, 'A', '41', n::text, 60000, 'AVAILABLE' FROM generate_series(3, 20) n
ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

-- =============================================
-- 테스트 트랜잭션 데이터 (예약 + 좌석배정 + 결제)
-- =============================================

-- 예약 1: user1 + schedule1, 추첨 결제완료 (PAID)
INSERT INTO reservations (user_id, schedule_id, grade, track_type, status, quantity, created_at, updated_at) VALUES
    (1, 1, 'VIP', 'LOTTERY', 'PAID', 1, '2026-02-10 10:00:00', '2026-02-10 10:05:00')
ON CONFLICT (user_id, schedule_id) DO NOTHING;

-- 예약 2: user2 + schedule1, 라이브 결제대기 (PENDING)
INSERT INTO reservations (user_id, schedule_id, grade, track_type, status, quantity, created_at, updated_at) VALUES
    (2, 1, 'S', 'LIVE', 'PENDING', 2, '2026-02-10 11:00:00', '2026-02-10 11:00:00')
ON CONFLICT (user_id, schedule_id) DO NOTHING;

-- 예약 3: user3 + schedule1, 추첨 대기중 (PAID_PENDING_SEAT)
INSERT INTO reservations (user_id, schedule_id, grade, track_type, status, quantity, created_at, updated_at) VALUES
    (3, 1, 'VIP', 'LOTTERY', 'PAID_PENDING_SEAT', 1, '2026-02-10 10:30:00', '2026-02-10 10:35:00')
ON CONFLICT (user_id, schedule_id) DO NOTHING;

-- 예약 4: user1 + schedule2, 추첨 취소됨 (CANCELLED)
INSERT INTO reservations (user_id, schedule_id, grade, track_type, status, quantity, created_at, updated_at) VALUES
    (1, 2, 'VIP', 'LOTTERY', 'CANCELLED', 1, '2026-02-20 20:00:00', '2026-02-20 20:30:00')
ON CONFLICT (user_id, schedule_id) DO NOTHING;

-- 예약-좌석: 예약1 (user1 VIP A-4 배정완료)
INSERT INTO reservation_seats (reservation_id, seat_id, zone, seat_number, status, assigned_at, created_at)
SELECT 1, s.id, 'A', '4', 'ASSIGNED', '2026-02-10 10:05:00', '2026-02-10 10:00:00'
FROM seats s WHERE s.schedule_id = 1 AND s.zone = 'A' AND s.seat_number = '4'
ON CONFLICT DO NOTHING;

-- 예약-좌석: 예약2 (user2 S 1-4, 1-5 대기중)
INSERT INTO reservation_seats (reservation_id, seat_id, zone, seat_number, status, created_at)
SELECT 2, s.id, '1', '4', 'PENDING', '2026-02-10 11:00:00'
FROM seats s WHERE s.schedule_id = 1 AND s.zone = '1' AND s.seat_number = '4'
ON CONFLICT DO NOTHING;

INSERT INTO reservation_seats (reservation_id, seat_id, zone, seat_number, status, created_at)
SELECT 2, s.id, '1', '5', 'PENDING', '2026-02-10 11:00:00'
FROM seats s WHERE s.schedule_id = 1 AND s.zone = '1' AND s.seat_number = '5'
ON CONFLICT DO NOTHING;

-- 예약-좌석: 예약3 (user3 VIP B-1 대기중)
INSERT INTO reservation_seats (reservation_id, seat_id, zone, seat_number, status, created_at)
SELECT 3, s.id, 'B', '1', 'PENDING', '2026-02-10 10:30:00'
FROM seats s WHERE s.schedule_id = 1 AND s.zone = 'B' AND s.seat_number = '1'
ON CONFLICT DO NOTHING;

-- 결제 1: 예약1 결제완료 (COMPLETED)
INSERT INTO payments (reservation_id, merchant_uid, imp_uid, amount, status, paid_at, created_at, updated_at) VALUES
    (1, 'FAIR_1707500000_test0001', 'imp_test_0001', 120000, 'COMPLETED', '2026-02-10 10:05:00', '2026-02-10 10:00:00', '2026-02-10 10:05:00')
ON CONFLICT (merchant_uid) DO NOTHING;

-- 결제 2: 예약2 결제대기 (PENDING) — imp_uid 미발급 상태
INSERT INTO payments (reservation_id, merchant_uid, amount, status, created_at, updated_at) VALUES
    (2, 'FAIR_1707500000_test0002', 180000, 'PENDING', '2026-02-10 11:00:00', '2026-02-10 11:00:00')
ON CONFLICT (merchant_uid) DO NOTHING;

-- 결제 3: 예약3 결제완료 (COMPLETED, 좌석 배정 대기중)
INSERT INTO payments (reservation_id, merchant_uid, imp_uid, amount, status, paid_at, created_at, updated_at) VALUES
    (3, 'FAIR_1707500000_test0003', 'imp_test_0003', 120000, 'COMPLETED', '2026-02-10 10:35:00', '2026-02-10 10:30:00', '2026-02-10 10:35:00')
ON CONFLICT (merchant_uid) DO NOTHING;

-- 좌석 상태 동기화: 예약된 좌석 SOLD 처리
UPDATE seats SET status = 'SOLD' WHERE schedule_id = 1 AND zone = 'A' AND seat_number = '4' AND status != 'SOLD';
UPDATE seats SET status = 'HELD' WHERE schedule_id = 1 AND zone = '1' AND seat_number IN ('4','5') AND status = 'AVAILABLE';
UPDATE seats SET status = 'HELD' WHERE schedule_id = 1 AND zone = 'B' AND seat_number = '1' AND status = 'AVAILABLE';
