-- =============================================
  -- FairTicket 시연용 시드 데이터
  -- =============================================
  SET timezone = 'Asia/Seoul';

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
  CREATE TABLE IF NOT EXISTS grades (
      id BIGSERIAL PRIMARY KEY,
      schedule_id BIGINT NOT NULL REFERENCES schedules(id),
      grade VARCHAR(10) NOT NULL,
      price INT NOT NULL,
      UNIQUE(schedule_id, grade)
  );

  -- 구역 설정
  CREATE TABLE IF NOT EXISTS zones (
      id BIGSERIAL PRIMARY KEY,
      schedule_id BIGINT NOT NULL REFERENCES schedules(id),
      zone VARCHAR(20) NOT NULL,
      grade VARCHAR(10) NOT NULL,
      seat_count INT NOT NULL,
      UNIQUE(schedule_id, zone)
  );

  -- 좌석
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
  -- 시연 데이터
  -- =============================================

  -- 관리자 + 테스트 유저 (비밀번호: bcrypt "password")
  INSERT INTO users (email, password, name, phone, role) VALUES
      ('admin@test.com', '$2b$10$PBwV7jUmNIf1L7sDJJgWNe82lJclUrxVZlN8jdwf0TGE51J0THE1W', '관리자', '010-0000-0000', 'ADMIN'),
      ('user@test.com', '$2b$10$b4/OZ/TPWsYYHXCFNRhbhuLpYwuTpsuqvcVI7qD6wYbKhqUBd45cC', '시연유저', '010-1111-1111', 'USER')
  ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password;

  -- 데모 유저 100명 (대기열 시뮬레이션용, 비밀번호: "password")
  INSERT INTO users (email, password, name, phone, role)
  SELECT
      'demo' || n || '@test.com',
      '$2b$10$b4/OZ/TPWsYYHXCFNRhbhuLpYwuTpsuqvcVI7qD6wYbKhqUBd45cC',
      '대기유저' || n,
      '010-' || LPAD((n / 100)::text, 4, '0') || '-' || LPAD((n % 10000)::text, 4, '0'),
      'USER'
  FROM generate_series(1, 100) n
  ON CONFLICT (email) DO NOTHING;

  -- =============================================
  -- 공연 6개
  -- =============================================
  INSERT INTO concerts (title, artist, venue) VALUES
      ('2026 아이유 콘서트 : The Golden Hour', '아이유', '잠실종합운동장'),
      ('2026 BLACKPINK WORLD TOUR', 'BLACKPINK', '올림픽공원 체조경기장'),
      ('2026 BTS YET TO COME', 'BTS', '고척스카이돔'),
      ('LUCY LIVE IN SEOUL', 'LUCY', '올림픽홀'),
      ('2026 aespa SYNK : PARALLEL LINE', 'aespa', 'KSPO DOME'),
      ('SEVENTEEN WORLD TOUR BE THE SUN', 'SEVENTEEN', '잠실실내체육관')
  ON CONFLICT DO NOTHING;

  -- =============================================
  -- 스케줄 (7개)
  -- =============================================
  -- schedule 1: 아이유 OPEN (시연 메인 — 라이브 트랙 데모용)
  -- schedule 2: 블랙핑크 OPEN
  -- schedule 3: BTS UPCOMING
  -- schedule 4: LUCY OPEN
  -- schedule 5: aespa OPEN
  -- schedule 6: 세븐틴 UPCOMING
  -- schedule 7: 아이유 CLOSED (과거 공연)
  INSERT INTO schedules (concert_id, date_time, total_seats, ticket_open_at, ticket_close_at, status) VALUES
      (1, '2026-03-15 19:00:00', 5100, NOW() - INTERVAL '5 minutes', NOW() + INTERVAL '7 days', 'OPEN'),
      (2, '2026-04-20 18:00:00', 5100, NOW() - INTERVAL '3 minutes', NOW() + INTERVAL '7 days', 'OPEN'),
      (3, '2026-05-10 19:00:00', 5100, NOW() + INTERVAL '7 days',    NOW() + INTERVAL '14 days', 'UPCOMING'),
      (4, '2026-03-22 19:00:00', 3000, NOW() - INTERVAL '2 minutes', NOW() + INTERVAL '7 days', 'OPEN'),
      (5, '2026-04-05 18:00:00', 5100, NOW() - INTERVAL '4 minutes', NOW() + INTERVAL '7 days', 'OPEN'),
      (6, '2026-05-20 18:00:00', 5100, NOW() + INTERVAL '14 days',   NOW() + INTERVAL '21 days', 'UPCOMING'),
      (1, '2026-01-10 19:00:00', 5100, '2025-12-01 20:00:00', '2026-01-10 18:00:00', 'CLOSED')
  ON CONFLICT DO NOTHING;

  -- =============================================
  -- 등급 (각 스케줄별 VIP/S/A)
  -- =============================================
  INSERT INTO grades (schedule_id, grade, price) VALUES
      -- 아이유 (schedule 1)
      (1, 'VIP', 154000), (1, 'S', 121000), (1, 'A', 88000),
      -- 블랙핑크 (schedule 2)
      (2, 'VIP', 176000), (2, 'S', 143000), (2, 'A', 110000),
      -- BTS (schedule 3)
      (3, 'VIP', 165000), (3, 'S', 132000), (3, 'A', 99000),
      -- LUCY (schedule 4)
      (4, 'VIP', 132000), (4, 'S', 99000),  (4, 'A', 66000),
      -- aespa (schedule 5)
      (5, 'VIP', 143000), (5, 'S', 110000), (5, 'A', 77000),
      -- 세븐틴 (schedule 6)
      (6, 'VIP', 154000), (6, 'S', 121000), (6, 'A', 88000),
      -- 아이유 과거 (schedule 7)
      (7, 'VIP', 154000), (7, 'S', 121000), (7, 'A', 88000)
  ON CONFLICT (schedule_id, grade) DO NOTHING;

  -- =============================================
  -- 구역 (OPEN 스케줄 1,2,4,5에 풀 구역, 나머지는 축소)
  -- VIP: A~H(200석) + 3~13(100석) = 2700석
  -- S: 1,2,14,15,27~40(18개 × 100석) = 1800석
  -- A: 24~26,41~43(6개 × 100석) = 600석
  -- =============================================

  -- 풀 구역 생성 함수 (schedule 1, 2, 5)
  INSERT INTO zones (schedule_id, zone, grade, seat_count)
  SELECT s.id, z.zone, z.grade, z.seat_count
  FROM (VALUES (1), (2), (5)) AS s(id)
  CROSS JOIN (
      VALUES
          ('A','VIP',200),('B','VIP',200),('C','VIP',200),('D','VIP',200),
          ('E','VIP',200),('F','VIP',200),('G','VIP',200),('H','VIP',200),
          ('3','VIP',100),('4','VIP',100),('5','VIP',100),('6','VIP',100),
          ('7','VIP',100),('8','VIP',100),('9','VIP',100),('10','VIP',100),
          ('11','VIP',100),('12','VIP',100),('13','VIP',100),
          ('1','S',100),('2','S',100),('14','S',100),('15','S',100),
          ('27','S',100),('28','S',100),('29','S',100),('30','S',100),
          ('31','S',100),('32','S',100),('33','S',100),('34','S',100),
          ('35','S',100),('36','S',100),('37','S',100),('38','S',100),
          ('39','S',100),('40','S',100),
          ('24','A',100),('25','A',100),('26','A',100),
          ('41','A',100),('42','A',100),('43','A',100)
  ) AS z(zone, grade, seat_count)
  ON CONFLICT (schedule_id, zone) DO NOTHING;

  -- LUCY (schedule 4) — 소규모 공연장 (구역 축소)
  INSERT INTO zones (schedule_id, zone, grade, seat_count) VALUES
      (4, 'A', 'VIP', 200), (4, 'B', 'VIP', 200), (4, 'C', 'VIP', 200), (4, 'D', 'VIP', 200),
      (4, '3', 'VIP', 100), (4, '4', 'VIP', 100), (4, '5', 'VIP', 100), (4, '6', 'VIP', 100),
      (4, '1', 'S', 100), (4, '2', 'S', 100), (4, '14', 'S', 100), (4, '15', 'S', 100),
      (4, '27', 'S', 100), (4, '28', 'S', 100), (4, '29', 'S', 100), (4, '30', 'S', 100),
      (4, '24', 'A', 100), (4, '25', 'A', 100), (4, '41', 'A', 100), (4, '42', 'A', 100)
  ON CONFLICT (schedule_id, zone) DO NOTHING;

  -- UPCOMING/CLOSED 스케줄 (3, 6, 7) — 최소 구역만
  INSERT INTO zones (schedule_id, zone, grade, seat_count)
  SELECT s.id, z.zone, z.grade, z.seat_count
  FROM (VALUES (3), (6), (7)) AS s(id)
  CROSS JOIN (
      VALUES
          ('A','VIP',200),('B','VIP',200),('C','VIP',200),
          ('1','S',100),('2','S',100),
          ('24','A',100),('25','A',100)
  ) AS z(zone, grade, seat_count)
  ON CONFLICT (schedule_id, zone) DO NOTHING;

  -- =============================================
  -- 좌석 생성 (OPEN 스케줄에 구역당 20석씩)
  -- =============================================

  -- schedule 1 (아이유 — 시연 메인)
  INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
  SELECT 1, z.grade, z.zone, n::text, g.price, 'AVAILABLE'
  FROM zones z
  JOIN grades g ON g.schedule_id = z.schedule_id AND g.grade = z.grade
  CROSS JOIN generate_series(1, 20) n
  WHERE z.schedule_id = 1
  ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

  -- schedule 2 (블랙핑크)
  INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
  SELECT 2, z.grade, z.zone, n::text, g.price, 'AVAILABLE'
  FROM zones z
  JOIN grades g ON g.schedule_id = z.schedule_id AND g.grade = z.grade
  CROSS JOIN generate_series(1, 20) n
  WHERE z.schedule_id = 2
  ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

  -- schedule 4 (LUCY)
  INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
  SELECT 4, z.grade, z.zone, n::text, g.price, 'AVAILABLE'
  FROM zones z
  JOIN grades g ON g.schedule_id = z.schedule_id AND g.grade = z.grade
  CROSS JOIN generate_series(1, 20) n
  WHERE z.schedule_id = 4
  ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;

  -- schedule 5 (aespa)
  INSERT INTO seats (schedule_id, grade, zone, seat_number, price, status)
  SELECT 5, z.grade, z.zone, n::text, g.price, 'AVAILABLE'
  FROM zones z
  JOIN grades g ON g.schedule_id = z.schedule_id AND g.grade = z.grade
  CROSS JOIN generate_series(1, 20) n
  WHERE z.schedule_id = 5
  ON CONFLICT (schedule_id, zone, seat_number) DO NOTHING;
