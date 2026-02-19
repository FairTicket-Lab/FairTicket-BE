# FairTicket-BE
## 대용량 트래픽 처리 공정 티켓팅 시스템 백엔드
> Spring WebFlux + Redis + Kafka 기반 리액티브 티켓팅 플랫폼 <br>
> 듀얼 트랙(추첨/선착순) 예매 시스템으로 공정한 티켓 구매 환경 제공 <br>
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
| 프로젝트 기획 및 총괄<br>백엔드 API 설계 및 구현<br>듀얼 트랙 예매 시스템 개발<br>Redis 대기열 및 동시성 제어 | 프론트엔드 개발<br>Vue.js 기반 UI/UX 구현<br>실시간 대기열 화면 개발<br>좌석 선택 인터페이스 구현 | 백엔드 API 개발<br>결제 시스템 연동<br>Kafka 이벤트 처리<br>인프라 구성 및 배포 |

<br/>

## 💡 프로젝트 배경

### 해결하고자 하는 문제
- 🎫 **불공정한 티켓팅**: 매크로/봇에 의한 티켓 선점으로 일반 소비자의 구매 기회 박탈
- ⚡ **대용량 트래픽**: 인기 공연 오픈 시 수만 명 동시 접속으로 인한 서버 과부하
- 🔄 **단일 방식의 한계**: 선착순만 존재하는 기존 시스템의 공정성 문제
- 💳 **결제 안정성**: 동시 결제 요청에 대한 데이터 정합성 보장 필요

### 솔루션: 듀얼 트랙 예매 시스템
- **Lottery Track (추첨)**: 응모 기간 내 신청 → 공정 추첨 → 당첨자 좌석 자동 배정 → 결제
- **Live Track (선착순)**: Redis 대기열 진입 → 순번 대기 → 좌석 직접 선택 → 결제

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
| Monitoring | Spring Actuator, Prometheus |
| Infra | Docker Compose |
| Build | Gradle |

<br/>

## 🏗️ 핵심 구현 기능

### 🎰 듀얼 트랙 예매 시스템
- **추첨 트랙 (Lottery Track)**: 응모 접수 → 추첨 → 좌석 자동 배정 → 결제 유도
- **선착순 트랙 (Live Track)**: Redis Sorted Set 기반 대기열 → 순번별 입장 → 실시간 좌석 선택

### 📋 Redis 기반 대기열 시스템
- Redis Sorted Set으로 대기열 순번 관리
- JWT 기반 대기열 토큰 발급 및 검증
- 스케줄러 기반 자동 입장 처리 (`QueueScheduler`)
- Rate Limiting 필터로 API 과부하 방지

### 💺 좌석 관리 및 동시성 제어
- Redis Set 기반 좌석 풀(Seat Pool) 관리
- `SREM` 원자적 연산으로 좌석 선점 동시성 제어
- 좌석 임시 홀드 + TTL 기반 자동 만료 (`SeatHoldService`)
- 앱 시작 시 좌석 풀 자동 초기화 (`SeatPoolInitializer`)

### 💳 결제 시스템
- PortOne V2 연동 결제 처리
- 결제 타이머 기반 시간 제한 (`PaymentTimerService`)
- 결제 만료 자동 처리 스케줄러 (`PaymentExpiryScheduler`)
- 결제 검증 스케줄러 (`PaymentVerificationScheduler`)
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
┃ ┣ 📂 reservation        # 예매 (추첨/선착순 트랙)
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
> #### &copy; 2025 FairTicket | Fair Ticketing Platform for Everyone | All Rights Reserved
