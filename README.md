# FairTicket-BE

대용량 트래픽 처리 티켓팅 시스템 백엔드

## 기술 스택

- Java 21
- Spring Boot 3.5.10
- Spring WebFlux (Reactive)
- R2DBC + PostgreSQL
- Redis
- Kafka
- Gradle

## 로컬 개발 환경 설정

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

실행되는 서비스:
| 서비스 | 포트 |
|--------|------|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Zookeeper | 2181 |

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 IDE(IntelliJ)에서 `FairticketBeApplication.java` 실행

### 4. 서버 확인

```bash
curl http://localhost:8080/actuator/health
```

## Docker 명령어

```bash
# 컨테이너 실행
docker-compose up -d

# 컨테이너 중지
docker-compose down

# 로그 확인
docker-compose logs -f

# 볼륨 포함 삭제
docker-compose down -v
```
