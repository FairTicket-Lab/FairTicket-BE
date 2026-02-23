# FairTicket-BE
## 대용량 트래픽 처리 공정 티켓팅 시스템 백엔드
> Spring WebFlux + Redis + Kafka 기반 리액티브 티켓팅 플랫폼 <br>
> 듀얼 트랙(사전 예매/실시간 선착순) 방식으로 트래픽을 분산하고 공정성을 강화하는 구조 <br>
> 25.01.20 - 25.02.19

---

## 🎥 시연 영상
<p align="center">
  <a href="https://youtu.be/c5Nu2MAlWs8">
    <img src="https://img.youtube.com/vi/c5Nu2MAlWs8/0.jpg" alt="시연 영상" width="600"/>
  </a>
</p>

---

## 📱 Contributors
| 양정우 (PM / PL) <br> [@mrangjw](https://github.com/mrangjw) | 권세빈 (Frontend) <br> [@sebeeeen](https://github.com/sebeeeen) | 임수현 (Backend) <br> [@suhyenim](https://github.com/suhyenim) |
|:---:|:---:|:---:|
| <img width="150" src="https://avatars.githubusercontent.com/u/157506327?v=4"/> | <img width="150" src="https://avatars.githubusercontent.com/u/128478309?v=4"/> | <img width="150" src="https://avatars.githubusercontent.com/u/100345983?v=4"/> |
| 프로젝트 기획 및 총괄<br>Redis 대기열 시스템 구현<br>JWT 인증 및 보안<br>Rate Limiting | 결제 시스템 연동 (PortOne)<br>Kafka 이벤트 처리<br>문서 자료 제작 | 예매 시스템 개발 (듀얼 트랙)<br>좌석 배정 로직 구현<br>동시성 제어 (분산 락)<br>인프라 구성 |

<br/>

## 💡 프로젝트 배경

### 해결하고자 하는 문제
- ⚡ **트래픽 집중**: 오픈 후 1~2분 내 트래픽의 90%가 집중, 서버 다운·504 에러·결제 중단 발생
- 🎫 **불공정한 경쟁**: 2024 예스24 실측 기준 매크로 트래픽 73.5%, 정상 사용자 26.5% — "빠른 클릭 = 좋은 자리" 구조는 매크로·고성능 장비에만 유리
- 🔄 **좌석 선택 vs 확보 확실성 충돌**: 원하는 좌석 선택 또는 확보 확실성 중 하나만 가능한 기존 구조
- 📋 **대기 불확실성**: 무한 새로고침 → 이탈 → 서버 추가 부하의 악순환

### 솔루션: 듀얼 트랙 예매 시스템
- **Lottery Track (사전 예매)**: 등급 선택 → 결제 → Live Track 마감 후 Fisher-Yates 알고리즘으로 좌석 랜덤 배정 (좌석 확보 확실성 보장, 매크로 무력화)
- **Live Track (실시간 선착순)**: Redis 대기열 진입 → 순번 대기 → 좌석 직접 선택 → 10분 홀드 → 결제 (좌석 선택권 보장)

<br/>

## 🔧 Tech Stacks
| Category | TechStack |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5, Spring WebFlux (Reactive) |
| Database | PostgreSQL 15, R2DBC (Non-blocking) |
| Cache / Queue | Redis 7 (Reactive), Redisson |
| Message Broker | Apache Kafka |
| Security | Spring Security, JWT (jjwt 0.12) |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Monitoring | Prometheus, Grafana, Spring Actuator |
| Load Test | k6 |
| Infra | Docker Compose |
| Build | Gradle |

<br/>

## 🏗️ 핵심 구현 기능

### 🎰 듀얼 트랙 예매 시스템
- **Lottery Track (사전 예매)**: 등급 선택 후 사전 결제 → Live Track 마감 후 Fisher-Yates 셔플 알고리즘으로 좌석 랜덤 배정 (1인당 최대 2장, 속도가 아닌 운으로 결정 → 매크로 무력화)
- **Live Track (실시간 선착순)**: Redis 대기열 진입 → 순번 대기 → 좌석 풀에서 직접 좌석 선택 → 10분 홀드 → 결제 (1인당 최대 4장, 원하는 자리 직접 선택)

### 📋 Redis 기반 대기열 시스템
- `ZADD` — Redis Sorted Set으로 대기열 순번 관리
- `ZRANK` — 3초 Polling으로 실시간 순번 및 예상 대기시간 전달
- 순번 도달 시 TTL 5분 입장 토큰 발급, 토큰 검증 후 예매 페이지 진입
- 하트비트 30초 미응답 시 자동 `ZREM` → 이탈 처리
- 스케줄러 기반 배치 입장 처리 (`QueueScheduler`)
- Redis 기반 Rate Limiting 필터로 API 과부하 방지

### 💺 좌석 관리 및 동시성 제어 (4중 방어)
1. **Redis 원자적 제거 (`SREM`)**: 좌석 풀에서 선점 시도, 이미 선점된 좌석이면 즉시 예약 거절
2. **NX 원자적 홀드**: TTL 설정으로 좌석 임시 잠금, 요청 중 단 1명만 홀드 성공
3. **Redisson 분산 락**: 배치 입장 처리 시 다중 서버 환경에서도 단일 실행 보장
4. **DB 유니크 제약**: Redis 예외 상황에서도 DB 레벨에서 중복 차단
- **Fisher-Yates Shuffle Algorithm**: 구역별 좌석 수 차이로 인한 불균등 배정 방지, O(n) 선형 시간 in-place 셔플로 모든 좌석에 동일한 선택 확률 보장
- 앱 시작 시 좌석 풀 자동 초기화 (`SeatPoolInitializer`)

### 💳 결제 시스템
- **Lottery Track**: 결제 마감 시각(ticketOpenAt - 15분) 이전까지 결제 완료 필요, 미결제 시 1분마다 자동 취소, 결제 완료 후 Live Track 종료 1시간 뒤 Fisher-Yates로 좌석 배정
- **Live Track**: 좌석 선점 즉시 10분 홀드 타이머 시작, 시간 내 결제 완료 시 즉시 좌석 SOLD 전환, 미결제 시 1분마다 홀드 자동 해제
- PortOne API 연동 결제 처리
- 5분 결제 타이머 기반 시간 제한 (`PaymentTimerService`)
- 결제 만료 자동 처리 스케줄러 (`PaymentExpiryScheduler`)
- Kafka 기반 환불 비동기 처리 (`RefundConsumer`)

### 🔐 인증 / 보안
- JWT 기반 인증 (`JwtProvider` + `JwtAuthenticationFilter`)
- Spring Security WebFlux 설정
- Redis 기반 Rate Limiting (`RateLimitFilter`)

### 📡 이벤트 기반 아키텍처
- Kafka를 활용한 결제/환불 이벤트 비동기 처리
- Redis Keyspace Notification 기반 키 만료 감지 (`RedisKeyExpiredListener`)
- 좌석 배정 비동기 처리 (`SeatAssignmentConsumer`)

<br/>

## 📁 Foldering
```
📂 com.fairticket
┣ 📂 domain
┃ ┣ 📂 auth              # 인증 (로그인/회원가입)
┃ ┃ ┣ 📂 controller
┃ ┃ ┣ 📂 dto
┃ ┃ ┗ 📂 service
┃ ┣ 📂 concert            # 공연/스케줄 관리
┃ ┃ ┣ 📂 controller
┃ ┃ ┣ 📂 dto
┃ ┃ ┣ 📂 entity
┃ ┃ ┣ 📂 repository
┃ ┃ ┗ 📂 service
┃ ┣ 📂 payment            # 결제 시스템
┃ ┃ ┣ 📂 controller
┃ ┃ ┣ 📂 dto
┃ ┃ ┣ 📂 entity
┃ ┃ ┣ 📂 repository
┃ ┃ ┗ 📂 service
┃ ┣ 📂 queue              # 대기열 시스템
┃ ┃ ┣ 📂 config
┃ ┃ ┣ 📂 controller
┃ ┃ ┣ 📂 dto
┃ ┃ ┗ 📂 service
┃ ┣ 📂 reservation        # 예매 (사전 예매/실시간 선착순 트랙)
┃ ┃ ┣ 📂 constants
┃ ┃ ┣ 📂 controller
┃ ┃ ┣ 📂 dto
┃ ┃ ┣ 📂 entity
┃ ┃ ┣ 📂 repository
┃ ┃ ┗ 📂 service
┃ ┣ 📂 seat               # 좌석 관리
┃ ┃ ┣ 📂 controller
┃ ┃ ┣ 📂 dto
┃ ┃ ┣ 📂 entity
┃ ┃ ┣ 📂 repository
┃ ┃ ┗ 📂 service
┃ ┗ 📂 user               # 사용자 관리
┃   ┣ 📂 entity
┃   ┗ 📂 repository
┣ 📂 global
┃ ┣ 📂 config             # Redis, Kafka, Security, Swagger 설정
┃ ┣ 📂 exception          # 글로벌 예외 처리
┃ ┣ 📂 security           # JWT, Rate Limiting
┃ ┗ 📂 util               # 유틸리티
┗ 📂 infra
  ┗ 📂 redis              # Redis 키 만료 리스너
```

<br/>

## 🛠️ 로컬 개발 환경 설정

### 사전 요구사항
- JDK 21+
- Docker Desktop

### 1. 레포지토리 클론
```bash
git clone https://github.com/FairTicket-Lab/FairTicket-BE.git
cd FairTicket-BE
```

### 2. Docker 컨테이너 실행
```bash
docker-compose up -d
```

| 서비스 | 포트 |
|--------|------|
| PostgreSQL | 5433 |
| Redis | 6379 |
| Kafka | 9092 |
| Zookeeper | 2181 |

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 4. API 문서 확인
```
http://localhost:8080/webjars/swagger-ui/index.html
```

---
> #### &copy; 2026 FairTicket | Fair Ticketing Platform for Everyone | All Rights Reserved
