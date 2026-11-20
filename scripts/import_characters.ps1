param(
    [string]$Source = "D:\APP_TEST\assets_in\characters",
    [int]$Size = 512,
    # Use when the received file names already match the pictures (no renaming).
    [switch]$NoRemap
)
# Import couple character art into the app WITHOUT putting it in the git repository.
#  - Output goes to android/app/src/chars/res (git-ignored). Gradle adds that folder only when it exists,
#    so a fresh clone still builds (the app then shows the old characters).
#  - The received set (2026-11-20) had 20 files whose names did not match the picture; $remap fixes that.
#  - Each picture -> 512x512 WebP (cwebp from tools/libwebp, BSD license). Falls back to PNG if cwebp is missing.
#  - Also makes: face avatars (char_bust_he / char_bust_she) and the app icon foreground (ic_launcher_char_fg).
# Run:  powershell -ExecutionPolicy Bypass -File D:\APP_TEST\scripts\import_characters.ps1
Add-Type -AssemblyName System.Drawing
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$res = Join-Path $root 'android\app\src\chars\res'
$draw = Join-Path $res 'drawable-nodpi'
$mip = Join-Path $res 'mipmap-anydpi-v26'
$cwebp = Join-Path $root 'tools\libwebp\libwebp-1.4.0-windows-x64\bin\cwebp.exe'
$useWebp = Test-Path -LiteralPath $cwebp
$tmp = Join-Path $env:TEMP ('uridul_chars_' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $draw, $mip, $tmp | Out-Null

# app name (picture content) <- received file name
$remap = [ordered]@{
  'char_00_base'='char_00_base'; 'char_01_joy'='char_01_joy'; 'char_02_love'='char_02_love'; 'char_03_hug'='char_03_hug';
  'char_04_kiss'='char_04_kiss'; 'char_05_flutter'='char_05_shy'; 'char_06_celebrate'='char_06_congrats';
  'char_07_thanks'='char_15_date'; 'char_08_sorry'='char_08_sorry'; 'char_09_sulk'='char_16_travel';
  'char_10_sad'='char_14_yummy'; 'char_11_surprise'='char_13_goodmorning'; 'char_12_goodnight'='char_11_surprise';
  'char_13_morning'='char_09_pout'; 'char_14_yummy'='char_07_thanks'; 'char_15_date'='char_10_sad';
  'char_16_trip'='char_18_save'; 'char_17_photo'='char_23_pat'; 'char_18_save'='char_20_fighting';
  'char_19_miss'='char_24_rest'; 'char_20_cheer'='char_17_photo'; 'char_21_think'='char_he_front';
  'char_22_letter'='char_22_letter'; 'char_23_pat'='char_21_think'; 'char_24_rest'='char_19_miss';
  'char_he_front'='char_12_goodnight'; 'char_she_front'='char_she_front'
}

function Save-Out([System.Drawing.Bitmap]$bmp, [string]$name, [int]$quality) {
  $png = Join-Path $tmp ($name + '.png')
  $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
  if ($useWebp) {
    & $cwebp -quiet -q $quality -alpha_q 100 -m 6 -exact $png -o (Join-Path $draw ($name + '.webp')) | Out-Null
  } else {
    Copy-Item -LiteralPath $png -Destination (Join-Path $draw ($name + '.png')) -Force
  }
}

# crop [x,y,w,h] of a 1024 source into a square of $out px (transparent background, centered)
function Make-Crop([string]$src, [int[]]$r, [int]$out, [int]$boxW, [int]$offY) {
  $img = [System.Drawing.Image]::FromFile($src)
  $k = $img.Width / 1024.0
  $bmp = New-Object System.Drawing.Bitmap $out, $out, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp); $g.Clear([System.Drawing.Color]::Transparent)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $w = $boxW; $h = [int]($boxW * $r[3] / $r[2])
  $dst = New-Object System.Drawing.RectangleF ([single](($out - $w) / 2)), ([single](($out - $h) / 2 + $offY)), ([single]$w), ([single]$h)
  $srcR = New-Object System.Drawing.RectangleF ($r[0]*$k), ($r[1]*$k), ($r[2]*$k), ($r[3]*$k)
  $g.DrawImage($img, $dst, $srcR, [System.Drawing.GraphicsUnit]::Pixel)
  $g.Dispose(); $img.Dispose()
  return $bmp
}

# old output (also old PNG names) is replaced
Get-ChildItem -LiteralPath $draw -Filter 'char_*' -ErrorAction SilentlyContinue | Remove-Item -Force
Get-ChildItem -LiteralPath $draw -Filter 'ic_launcher_char*' -ErrorAction SilentlyContinue | Remove-Item -Force

$ok = 0; $missing = @(); $problems = @(); $srcOf = @{}
foreach ($app in $remap.Keys) {
  $recv = if ($NoRemap) { $app } else { $remap[$app] }
  $src = Join-Path $Source ($recv + '.png')
  if (-not (Test-Path -LiteralPath $src)) { $missing += "$recv.png (-> $app)"; continue }
  $bytes = [System.IO.File]::ReadAllBytes($src)
  if ($bytes.Length -lt 33 -or $bytes[0] -ne 0x89 -or $bytes[1] -ne 0x50) { $problems += "$recv.png: not a PNG"; continue }
  if ($bytes[25] -ne 6 -and $bytes[25] -ne 4) { $problems += "$recv.png: no transparency (white background will show)" }
  $srcOf[$app] = $src
  $img = [System.Drawing.Image]::FromFile($src)
  $bmp = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp); $g.Clear([System.Drawing.Color]::Transparent)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $s = [Math]::Min($Size / $img.Width, $Size / $img.Height)
  $dw = [int]($img.Width * $s); $dh = [int]($img.Height * $s)
  $g.DrawImage($img, [int](($Size - $dw) / 2), [int](($Size - $dh) / 2), $dw, $dh)
  $g.Dispose(); $img.Dispose()
  Save-Out $bmp $app 90
  $bmp.Dispose(); $ok++
}

# face avatars for small round spots (name circles, widget)
if ($srcOf['char_he_front']) { $b = Make-Crop $srcOf['char_he_front'] @(312, 40, 400, 400) 256 256 0; Save-Out $b 'char_bust_he' 90; $b.Dispose() }
if ($srcOf['char_she_front']) { $b = Make-Crop $srcOf['char_she_front'] @(312, 50, 400, 400) 256 256 0; Save-Out $b 'char_bust_she' 90; $b.Dispose() }

# app icon foreground: 108dp layer = 432px, faces + heart 74dp wide, 5dp lower (horns stay inside the round mask)
if ($srcOf['char_02_love']) {
  $b = Make-Crop $srcOf['char_02_love'] @(175, 60, 680, 420) 432 296 20; Save-Out $b 'ic_launcher_char_fg' 95; $b.Dispose()
  $xml = @'
<?xml version="1.0" encoding="utf-8"?>
<!-- made by scripts/import_characters.ps1 (not in git). Monochrome (themed icon) keeps the heart. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_char_fg" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'@
  [System.IO.File]::WriteAllText((Join-Path $mip 'ic_launcher_char.xml'), $xml)
  [System.IO.File]::WriteAllText((Join-Path $mip 'ic_launcher_char_round.xml'), $xml)
}
Remove-Item -Recurse -Force -LiteralPath $tmp

$total = (Get-ChildItem -LiteralPath $draw | Measure-Object -Property Length -Sum).Sum
"imported=$ok / $($remap.Count)  format=$(if ($useWebp) {'webp'} else {'png (cwebp not found)'})  total=$([Math]::Round($total / 1KB)) KB"
"output: $res  (git-ignored)"
if ($missing.Count) { "missing (app keeps the old characters there): " + ($missing -join ', ') }
if ($problems.Count) { "check:"; $problems }
