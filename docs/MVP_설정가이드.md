# 우리둘 MVP 설정 가이드

1단계 MVP(로그인 · 초대 링크 연결 · 디데이 홈 · 디데이 위젯)를 두 폰에서 실행하기까지의 순서입니다.
코드는 이미 빌드·테스트가 끝난 상태이고, **Firebase 프로젝트 연결만 하면** 동작합니다.

> 💡 **PowerShell 명령어로 따라 하려면 [PowerShell 진행 가이드](PowerShell_진행가이드.md)를 보세요.** 이 문서는 전체 개요(콘솔 화면 위주)입니다.

---

## 0. 폴더 구성

```
D:\APP_TEST\
├─ docs\        검토안, 이 가이드
├─ android\     Android Studio 프로젝트 (Kotlin + Compose + Glance)
└─ firebase\    Firestore 보안 규칙, 초대 링크 웹페이지(Hosting)
```

## 1. Firebase 프로젝트 만들기 (약 10분)

1. https://console.firebase.google.com → **프로젝트 추가** → 이름 예: `uridul-couple` (Google 애널리틱스는 꺼도 됨)
2. **Android 앱 추가**
   - 패키지 이름: `com.uridul.app`
   - 디버그 서명 인증서 SHA-1 (이 PC의 디버그 키):
     ```
     5E:1A:27:8E:E7:8C:48:BF:AB:E2:3B:3D:3A:DD:1D:3E:0C:D3:11:51
     ```
   - 프로젝트 설정 → 내 앱 → **SHA-256도 추가**:
     ```
     2D:47:4F:9C:E5:D9:12:9B:DC:39:04:33:7D:F6:95:A2:3B:20:F9:E8:7E:D8:32:8C:71:A3:85:48:36:8E:D9:90
     ```
3. **Authentication** → 시작하기 → 로그인 방법 → **Google 사용 설정** (지원 이메일 선택)
4. **2~3번을 마친 뒤** 프로젝트 설정에서 `google-services.json`을 **다시** 다운로드 → `D:\APP_TEST\android\app\` 에 넣기
   (Google 로그인을 켠 뒤 받아야 로그인에 필요한 값이 들어 있습니다)
5. **Firestore Database** → 데이터베이스 만들기 → 위치 **asia-northeast3 (서울)** → 프로덕션 모드
   → **규칙** 탭에 `firebase/firestore.rules` 내용을 그대로 붙여넣고 **게시**

> 요금: 무료(Spark) 요금제로 충분합니다. 서버 함수 없이 보안 규칙만으로 초대 연결을 처리하도록 만들었습니다.

## 2. 초대 링크 웹페이지 배포 (Firebase Hosting)

초대 링크(`https://<프로젝트ID>.web.app/invite/...`)를 누르면 앱이 바로 열리게 하는 설정입니다.

1. Node.js LTS 설치: https://nodejs.org
2. 새 터미널에서:
   ```bash
   npm install -g firebase-tools
   ```
   ```bash
   firebase login
   ```
3. `D:\APP_TEST\firebase\.firebaserc` 의 `your-project-id`를 실제 프로젝트 ID로 바꾸기
4. 배포 (보안 규칙도 함께 올라갑니다):
   ```bash
   cd /d/APP_TEST/firebase && firebase deploy --only hosting,firestore:rules
   ```

> Hosting을 아직 안 했어도 테스트는 가능합니다. 받은 링크를 앱의 **"초대받았어요"** 칸에 붙여넣으면 연결됩니다.

## 3. 앱 빌드 · 설치

**Android Studio 사용 (권장)**
1. Android Studio → Open → `D:\APP_TEST\android`
2. 폰: 설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드번호 7번 탭** → 개발자 옵션 → **무선 디버깅** 켜기
3. Android Studio에서 기기 페어링 후 ▶ Run

**명령줄**
```bash
cd /d/APP_TEST/android && ./gradlew installDebug
```

> ⚠️ **두 폰 모두 이 PC에서 빌드한 APK**를 설치해야 합니다. 초대 링크 자동 열기(App Links)는 이 PC의 디버그 키(위 SHA-256)로 서명된 앱만 인증됩니다.
> 여자친구 폰에 원격 설치하려면 `android/app/build/outputs/apk/debug/app-debug.apk` 를 보내거나, 다음 단계에서 Firebase App Distribution을 붙이면 됩니다.

## 4. 사용 순서

1. **A 폰:** 앱 실행 → Google 로그인 → 시작일 선택 → **만들기**
2. **A 폰:** 홈의 **초대 링크 보내기** → 카카오톡으로 전송
3. **B 폰:** 링크 클릭 → 앱이 열림 → Google 로그인 → **연결하기**
4. 두 폰 모두 홈 화면 길게 누르기 → **위젯** → **우리둘 디데이** 추가

## 5. 갤럭시 필수 설정 (위젯 자정 갱신)

설정 → 배터리 → **백그라운드 사용 제한** → "사용하지 않는 앱을 절전 상태로 전환" 끄기, 또는 **절전 예외 앱**에 우리둘 추가.
이 설정을 하지 않으면 One UI가 앱을 재워 위젯 숫자가 자정에 바로 바뀌지 않을 수 있습니다.

## 6. 확인 체크리스트

- [ ] A가 만든 링크로 B가 연결되면, 같은 링크를 다시 열었을 때 거부되는가
- [ ] 세 번째 계정으로 링크를 열면 거부되는가
- [ ] 시작일을 바꾸면 상대 폰 홈 화면에도 즉시 반영되는가
- [ ] 위젯 2×2 / 4×1 / 4×2 크기별로 모양이 바뀌는가
- [ ] Fold8: 커버에서 앱 사용 중 펼치면 2단 레이아웃으로 바뀌고 내용이 유지되는가
- [ ] Fold8: 커버 홈과 메인 홈 위젯이 모두 잘리지 않는가
- [ ] 글꼴 크기 최대에서 디데이 숫자가 잘리지 않는가
- [ ] 자정이 지나면 위젯 숫자가 바뀌는가

## 7. MVP에서 검토안과 달라진 점

| 항목 | 검토안 | MVP 구현 | 이유 |
|---|---|---|---|
| 초대 연결 처리 | Cloud Functions | **Firestore 보안 규칙**으로 처리 | Node.js·유료 요금제 없이 동작. 보안 수준은 같음(1회용·24시간·2명 잠금) |
| 위젯 데이터 갱신 | 상대 변경 시 즉시 | 앱을 열 때 + 자정 | 상대 변경 즉시 반영은 2단계 푸시(FCM)와 함께 구현 |

## 8. 알려진 제한 (다음 단계에서 처리)

- 연결 해제·데이터 삭제 기능 없음
- 상대가 시작일을 바꿔도 내 위젯은 내가 앱을 열 때 갱신됨 (2단계 FCM으로 해결 예정)
- 릴리스 서명 키 미설정 (현재 디버그 키로 배포)
