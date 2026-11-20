param(
    [string]$Source = "D:\APP_TEST\assets_in\characters",
    [int]$Size = 512
)
# Import couple character art into the app.
# Expects PNG files named char_NN_key.png (see docs/charater guide table), transparent background, square.
# Checks names, PNG signature, size and alpha, then resizes to $Size x $Size and copies to res/drawable-nodpi.
# The app shows a picture automatically when its file exists (ui/EmotionArt.kt), so no code change is needed.
Add-Type -AssemblyName System.Drawing
$names = @('00_base','01_joy','02_love','03_hug','04_kiss','05_flutter','06_celebrate','07_thanks','08_sorry','09_sulk',
  '10_sad','11_surprise','12_goodnight','13_morning','14_yummy','15_date','16_trip','17_photo','18_save','19_miss',
  '20_cheer','21_think','22_letter','23_pat','24_rest')
$dest = Join-Path $PSScriptRoot '..\android\app\src\main\res\drawable-nodpi'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$ok = 0; $missing = @(); $problems = @()
foreach ($n in $names) {
  $src = Join-Path $Source "char_$n.png"
  if (-not (Test-Path -LiteralPath $src)) { $missing += "char_$n.png"; continue }
  $bytes = [System.IO.File]::ReadAllBytes($src)
  if ($bytes.Length -lt 33 -or $bytes[0] -ne 0x89 -or $bytes[1] -ne 0x50) { $problems += "char_$n.png: not a PNG"; continue }
  $w = ($bytes[16] -shl 24) -bor ($bytes[17] -shl 16) -bor ($bytes[18] -shl 8) -bor $bytes[19]
  $h = ($bytes[20] -shl 24) -bor ($bytes[21] -shl 16) -bor ($bytes[22] -shl 8) -bor $bytes[23]
  $colorType = $bytes[25]
  if ($w -ne $h) { $problems += "char_$n.png: not square (${w}x${h}) - padded to square" }
  if ($colorType -ne 6 -and $colorType -ne 4) { $problems += "char_$n.png: no transparency (white background will show)" }
  $img = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $src))
  $bmp = New-Object System.Drawing.Bitmap $Size, $Size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.Clear([System.Drawing.Color]::Transparent)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $scale = [Math]::Min($Size / $img.Width, $Size / $img.Height)
  $dw = [int]($img.Width * $scale); $dh = [int]($img.Height * $scale)
  $g.DrawImage($img, [int](($Size - $dw) / 2), [int](($Size - $dh) / 2), $dw, $dh)
  $out = Join-Path $dest "char_$n.png"
  $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose(); $img.Dispose()
  $ok++
}
"imported=$ok / 25"
if ($missing.Count) { "missing (app keeps the current characters there): " + ($missing -join ', ') }
if ($problems.Count) { "check:"; $problems }
