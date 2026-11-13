# Prepares the "Mobil" wizard page: makes sure a local web server is serving the
# APK, generates the two QR codes (APK download + OMP pairing) as BMP files and
# writes a plain KEY=VALUE file that the Inno Setup script reads back.
param(
    [Parameter(Mandatory = $true)][string]$AppDir,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [int]$ApkPort = 7787,
    [int]$BridgePort = 7777
)

$ErrorActionPreference = "Continue"
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$infoPath = Join-Path $OutDir "qr-info.txt"
$lines = New-Object System.Collections.Generic.List[string]
function Add-Info([string]$key, [string]$value) {
    $clean = ($value -replace "[\r\n]+", " ").Trim()
    $lines.Add("$key=$clean")
}

$apkPath = Join-Path $AppDir "apk\OmpChat.apk"
$bridgeDir = Join-Path $AppDir "bridge"
$toolsDir = Join-Path $AppDir "tools"
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    foreach ($candidate in @("C:\Program Files\nodejs\node.exe", (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"))) {
        if (Test-Path $candidate) { $node = $candidate; break }
    }
}

Add-Info "APK_FILE" $apkPath
if (-not (Test-Path $apkPath)) {
    Add-Info "STATUS" "error"
    Add-Info "MESSAGE" "APK-filen mangler i installasjonen."
    Set-Content -Path $infoPath -Value $lines -Encoding ASCII
    exit 1
}
Add-Info "APK_BYTES" ([string](Get-Item $apkPath).Length)

if (-not $node) {
    Add-Info "STATUS" "error"
    Add-Info "MESSAGE" "Node.js ble ikke funnet, kan ikke starte lokal APK-server."
    Set-Content -Path $infoPath -Value $lines -Encoding ASCII
    exit 1
}

function Test-Bridge {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$BridgePort/api/ping" -TimeoutSec 3 -UseBasicParsing
        return ($response.StatusCode -eq 200)
    } catch { return $false }
}

$bridgeUp = Test-Bridge
if (-not $bridgeUp) {
    # Start through explorer.exe when possible: this script runs from the
    # elevated installer, and the bridge must keep the normal user's rights.
    $vbs = Join-Path $toolsDir "start-bridge-hidden.vbs"
    if (Test-Path $vbs) {
        Start-Process -FilePath (Join-Path $env:SystemRoot "explorer.exe") -ArgumentList "`"$vbs`"" -ErrorAction SilentlyContinue
    } else {
        Start-Process -FilePath $node -ArgumentList "`"$(Join-Path $bridgeDir 'omp-bridge.mjs')`"" -WorkingDirectory $bridgeDir -WindowStyle Hidden -ErrorAction SilentlyContinue
    }
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Bridge) { $bridgeUp = $true; break }
    }
    if (-not $bridgeUp -and (Test-Path $vbs)) {
        # Explorer launch did not work (no shell?) - start it directly so the
        # QR page still shows a live pairing code.
        Start-Process -FilePath $node -ArgumentList "`"$(Join-Path $bridgeDir 'omp-bridge.mjs')`"" -WorkingDirectory $bridgeDir -WindowStyle Hidden -ErrorAction SilentlyContinue
        for ($i = 0; $i -lt 10; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-Bridge) { $bridgeUp = $true; break }
        }
    }
}
Add-Info "BRIDGE" $(if ($bridgeUp) { "running" } else { "stopped" })

# The pairing QR needs a token. An older bridge (or one that has never started)
# has no config.json yet, so create a minimal one - the bridge reads it on start
# and pairs the phone with exactly this token.
$configPath = Join-Path $bridgeDir "config.json"
$token = ""
if (Test-Path $configPath) {
    try { $token = (Get-Content -Raw -Encoding UTF8 $configPath | ConvertFrom-Json).token } catch { }
}
if (-not $token) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $token = -join ($bytes | ForEach-Object { $_.ToString("x2") })
    $config = @{ token = $token; port = $BridgePort; approvalMode = "always-ask" } | ConvertTo-Json
    try {
        [System.IO.File]::WriteAllText($configPath, $config, (New-Object System.Text.UTF8Encoding($false)))
        Add-Info "BRIDGE_CONFIG" "opprettet"
    } catch {
        # Read-only install dir: keep the in-memory token so the pairing QR below
        # still matches the bridge that is running right now.
        Add-Info "BRIDGE_CONFIG" "kun i minnet (skriving nektet)"
    }
}

# Pick the address a phone on the same network can actually reach.
$urlInfo = & $node (Join-Path $toolsDir "apk-url.mjs") --app $AppDir --apk-port $ApkPort --bridge-port $BridgePort --plain 2>$null
$host_ip = ""
$pairPayload = ""
foreach ($line in $urlInfo) {
    if ($line -match "^HOST=(.+)$") { $host_ip = $Matches[1].Trim() }
    if ($line -match "^PAIRING=(.+)$") { $pairPayload = $Matches[1].Trim() }
}
if (-not $host_ip) { $host_ip = "127.0.0.1" }
Add-Info "HOST" $host_ip

function Test-ApkUrl {
    param([string]$url)
    try {
        $head = Invoke-WebRequest -Uri $url -Method Head -TimeoutSec 15 -UseBasicParsing
        $script:reported = [int64]$head.Headers["Content-Length"]
        return ($head.StatusCode -eq 200 -and $script:reported -eq $apkPathBytes)
    } catch {
        $script:reported = 0
        $script:lastError = $_.Exception.Message
        return $false
    }
}

$apkPathBytes = (Get-Item $apkPath).Length
$reported = 0
$lastError = ""

# Prefer the bridge (port 7777) because it is the service that stays running.
# An older bridge build has no /apk route, so fall back to the standalone APK
# server on port 7787 when the bridge cannot deliver the file.
$apkUrl = "http://${host_ip}:$BridgePort/apk/OmpChat.apk"
$servePort = $BridgePort
$servedByBridge = $false
if ($bridgeUp -and (Test-ApkUrl $apkUrl)) {
    $servedByBridge = $true
} else {
    $servePort = $ApkPort
    $apkUrl = "http://${host_ip}:$ApkPort/apk/OmpChat.apk"
    $already = $false
    try {
        $probe = Invoke-WebRequest -Uri "http://127.0.0.1:$ApkPort/health" -TimeoutSec 2 -UseBasicParsing
        $already = ($probe.StatusCode -eq 200)
    } catch { }
    if (-not $already) {
        Start-Process -FilePath $node -ArgumentList "`"$(Join-Path $toolsDir 'apk-server.mjs')`" --app `"$AppDir`" --port $ApkPort" -WorkingDirectory $toolsDir -WindowStyle Hidden -ErrorAction SilentlyContinue
        for ($i = 0; $i -lt 10; $i++) {
            Start-Sleep -Milliseconds 500
            try {
                $probe = Invoke-WebRequest -Uri "http://127.0.0.1:$ApkPort/health" -TimeoutSec 2 -UseBasicParsing
                if ($probe.StatusCode -eq 200) { break }
            } catch { }
        }
    }
}

Add-Info "APK_URL" $apkUrl
Add-Info "SERVE_PORT" ([string]$servePort)

# Verify the download URL over the LAN address (not localhost), with a real
# HTTP request, and compare the reported length with the file on disk.
$verify = "fail"
$detail = ""
if (Test-ApkUrl $apkUrl) {
    $verify = "ok"
    $detail = "HTTP 200, $reported bytes"
} elseif ($reported -gt 0) {
    $detail = "HTTP 200, lengde $reported (forventet $apkPathBytes)"
} elseif ($lastError) {
    $detail = $lastError
} else {
    $detail = "ukjent feil"
}
Add-Info "VERIFY" $verify
Add-Info "VERIFY_DETAIL" $detail

if ($pairPayload) {
    $pairPath = Join-Path $OutDir "qr-pair.bmp"
    & $node (Join-Path $toolsDir "qrgen.mjs") $pairPayload $pairPath 6 | Out-Null
    if (Test-Path $pairPath) {
        Add-Info "QR_PAIR" $pairPath
        Add-Info "PAIR_URL" ("omp2://" + $host_ip + ":" + $BridgePort)
    }
}

$qrPath = Join-Path $OutDir "qr-apk.bmp"
& $node (Join-Path $toolsDir "qrgen.mjs") $apkUrl $qrPath 6 | Out-Null
if (Test-Path $qrPath) { Add-Info "QR_APK" $qrPath }

# Report verified voice state (only what the setup script actually confirmed).
$statusFile = Join-Path $AppDir "voice\voice-status.json"
if (Test-Path $statusFile) {
    try {
        $voice = Get-Content -Raw $statusFile | ConvertFrom-Json
        $ttsNames = @()
        foreach ($p in $voice.tts.PSObject.Properties) {
            if ($p.Value.installed) { $ttsNames += $p.Value.name }
        }
        $sttNames = @()
        foreach ($p in $voice.stt.PSObject.Properties) {
            if ($p.Value.installed) { $sttNames += $p.Value.name }
        }
        if ($ttsNames.Count -gt 0) { Add-Info "VOICE_TTS" ($ttsNames -join ", ") }
        if ($sttNames.Count -gt 0) { Add-Info "VOICE_STT" ($sttNames -join ", ") }
        if ($voice.test) {
            Add-Info "VOICE_TEST" ("syntese: " + $voice.test.tts + ", gjenkjenning: " + $voice.test.stt)
        }
    } catch {
        Add-Info "VOICE_TEST" "kunne ikke lese voice-status.json"
    }
}

Add-Info "STATUS" "ok"
Add-Info "MESSAGE" $(if ($bridgeUp) { "OMP-broen kjorer og deler APK-en." } else { "Lokal APK-server kjorer (broen er ikke startet)." })
Set-Content -Path $infoPath -Value $lines -Encoding ASCII
exit 0
