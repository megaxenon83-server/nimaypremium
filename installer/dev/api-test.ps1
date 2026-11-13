# End-to-end check of an installed copy: bridge health, APK distribution and
# real local speech synthesis + recognition. Never prints the pairing token.
param(
    [Parameter(Mandatory = $true)][string]$AppDir,
    [int]$Port = 7777,
    [string]$Text = "Hei, dette er Nimay OMP som tester norsk tale."
)

$ErrorActionPreference = "Continue"
$results = [ordered]@{}
$configPath = Join-Path $AppDir "bridge\config.json"
if (-not (Test-Path $configPath)) { throw "Fant ikke $configPath - start broen forst." }
$token = (Get-Content -Raw $configPath | ConvertFrom-Json).token
$headers = @{ Authorization = "Bearer $token" }
$base = "http://127.0.0.1:$Port"

function Step($name, $script) {
    try {
        $value = & $script
        $script:results[$name] = $value
    } catch {
        $script:results[$name] = "FEIL: $($_.Exception.Message)"
    }
}

Step "ping" {
    $r = Invoke-RestMethod -Uri "$base/api/ping" -TimeoutSec 5
    "ok=$($r.ok) version=$($r.version)"
}

Step "apk_head" {
    $apkPath = Join-Path $AppDir "apk\OmpChat.apk"
    $head = Invoke-WebRequest -Uri "$base/apk/OmpChat.apk" -Method Head -TimeoutSec 10 -UseBasicParsing
    $expected = (Get-Item $apkPath).Length
    "status=$($head.StatusCode) bytes=$($head.Headers['Content-Length']) expected=$expected"
}

Step "apk_lan" {
    $ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -like "192.168.*" -or $_.IPAddress -like "10.*" } | Select-Object -First 1).IPAddress
    if (-not $ip) { return "ingen LAN-adresse funnet" }
    $url = "http://${ip}:$Port/apk/OmpChat.apk"
    $head = Invoke-WebRequest -Uri $url -Method Head -TimeoutSec 10 -UseBasicParsing
    "url=$url status=$($head.StatusCode) bytes=$($head.Headers['Content-Length'])"
}

Step "voice_engines" {
    $r = Invoke-RestMethod -Uri "$base/api/voice/engines" -Headers $headers -TimeoutSec 15
    $tts = ($r.engines.PSObject.Properties | Where-Object { $_.Value.installed } | ForEach-Object { $_.Name }) -join ","
    $stt = ($r.stt.engines.PSObject.Properties | Where-Object { $_.Value.installed } | ForEach-Object { $_.Name }) -join ","
    "tts=[$tts] stt=[$stt] active_tts=$($r.active.engine) active_stt=$($r.active.stt)"
}

$wav = Join-Path $env:TEMP "nimay-tts-check.wav"
Step "tts" {
    $body = @{ text = $Text } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/voice/speak" -Method Post -Headers $headers -Body $body -ContentType "application/json" -TimeoutSec 180
    if (-not $r.ok) { throw "svar uten ok" }
    Invoke-WebRequest -Uri "$base$($r.audioUrl)" -OutFile $wav -TimeoutSec 60 -UseBasicParsing | Out-Null
    "engine=$($r.engine) duration=$($r.duration)s bytes=$((Get-Item $wav).Length)"
}

Step "stt" {
    if (-not (Test-Path $wav)) { throw "ingen wav fra TTS" }
    $payload = @{ audio = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($wav)) } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/api/voice/transcribe" -Method Post -Headers $headers -Body $payload -ContentType "application/json" -TimeoutSec 300
    "language=$($r.language) text=$($r.text)"
}

$results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-14} {1}" -f $_.Key, $_.Value) }
