# 우리둘 새 버전 배포 스크립트 (PowerShell 5.1)
#
# 하는 일
#   1) android/app/build.gradle.kts 의 버전 올리기 (versionCode +1, versionName 은 지정값 또는 마지막 자리 +1)
#   2) 앱 빌드 (이 PC 의 서명 키로 서명된 APK)
#   3) APK 를 GitHub Releases(ckskagns/hoonstorage)에 올리기
#      (Firebase 무료 요금제는 APK 같은 실행 파일을 올릴 수 없어서 APK 만 GitHub 에 둔다)
#   4) version.json 을 Firebase Hosting(download/)에 배포 → 두 사람의 앱이 다음에 열릴 때 업데이트 안내
#
# 사용 예
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Notes "캐릭터 추가, 버그 수정"
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Version 0.4.0 -Notes "오늘의 질문"
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -NoBump -Notes "..."  # 버전 그대로 다시 배포
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -NoDeploy   # 빌드만 (올리지 않음)
#
# 준비물: GitHub CLI(gh) 설치 + gh auth login (처음 한 번)
# 주의: 업데이트는 "같은 서명 키"로 만든 APK 만 설치된다.
#       이 PC 의 %USERPROFILE%\.android\debug.keystore 를 꼭 백업해 두세요.

param(
    [string]$Version = "",
    [string]$Notes = "",
    [switch]$NoBump,
    [switch]$NoDeploy
)

$Repo = "ckskagns/hoonstorage"

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Gradle = Join-Path $Root "android\app\build.gradle.kts"
$Download = Join-Path $Root "firebase\hosting\public\download"
$Utf8 = New-Object System.Text.UTF8Encoding($false)   # BOM 없는 UTF-8 (한글 깨짐 방지)

# 1) 버전 올리기 (-NoBump 이면 그대로)
$text = [IO.File]::ReadAllText($Gradle, $Utf8)
$code = [int]([regex]::Match($text, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$name = [regex]::Match($text, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if ($NoBump) {
    $newCode = $code; $newName = $name
} else {
    $newCode = $code + 1
    if ($Version) {
        $newName = $Version
    } else {
        $parts = $name.Split('.')
        $parts[$parts.Length - 1] = [string]([int]$parts[$parts.Length - 1] + 1)
        $newName = $parts -join '.'
    }
    $text = [regex]::Replace($text, 'versionCode\s*=\s*\d+', "versionCode = $newCode")
    $text = [regex]::Replace($text, 'versionName\s*=\s*"[^"]+"', "versionName = `"$newName`"")
    [IO.File]::WriteAllText($Gradle, $text, $Utf8)
}
Write-Host "버전: $name ($code) -> $newName ($newCode)"

# 2) 빌드
Push-Location (Join-Path $Root "android")
try {
    & .\gradlew.bat --console=plain -q assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "빌드 실패" }
} finally {
    Pop-Location
}
$apkSrc = Join-Path $Root "android\app\build\outputs\apk\debug\app-debug.apk"
$work = Join-Path $env:TEMP "uridul-release"
New-Item -ItemType Directory -Force $work | Out-Null
$apk = Join-Path $work "uridul.apk"
Copy-Item $apkSrc $apk -Force
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $apk).Length
Write-Host "APK: $([math]::Round($size / 1MB, 1))MB, SHA-256 $($hash.Substring(0, 12))…"
if ($NoDeploy) {
    Write-Host "올리기는 건너뜀 (-NoDeploy). APK: $apk"
    exit 0
}

# 3) GitHub Releases 에 APK 올리기
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = "C:\Program Files\GitHub CLI\gh.exe" }
if (-not (Test-Path $gh)) { throw "GitHub CLI(gh)가 없어요. 설치 후 'gh auth login' 을 먼저 해 주세요." }
$tag = "v$newName"
$title = "우리둘 $newName"
$notesFile = Join-Path $work "notes.txt"
[IO.File]::WriteAllText($notesFile, $(if ($Notes) { $Notes } else { "우리둘 $newName" }), $Utf8)
& $gh release view $tag --repo $Repo *> $null
if ($LASTEXITCODE -eq 0) {
    & $gh release upload $tag $apk --repo $Repo --clobber
} else {
    & $gh release create $tag $apk --repo $Repo --title $title --notes-file $notesFile --latest
}
if ($LASTEXITCODE -ne 0) { throw "GitHub 업로드 실패" }
$apkUrl = "https://github.com/$Repo/releases/download/$tag/uridul.apk"

# 4) version.json 을 Firebase Hosting 에 배포
$host_ = [regex]::Match((Get-Content (Join-Path $Root "firebase\.firebaserc") -Raw), '"default"\s*:\s*"([^"]+)"').Groups[1].Value
New-Item -ItemType Directory -Force $Download | Out-Null
$json = [ordered]@{
    versionCode = $newCode
    versionName = $newName
    notes       = $Notes
    size        = $size
    sha256      = $hash
    apkUrl      = $apkUrl
    releasedAt  = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
} | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $Download "version.json"), $json, $Utf8)

$node = "C:\Program Files\nodejs\node.exe"
$fb = Join-Path $env:APPDATA "npm\node_modules\firebase-tools\lib\bin\firebase.js"
Push-Location (Join-Path $Root "firebase")
try {
    if ((Test-Path $node) -and (Test-Path $fb)) {
        & $node $fb deploy --only hosting --project $host_
    } else {
        firebase.cmd deploy --only hosting --project $host_
    }
    if ($LASTEXITCODE -ne 0) { throw "배포 실패" }
} finally {
    Pop-Location
}
Write-Host ""
Write-Host "배포 완료: $newName"
Write-Host "설치 페이지: https://$host_.web.app/download/"
Write-Host "APK: $apkUrl"
Write-Host "두 사람의 앱은 다음에 열 때 업데이트 안내가 나와요."