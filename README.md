# 우리둘 (Uridul)

두 사람만 쓰는 커플 앱. 초대 링크를 받은 상대 한 명과만 연결되는 공간에서 디데이·기념일·공유 캘린더를 함께 봅니다.

- 대상 기기: Galaxy S26 Ultra, Galaxy Z Fold8 (Android 전용)
- 기술: Kotlin · Jetpack Compose · Glance 위젯 · Firebase (Auth, Firestore, Hosting)

## 폴더

| 경로 | 내용 |
|---|---|
| [`android/`](android/) | Android Studio 프로젝트 |
| [`firebase/`](firebase/) | Firestore 보안 규칙, 초대 링크 웹페이지 |
| [`docs/`](docs/) | [개발 검토안](docs/커플앱_개발검토안.md), [MVP 설정 가이드](docs/MVP_설정가이드.md), [PowerShell 진행 가이드](docs/PowerShell_진행가이드.md) |

## 진행 상황

- [x] 1단계 MVP: Google 로그인, 초대 링크 연결, 디데이 홈, 디데이 위젯
  - [x] Firebase 연결 (`rabbithooni-5d8f4`, Firestore 서울, 초대 페이지 https://rabbithooni-5d8f4.web.app)
  - [x] Fold8 설치, 초대 링크 인증(verified) 확인 (2026-10-27)
  - [ ] S26 Ultra 설치, 두 폰 연결 테스트
- [ ] 2단계: 공유 캘린더, 상대 활동 푸시 알림
  - [x] 공유 캘린더 (월 달력, 일정 추가·수정·삭제, 여러 날 일정, 기념일 하트 표시)
  - [x] 위젯: 하트 2×2 "사귄 날" (잠금 화면 지원 기기), 공유 일정 목록
  - [x] S26 Ultra 화면(에뮬레이터 1080×2340·450dpi), 폴드 펼침 화면 배치 점검, 큰 글꼴·다크 모드 점검
  - [x] Fold8 실기기 점검 (2026-10-27): 커버·펼침 화면, 일정 추가·수정·삭제와 서버 저장, 하트·일정·디데이 위젯
  - [x] 하트 위젯 잠금 화면: One UI 기본 목록에는 삼성 앱만 나와서 없음 → 삼성 **굿락 LockStar** 위젯 목록에서 추가 가능 확인 (2026-10-27)
  - [ ] 상대 활동 푸시 알림 (FCM, 유료 요금제 필요)
- [ ] 3단계: 기념일 알림 (음력 포함)
- [ ] 4단계: 폴드 화면 최적화 마무리
- [ ] 5단계: 사진·테마, 배포

## 빌드

```bash
cd android && ./gradlew assembleDebug
```

Firebase 연결부터 폰 설치까지는 [PowerShell 진행 가이드](docs/PowerShell_진행가이드.md)를 따라 명령어를 복사·실행하면 됩니다. `google-services.json`은 저장소에 포함하지 않습니다.
