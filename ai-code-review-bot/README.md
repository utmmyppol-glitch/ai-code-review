# 🔍 AI Code Review Bot — GitHub PR 자동 코드 리뷰 & 한글 요약

> PR이 올라오면 AI가 코드를 분석하고, 한글로 리뷰 + 위험도를 판단합니다.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![GitHub](https://img.shields.io/badge/GitHub_API-181717?style=flat-square&logo=github&logoColor=white)
![OpenAI](https://img.shields.io/badge/OpenAI_API-412991?style=flat-square&logo=openai&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-010101?style=flat-square&logo=socketdotio&logoColor=white)

---

## 📌 프로젝트 소개

GitHub Pull Request가 생성되면 **Webhook**으로 자동 감지하여 AI가 코드를 분석합니다.
변경된 코드의 품질, 보안 취약점, 성능 이슈를 검토하고 **한글로 리뷰 코멘트**를 자동 작성합니다.

### 핵심 기능

- **PR Webhook 자동 감지**: GitHub Webhook으로 PR 생성/수정 이벤트 실시간 수신
- **AI 코드 분석**: OpenAI API로 변경된 코드의 품질·보안·성능 리뷰
- **한글 리뷰 작성**: GitHub API로 PR에 한글 코멘트 자동 작성
- **위험도 판단**: 변경 사항의 위험 수준을 🟢🟡🔴 3단계로 분류
- **PR 요약**: 변경 내용을 한글로 간결하게 요약
- **실시간 대시보드**: WebSocket으로 리뷰 현황 실시간 표시
- **리뷰 통계**: 리포지토리별 코드 품질 트렌드 추적

---

## 🏗 아키텍처

```
[GitHub] ── PR 생성 이벤트 (Webhook)
    ↓
[Spring Boot 서버]
    ├── Webhook 수신 + 검증 (HMAC SHA-256)
    ├── GitHub API → PR diff 조회
    ├── AI 코드 분석 (OpenAI API)
    │     ├── 코드 품질 리뷰
    │     ├── 보안 취약점 검사
    │     ├── 성능 이슈 검토
    │     └── 위험도 판단 (LOW/MEDIUM/HIGH)
    ├── GitHub API → 리뷰 코멘트 작성
    └── WebSocket → 대시보드 실시간 업데이트
```

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3, Spring WebSocket |
| AI | OpenAI GPT API (코드 리뷰 + 요약) |
| GitHub | GitHub REST API v3, Webhook (HMAC 검증) |
| Realtime | WebSocket + STOMP Protocol |
| DB | MySQL 8.0 (리뷰 이력 저장) |
| Auth | GitHub App / Personal Access Token |

---

## 📁 프로젝트 구조

```
src/main/java/com/bomin/codereview/
├── config/          # WebSocket, OpenAI, GitHub 설정
├── controller/      # REST API + Webhook 엔드포인트
├── dto/             # Request/Response DTO
├── model/           # Entity (PullRequest, ReviewComment, Repository)
├── service/         # AI 리뷰, GitHub 연동, 통계
├── webhook/         # GitHub Webhook 핸들러 + HMAC 검증
└── CodeReviewBotApplication.java
```

---

## 🚀 실행 방법

```bash
# 1. 환경변수 설정
export OPENAI_API_KEY=your_openai_api_key
export GITHUB_TOKEN=your_github_personal_access_token
export GITHUB_WEBHOOK_SECRET=your_webhook_secret

# 2. MySQL 실행
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=code_review mysql:8.0

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. GitHub Webhook 설정
# Repository → Settings → Webhooks → Add webhook
# Payload URL: https://your-server.com/api/webhook/github
# Content type: application/json
# Secret: your_webhook_secret
# Events: Pull requests
```

---

## 📡 API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/webhook/github` | GitHub Webhook 수신 |
| GET | `/api/reviews` | 리뷰 목록 조회 |
| GET | `/api/reviews/{prId}` | PR별 리뷰 상세 |
| POST | `/api/reviews/{prId}/re-review` | 재리뷰 요청 |
| GET | `/api/stats` | 리뷰 통계 |
| GET | `/api/stats/{repo}` | 리포지토리별 통계 |
| WS | `/ws/reviews` | 실시간 리뷰 알림 |

---

## 📄 라이선스

MIT License

---

## 👨‍💻 만든 사람

**김보민** — [@utmmyppol-glitch](https://github.com/utmmyppol-glitch)
