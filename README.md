# 우리둘 (Uridul)

두 사람만 쓰는 커플 앱. 초대 링크를 받은 상대 한 명과만 연결되는 공간에서 디데이·기념일·공유 캘린더를 함께 봅니다.

- 대상 기기: Galaxy S26 Ultra, Galaxy Z Fold8 (Android 전용)
- 기술: Kotlin · Jetpack Compose · Glance 위젯 · Firebase (Auth, Firestore, Hosting)

## 폴더

| 경로 | 내용 |
|---|---|
| [`android/`](android/) | Android Studio 프로젝트 |
| [`firebase/`](firebase/) | Firestore 보안 규칙, 초대 링크 웹페이지 |
| [`docs/`](docs/) | [개발 검토안](docs/커플앱_개발검토안.md), [MVP 설정 가이드](docs/MVP_설정가이드.md) |

## 진행 상황

- [x] 1단계 MVP: Google 로그인, 초대 링크 연결, 디데이 홈, 디데이 위젯
- [ ] 2단계: 공유 캘린더, 상대 활동 푸시 알림
- [ ] 3단계: 기념일 알림 (음력 포함)
- [ ] 4단계: 폴드 화면 최적화 마무리
- [ ] 5단계: 사진·테마, 배포

## 빌드

```bash
cd android && ./gradlew assembleDebug
```

Firebase 연결 방법은 [MVP 설정 가이드](docs/MVP_설정가이드.md)를 참고하세요. `google-services.json`은 저장소에 포함하지 않습니다.
