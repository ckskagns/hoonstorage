# 우리둘 PowerShell 진행 가이드

Firebase 연결부터 두 폰 설치·테스트까지 **PowerShell 명령어를 복사해서 붙여넣는 방식**으로 진행하는 가이드입니다.
브라우저를 꼭 써야 하는 단계는 두 군데뿐입니다(Firebase 약관 동의 · Google 로그인 켜기). 이 단계는 **🖱️ 브라우저 단계**로 표시했습니다.

> 이 PC 기준으로 확인한 환경: Windows 10, PowerShell 5.1, JDK 21, Android SDK, winget 설치됨

---

## 전체 흐름

| 단계 | 내용 | 소요 |
|---|---|---|
| 0 | PowerShell 사용법 · 공통 변수 | 1분 |
| 1 | 개발 환경 점검 | 1분 |
| 2 | Node.js · Firebase CLI 설치 | 5분 |
| 3 | Firebase 로그인 · 프로젝트 생성 | 5분 |
| 4 | Android 앱 등록 · 서명키(SHA) 등록 | 3분 |
| 5 | 🖱️ Google 로그인 켜기 | 2분 |
| 6 | google-services.json 받기 | 1분 |
| 7 | Firestore 생성 · 규칙/웹페이지 배포 | 3분 |
| 8 | 앱 빌드 | 2~5분 |
| 9 | 폰 연결 (USB 또는 무선) | 5분 |
| 10 | 두 폰에 설치 · 초대 링크 인증 확인 | 3분 |
| 11 | 동작 테스트 | 10분 |
| 12 | 화면 정보 확인 (S26 Ultra / Fold8) | 3분 |
| 13 | 갤럭시 절전 예외 설정 | 2분 |
| 부록 | 문제 해결 · 명령 요약 | |

---

## 0. PowerShell 사용법과 공통 변수

### 0-1. PowerShell 열기
- 시작 버튼 → `PowerShell` 입력 → **Windows PowerShell** 실행 (관리자 권한 필요 없음)
- 코드 블록을 **통째로 복사 → PowerShell 창에 마우스 오른쪽 클릭(붙여넣기) → Enter**

### 0-2. 꼭 알아둘 점 (PowerShell 5.1)
- `&&` 를 쓸 수 없습니다. 이 가이드는 모두 한 줄씩 또는 `;` 로 나눠 두었습니다.
- 이 PC는 PowerShell 스크립트 실행이 기본적으로 막혀 있어서, `npm`·`firebase` 대신 **`npm.cmd`·`firebase.cmd`** 로 실행합니다. (보안 설정을 바꾸지 않아도 됩니다.)
- 한글이 깨져 보이면 먼저 실행:
  ```powershell
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
  ```

### 0-3. 공통 변수 (⚠️ PowerShell 창을 새로 열 때마다 먼저 실행)

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Root = "D:\APP_TEST"
$Sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$adb  = "$Sdk\platform-tools\adb.exe"
$env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User") + ";$Sdk\platform-tools"
$ProjectId = (Get-Content "$Root\firebase\.firebaserc" -Raw | ConvertFrom-Json).projects.default
"ProjectId = $ProjectId"
```

> 3단계 전에는 `ProjectId = your-project-id` 로 나오는 게 정상입니다. 7단계에서 실제 ID로 저장되면 이후에는 자동으로 불러옵니다.

---

## 1. 개발 환경 점검

```powershell
java -version
Test-Path $adb
& $adb version
Set-Location "$Root\android"
.\gradlew.bat --version
```

**정상 결과**
- `openjdk version "21..."`
- `True`
- `Android Debug Bridge version 1.0.41`
- `Gradle 8.14.3`

---

## 2. Node.js · Firebase CLI 설치

### 2-1. Node.js LTS 설치

```powershell
winget install --id OpenJS.NodeJS.LTS -e --accept-source-agreements --accept-package-agreements
```

설치가 끝나면 **현재 창에서 PATH를 새로 읽습니다** (창을 다시 열어도 됩니다):

```powershell
$env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User") + ";$Sdk\platform-tools"
node -v
npm.cmd -v
```

정상: `v22.x.x`, `10.x.x` 같은 버전 번호가 나옵니다.

### 2-2. Firebase CLI 설치

```powershell
npm.cmd install -g firebase-tools
firebase.cmd --version
```

정상: `14.x.x` 같은 버전 번호가 나옵니다.

---

## 3. Firebase 로그인 · 프로젝트 생성

### 3-1. 로그인

```powershell
firebase.cmd login
```

- 브라우저가 열리면 **앱 개발용 Google 계정**으로 로그인 → 허용
- PowerShell에 `Success! Logged in as ...` 가 나오면 완료
- 브라우저가 안 열리면: `firebase.cmd login --no-localhost` (표시되는 주소를 직접 열고 코드를 붙여넣기)

### 3-2. 🖱️ 처음 Firebase를 쓰는 계정이면 약관 동의

https://console.firebase.google.com 을 한 번 열고, 약관 동의 화면이 나오면 동의만 하고 닫습니다.
(이걸 안 하면 다음 명령이 `terms of service` 오류로 실패합니다.)

### 3-3. 프로젝트 생성

프로젝트 ID는 전 세계에서 유일해야 해서 뒤에 숫자를 붙입니다.

```powershell
$ProjectId = "uridul-couple-" + (Get-Random -Minimum 1000 -Maximum 9999)
"만들 프로젝트 ID: $ProjectId"
firebase.cmd projects:create $ProjectId --display-name "Uridul"
```

정상: `Your Firebase project is ready!`

> 실패하면 숫자만 바꿔서 다시 실행하세요. Firebase 콘솔에서 직접 만들었다면 `$ProjectId = "콘솔에서 만든 프로젝트ID"` 로 지정하고 다음으로 넘어가면 됩니다.

### 3-4. 프로젝트 ID를 파일에 저장

이후 모든 단계와 새 PowerShell 창에서 자동으로 쓰이도록 `.firebaserc` 에 저장합니다.

```powershell
$rc = "$Root\firebase\.firebaserc"
$json = "{`n  `"projects`": {`n    `"default`": `"$ProjectId`"`n  }`n}`n"
[System.IO.File]::WriteAllText($rc, $json)
Get-Content $rc
```

---

## 4. Android 앱 등록 · 서명키(SHA) 등록

### 4-1. 앱 등록

```powershell
firebase.cmd apps:create ANDROID "Uridul" --package-name com.uridul.app --project $ProjectId
```

### 4-2. 앱 ID 가져오기

```powershell
$apps  = firebase.cmd apps:list ANDROID --project $ProjectId --json | ConvertFrom-Json
$AppId = ($apps.result | Where-Object { $_.packageName -eq "com.uridul.app" } | Select-Object -First 1).appId
"AppId = $AppId"
```

정상: `AppId = 1:123456789012:android:abcdef...`

### 4-3. 이 PC의 서명키 지문(SHA-1, SHA-256) 읽기

```powershell
$certInfo = keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
$Sha1   = (($certInfo | Select-String 'SHA1:')   -split 'SHA1:')[1].Trim()
$Sha256 = (($certInfo | Select-String 'SHA256:') -split 'SHA256:')[1].Trim()
"SHA1   = $Sha1"
"SHA256 = $Sha256"
```

이 PC 기준 정상 값:
- `SHA1   = 5E:1A:27:8E:E7:8C:48:BF:AB:E2:3B:3D:3A:DD:1D:3E:0C:D3:11:51`
- `SHA256 = 2D:47:4F:9C:E5:D9:12:9B:DC:39:04:33:7D:F6:95:A2:3B:20:F9:E8:7E:D8:32:8C:71:A3:85:48:36:8E:D9:90`

### 4-4. Firebase에 SHA 등록

```powershell
firebase.cmd apps:android:sha:create $AppId ($Sha1 -replace ':','') --project $ProjectId
firebase.cmd apps:android:sha:create $AppId ($Sha256 -replace ':','') --project $ProjectId
firebase.cmd apps:android:sha:list $AppId --project $ProjectId
```

정상: 목록에 SHA_1, SHA_256 두 줄이 보입니다.

> SHA-1이 없으면 Google 로그인이 실패하고, SHA-256은 초대 링크 자동 열기(App Links)에 쓰입니다.

---

## 5. 🖱️ Google 로그인 켜기 (브라우저)

CLI로 켤 수 없는 유일한 설정입니다. 아래 명령으로 해당 화면을 바로 엽니다.

```powershell
Start-Process "https://console.firebase.google.com/project/$ProjectId/authentication/providers"
```

1. **시작하기** 버튼이 보이면 클릭
2. 로그인 제공업체 목록에서 **Google** 클릭
3. **사용 설정** 스위치 켜기
4. **프로젝트 지원 이메일**에서 내 Gmail 선택
5. **저장**

---

## 6. google-services.json 받기

⚠️ **반드시 5단계(Google 로그인 켜기) 후에** 받아야 로그인에 필요한 값이 들어 있습니다.

```powershell
firebase.cmd apps:sdkconfig ANDROID $AppId --project $ProjectId --out "$Root\android\app\google-services.json"
Select-String -Path "$Root\android\app\google-services.json" -Pattern '"client_type": 3' | Select-Object -First 1
```

- 두 번째 명령에서 `"client_type": 3` 이 들어간 줄이 **보이면 정상**입니다.
- 아무것도 안 나오면 5단계가 저장되지 않은 것입니다. 5단계를 확인한 뒤 위 명령을 다시 실행하세요.
- 이미 파일이 있다는 오류가 나면 먼저 지우고 다시 실행:
  ```powershell
  Remove-Item "$Root\android\app\google-services.json"
  ```

> 이 파일은 `.gitignore` 에 등록되어 있어 GitHub에 올라가지 않습니다.

---

## 7. Firestore 생성 · 보안 규칙/초대 웹페이지 배포

### 7-1. Firestore 데이터베이스 만들기 (서울 리전)

```powershell
firebase.cmd firestore:databases:create "(default)" --location asia-northeast3 --project $ProjectId
```

정상: `Successfully created ...` (이미 있다는 오류는 무시해도 됩니다)

### 7-2. 보안 규칙 + 초대 웹페이지 배포

```powershell
Set-Location "$Root\firebase"
firebase.cmd deploy --only "firestore:rules,hosting" --project $ProjectId
```

정상: 마지막에 `Deploy complete!` 와 `Hosting URL: https://<프로젝트ID>.web.app`

### 7-3. 배포 확인

```powershell
(Invoke-WebRequest "https://$ProjectId.web.app/.well-known/assetlinks.json" -UseBasicParsing).Content
(Invoke-WebRequest "https://$ProjectId.web.app/invite/test" -UseBasicParsing).StatusCode
```

정상: 첫 줄에 `com.uridul.app` 과 SHA-256이 담긴 JSON, 둘째 줄에 `200`

브라우저로 초대 페이지 모양 보기:

```powershell
Start-Process "https://$ProjectId.web.app/invite/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
```

---

## 8. 앱 빌드

```powershell
Set-Location "$Root\android"
.\gradlew.bat testDebugUnitTest assembleDebug
```

정상: `BUILD SUCCESSFUL`

APK 위치 확인:

```powershell
Get-Item "$Root\android\app\build\outputs\apk\debug\app-debug.apk" | Select-Object FullName, @{n="MB";e={[math]::Round($_.Length/1MB,1)}}
```

> 빌드 시 `Could not move temporary workspace` 오류가 나면 같은 명령을 한 번 더 실행하세요 (백신이 파일을 잠깐 잡고 있어서 생기는 일시적 오류).

---

## 9. 폰 연결

두 폰 모두 먼저 **개발자 옵션**을 켭니다:
설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드번호를 7번 탭** → (PIN 입력) → "개발자 모드를 켰습니다"

### 방법 A. 무선 디버깅 (권장, 케이블 불필요)

PC와 폰이 **같은 Wi-Fi**에 있어야 합니다.

1. 폰: 설정 → **개발자 옵션** → **무선 디버깅** 켜기 → **페어링 코드로 기기 페어링** 누르기
2. 폰 화면에 나온 `IP주소:포트` 와 `6자리 코드`를 아래에 넣어 실행:

```powershell
& $adb pair 192.168.0.12:37123
```

→ `Enter pairing code:` 가 나오면 6자리 코드 입력 → `Successfully paired`

3. 페어링 창을 닫으면 **무선 디버깅** 화면 상단에 `IP 주소 및 포트`가 보입니다 (페어링 포트와 **다른 번호**). 그 값으로 연결:

```powershell
& $adb connect 192.168.0.12:41234
& $adb devices -l
```

정상: `192.168.0.12:41234  device ... model:SM_xxxx` 처럼 `device` 로 표시

### 방법 B. USB 케이블

1. 폰: 개발자 옵션 → **USB 디버깅** 켜기
2. USB 연결 → 폰에 뜨는 "USB 디버깅을 허용하시겠습니까?" → **허용**
3. 확인:

```powershell
& $adb devices -l
```

> `unauthorized` 로 나오면 폰 화면의 허용 팝업을 누르세요. 아무것도 안 나오면 [Samsung USB 드라이버](https://developer.samsung.com/android-usb-driver)를 설치하세요.

### 9-1. 두 폰을 변수로 지정

두 폰이 동시에 연결되어 있으면 어느 폰에 명령할지 지정해야 합니다. `adb devices -l` 결과의 **첫 번째 열(시리얼 또는 IP:포트)** 을 넣습니다. `model:` 값으로 어떤 폰인지 구분할 수 있습니다.

```powershell
& $adb devices -l
$Fold = "여기에_Fold8_시리얼"
$S26  = "여기에_S26Ultra_시리얼"
```

---

## 10. 두 폰에 설치 · 초대 링크 인증 확인

### 10-1. 설치

```powershell
$Apk = "$Root\android\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $Fold install -r $Apk
& $adb -s $S26  install -r $Apk
```

정상: 각각 `Success`

> **여자친구 폰을 지금 연결할 수 없다면:** 위 APK 파일을 카카오톡 "나에게 보내기" 등으로 전달 → 폰에서 파일 열기 → "출처를 알 수 없는 앱 설치" 허용 → 설치. 이 PC에서 빌드한 APK라 초대 링크 인증도 똑같이 됩니다.

### 10-2. 초대 링크 자동 열기(App Links) 인증 확인

```powershell
& $adb -s $Fold shell pm verify-app-links --re-verify com.uridul.app
Start-Sleep -Seconds 10
& $adb -s $Fold shell pm get-app-links com.uridul.app
```

정상: `<프로젝트ID>.web.app: verified`
S26 Ultra도 `$Fold` 를 `$S26` 으로 바꿔 똑같이 실행합니다.

> `verified` 가 아니면 7단계 배포 또는 SHA-256이 맞지 않는 것입니다. 그래도 **링크를 앱에 붙여넣으면 연결은 됩니다.**

### 10-3. 초대 링크로 앱이 열리는지 시험 (가짜 토큰)

```powershell
& $adb -s $S26 shell am start -a android.intent.action.VIEW -d "https://$ProjectId.web.app/invite/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
```

정상: 브라우저가 아니라 **우리둘 앱이 바로 열림**. (가짜 토큰이라 실제 연결은 "만료되었거나 이미 사용된 링크" 안내가 나오면 정상)

---

## 11. 동작 테스트

### 11-1. 실제 사용 순서

1. **Fold8:** 우리둘 실행 → **Google 계정으로 시작하기** → 시작일 선택 → **만들기**
2. **Fold8:** 홈의 **초대 링크 보내기** → 카카오톡으로 여자친구에게 전송
3. **S26 Ultra:** 카카오톡에서 링크 누르기 → 앱이 열림 → Google 로그인 → **연결하기**
4. 두 폰 홈 화면에 이름 `○○ ♥ ○○` 와 같은 디데이가 나오면 성공
5. 두 폰 모두 홈 화면 빈 곳 길게 누르기 → **위젯** → **우리둘** → **우리둘 디데이** 추가

### 11-2. 보안 확인

- 같은 초대 링크를 **다시** 열어 연결하기 → "만료되었거나 이미 사용된 링크" 안내가 나와야 정상
- 세 번째 Google 계정으로 링크를 열어도 연결되면 안 됨

### 11-3. 위젯 강제 갱신 (자정 갱신 시험)

```powershell
& $adb -s $Fold shell am broadcast -n com.uridul.app/.widget.WidgetRefreshReceiver -a com.uridul.app.action.MIDNIGHT_REFRESH
```

다음 자정 알람이 예약되어 있는지 확인:

```powershell
& $adb -s $Fold shell dumpsys alarm | Select-String "com.uridul.app" | Select-Object -First 5
```

> 실제 자정 시험: 폰 설정 → 일반 → 날짜 및 시간 → 자동 끄기 → 날짜를 하루 뒤로 바꾸면 위젯 숫자가 1 올라가야 합니다 (시험 후 자동으로 되돌리기).

### 11-4. 오류 로그 보기

앱이 꺼지거나 이상할 때:

```powershell
& $adb -s $Fold logcat -b crash -d
```

앱 실행 중 실시간 로그 (끝내려면 `Ctrl + C`):

```powershell
$appPid = (& $adb -s $Fold shell pidof com.uridul.app).Trim()
& $adb -s $Fold logcat --pid=$appPid
```

### 11-5. 체크리스트

- [ ] 시작일을 바꾸면 상대 폰 홈 화면에 바로 반영되는가
- [ ] 위젯을 2×2 / 4×1 / 4×2 로 늘리고 줄이면 모양이 바뀌는가
- [ ] **Fold8:** 커버 화면에서 앱을 쓰다가 펼치면 2단(왼쪽 디데이, 오른쪽 기념일)으로 바뀌고 내용이 유지되는가
- [ ] **Fold8:** 커버 홈과 메인 홈에 위젯을 각각 놓았을 때 잘리지 않는가
- [ ] 설정 → 디스플레이 → 글자 크기 최대에서 디데이 숫자가 잘리지 않는가
- [ ] 다크 모드에서 글자가 잘 보이는가

---

## 12. 화면 정보 확인 (S26 Ultra / Fold8)

검토안의 화면 폭(dp) 추정치를 실제 값으로 확인합니다. Fold8은 **접은 상태와 펼친 상태에서 각각** 실행하세요.

```powershell
function Get-ScreenDp($dev) {
    $sizeLine = (& $adb -s $dev shell wm size)    | Select-Object -Last 1
    $densLine = (& $adb -s $dev shell wm density) | Select-Object -Last 1
    $wh = (($sizeLine -split ':')[1].Trim()) -split 'x'
    $w = [int]$wh[0]; $h = [int]$wh[1]
    $d = [int](($densLine -split ':')[1].Trim())
    "{0}: {1} x {2} px, {3}dpi -> {4:N0} x {5:N0} dp" -f $dev, $w, $h, $d, ($w*160/$d), ($h*160/$d)
}
Get-ScreenDp $S26
Get-ScreenDp $Fold
```

판단 기준: 짧은 쪽 폭이 **600dp 미만 → 1단**, **600dp 이상 → 2단** 레이아웃.
결과를 알려주시면 검토안 표를 실제 값으로 업데이트하겠습니다.

---

## 13. 갤럭시 절전 예외 설정 (두 폰 모두)

One UI가 앱을 재우면 위젯이 자정에 바로 안 바뀔 수 있습니다.

**폰에서 설정 (권장)**
설정 → 배터리 → **백그라운드 사용 제한** →
- "사용하지 않는 앱을 절전 상태로 전환" **끄기**, 또는
- **절전 예외 앱** → `+` → 우리둘 추가

**PowerShell로 배터리 최적화 제외 (보조)**

```powershell
& $adb -s $Fold shell dumpsys deviceidle whitelist +com.uridul.app
& $adb -s $S26  shell dumpsys deviceidle whitelist +com.uridul.app
```

> PowerShell 명령은 안드로이드 기본 "배터리 최적화"만 제외합니다. 삼성의 "절전 앱" 목록은 폰 설정에서 따로 확인해 주세요.

---

## 부록 A. 코드가 바뀐 뒤 다시 설치하기

코드 수정은 GitHub(ckskagns/hoonstorage)에 올라갑니다. 최신 코드를 받아서 두 폰에 다시 설치:

```powershell
Set-Location $Root
git pull
Set-Location "$Root\android"
.\gradlew.bat assembleDebug
& $adb -s $Fold install -r "$Root\android\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $S26  install -r "$Root\android\app\build\outputs\apk\debug\app-debug.apk"
```

> `install -r` 은 데이터를 지우지 않고 덮어씁니다. 로그인·연결 상태가 그대로 유지됩니다.

보안 규칙이나 초대 웹페이지가 바뀐 경우:

```powershell
Set-Location "$Root\firebase"
firebase.cmd deploy --only "firestore:rules,hosting" --project $ProjectId
```

---

## 부록 B. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `이 시스템에서 스크립트를 실행할 수 없으므로…` | 스크립트 실행 정책 | `npm` → `npm.cmd`, `firebase` → `firebase.cmd` 로 실행 |
| `'&&' 토큰은 이 버전에서 올바른 문 구분 기호가 아닙니다` | PowerShell 5.1 | 명령을 한 줄씩 실행하거나 `;` 사용 |
| `'adb' 용어가 … 인식되지 않습니다` | PATH에 없음 | 0-3 공통 변수 실행 후 `& $adb ...` 형태로 사용 |
| `node` 인식 안 됨 (설치 직후) | PATH 갱신 전 | 2-1의 PATH 새로 읽기 명령 실행 또는 창 다시 열기 |
| `projects:create` 실패 – terms | 약관 미동의 | 3-2 브라우저 단계 진행 |
| `projects:create` 실패 – already exists | ID 중복 | 3-3을 다시 실행 (숫자 새로 뽑힘) |
| 배포 시 Hosting 사이트 없음 오류 | 기본 사이트 미생성 | `firebase.cmd hosting:sites:create $ProjectId --project $ProjectId` 후 다시 배포 |
| 앱에 "Firebase 설정이 필요해요" | google-services.json 없음 | 6단계 후 8단계 다시 빌드·설치 |
| 로그인 시 "Google 로그인이 켜져 있지 않아요" | 5단계 전에 받은 json | 6단계 다시 실행 → 다시 빌드·설치 |
| 로그인 창이 뜨지 않고 실패 / DEVELOPER_ERROR | SHA-1 미등록 또는 다른 PC에서 빌드 | 4-3 ~ 4-4 확인, 이 PC에서 빌드한 APK인지 확인 |
| "이 폰에 로그인할 수 있는 Google 계정이 없어요" | 폰에 Google 계정 없음 | 폰 설정 → 계정 → Google 계정 추가 |
| 연결하기에서 "권한이 없어요" | 규칙 미배포 또는 사용된 링크 | 7-2 다시 배포, 새 초대 링크 생성 |
| 링크를 누르면 브라우저가 열림 | App Links 미인증 | 10-2 실행. 급하면 웹페이지의 "앱에서 열기" 버튼 또는 링크 붙여넣기 |
| `adb pair` 실패 | 다른 Wi-Fi / 포트 변경 | 같은 Wi-Fi 확인, 폰 화면의 새 포트·코드로 다시 |
| `adb devices` 에 `offline` | 연결 끊김 | `& $adb kill-server` → `& $adb connect IP:포트` |
| Gradle `Could not move temporary workspace` | 백신의 일시적 파일 잠금 | 같은 빌드 명령 다시 실행. 반복되면 `.\gradlew.bat --stop` 후 재시도 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 다른 키로 서명된 앱이 이미 설치됨 | `& $adb -s $Fold uninstall com.uridul.app` 후 다시 설치 (데이터 초기화됨) |

---

## 부록 C. 명령 요약 (처음 설정이 끝난 뒤 자주 쓰는 것)

```powershell
# 공통 변수 (새 창마다)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Root = "D:\APP_TEST"; $Sdk = "$env:LOCALAPPDATA\Android\Sdk"; $adb = "$Sdk\platform-tools\adb.exe"
$ProjectId = (Get-Content "$Root\firebase\.firebaserc" -Raw | ConvertFrom-Json).projects.default

# 폰 무선 연결
& $adb connect 192.168.0.12:41234
& $adb devices -l

# 빌드 + 설치
Set-Location "$Root\android"; .\gradlew.bat assembleDebug
& $adb install -r "$Root\android\app\build\outputs\apk\debug\app-debug.apk"

# 위젯 강제 갱신
& $adb shell am broadcast -n com.uridul.app/.widget.WidgetRefreshReceiver -a com.uridul.app.action.MIDNIGHT_REFRESH

# 크래시 로그
& $adb logcat -b crash -d
```
