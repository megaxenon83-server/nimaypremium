# Starts this installation's OMP bridge without a console window and waits
# until its own token authenticates against /api/health.
param(
    [int]$WaitSeconds = 20,
    [int]$Port = 0
)

$appDir = Split-Path -Parent $PSScriptRoot
$bridgeDir = Join-Path $appDir "bridge"
$script = Join-Path $bridgeDir "omp-bridge.mjs"
$configPath = Join-Path $bridgeDir "config.json"

# Resolve OMP_BIN: env var first, then the installer-written file, then registry.
if (-not $env:OMP_BIN) {
    $binFile = Join-Path $bridgeDir "omp-bin.txt"
    if (Test-Path $binFile) {
        $saved = (Get-Content -Raw -Encoding UTF8 $binFile).Trim()
        if ($saved) { $env:OMP_BIN = $saved }
    }
}
if (-not $env:OMP_BIN) {
    try {
        $reg = (Get-ItemProperty -Path 'HKCU:\Environment' -Name 'OMP_BIN' -ErrorAction SilentlyContinue).OMP_BIN
        if ($reg) { $env:OMP_BIN = $reg }
    } catch { }
}
if (-not (Test-Path $script)) { Write-Host "Fant ikke $script"; exit 1 }

function Read-BridgeConfig {
    if (-not (Test-Path $configPath)) { return $null }
    try { return Get-Content -Raw -Encoding UTF8 $configPath | ConvertFrom-Json } catch { return $null }
}

if ($Port -le 0 -and $env:OMPCHAT_BRIDGE_PORT) {
    [void][int]::TryParse($env:OMPCHAT_BRIDGE_PORT, [ref]$Port)
}
if ($Port -le 0) {
    $Port = 7777
    $config = Read-BridgeConfig
    if ($config -and $config.port) {
        $configured = [int]$config.port
        if ($configured -gt 0) { $Port = $configured }
    }
}

$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    foreach ($candidate in @("C:\Program Files\nodejs\node.exe", (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"))) {
        if (Test-Path $candidate) { $node = $candidate; break }
    }
}
if (-not $node) { Write-Host "Node.js er ikke installert."; exit 1 }

function Test-Bridge {
    $config = Read-BridgeConfig
    if (-not $config -or -not $config.token) { return $false }
    try {
        $headers = @{ Authorization = "Bearer $($config.token)" }
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/health" -Headers $headers -TimeoutSec 3
        return ($r.ok -eq $true -and $r.version)
    } catch { return $false }
}

# A health response authenticated with this installation's token prevents an
# unrelated bridge on the same port from being mistaken for this instance.
if (Test-Bridge) { Write-Host "Broen svarer allerede paa port $Port."; exit 0 }

$env:OMPCHAT_BRIDGE_PORT = "$Port"
# Do not let the detached bridge inherit the installer's console handles.
# Besides keeping silent installs responsive, this preserves useful diagnostics.
$stdoutLog = Join-Path $bridgeDir "bridge.stdout.log"
$stderrLog = Join-Path $bridgeDir "bridge.stderr.log"
Start-Process -FilePath $node -ArgumentList "`"$script`"" -WorkingDirectory $bridgeDir -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog | Out-Null
for ($i = 0; $i -lt ($WaitSeconds * 2); $i++) {
    Start-Sleep -Milliseconds 500
    if (Test-Bridge) { Write-Host "Broen svarer paa port $Port."; exit 0 }
}
Write-Host "Broen svarte ikke innen $WaitSeconds sekunder med denne installasjonens token."
exit 1
