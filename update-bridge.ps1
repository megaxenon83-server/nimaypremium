# Safe Bridge Update Script using Task Scheduler for process detachment
param(
    [Parameter(Mandatory=$true)][int]$TargetPort,
    [Parameter(Mandatory=$true)][string]$TargetDir,
    [Parameter(Mandatory=$true)][string]$NewSource,
    [Parameter(Mandatory=$true)][string]$ExpectedOldVersion
)

$ErrorActionPreference = "Continue"

$bridgeDir = Join-Path $TargetDir "bridge"
$script = Join-Path $bridgeDir "omp-bridge.mjs"
$configPath = Join-Path $bridgeDir "config.json"
$logFile = Join-Path $env:USERPROFILE ".omp\agent\bridge-updates.log"
$stabilizationSeconds = 5

$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    foreach ($candidate in @("C:\Program Files\nodejs\node.exe", (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"))) {
        if (Test-Path $candidate) { $node = $candidate; break }
    }
}
if (-not $node) { Write-Host "Node.js not found"; exit 1 }

function Read-Config { if (-not (Test-Path $configPath)) { return $null }; try { return Get-Content -Raw -Encoding UTF8 $configPath | ConvertFrom-Json } catch { return $null } }
function Get-Version {
    $config = Read-Config
    if (-not $config -or -not $config.token) { return $null }
    try {
        $headers = @{ Authorization = "Bearer $($config.token)" }
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$TargetPort/api/health" -Headers $headers -TimeoutSec 2
        return $r.version
    } catch { return $null }
}
function Test-Bridge {
    $config = Read-Config
    if (-not $config -or -not $config.token) { return $false }
    try {
        $headers = @{ Authorization = "Bearer $($config.token)" }
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$TargetPort/api/health" -Headers $headers -TimeoutSec 2
        return ($r.ok -eq $true -and $r.version)
    } catch { return $false }
}
function Log-Update($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $entry = "[$timestamp] $msg"
    Write-Host $entry
    $dir = Split-Path $logFile -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Add-Content -Path $logFile -Value $entry
}
function Start-Bridge {
    $env:OMPCHAT_BRIDGE_PORT = "$TargetPort"
    $binFile = Join-Path $bridgeDir "omp-bin.txt"
    if (Test-Path $binFile) {
        $saved = (Get-Content -Raw -Encoding UTF8 $binFile).Trim()
        if ($saved) { $env:OMP_BIN = $saved }
    }
    $batchFile = Join-Path $bridgeDir "start-via-task.bat"
    $batchLines = @(
        "@echo off",
        "setlocal",
        "set BRIDGE_DIR=$bridgeDir",
        "set OMPCHAT_BRIDGE_PORT=$TargetPort",
        "cd /d $bridgeDir",
        "`"$node`" `"$bridgeDir\omp-bridge.mjs`" > `"$bridgeDir\bridge.stdout.log`" 2> `"$bridgeDir\bridge.stderr.log`""
    )
    $batchLines | Set-Content -Path $batchFile -Encoding ASCII
    $taskName = "NimayOMPBridge-$TargetPort"
    $action = New-ScheduledTaskAction -Execute $batchFile
    $principal = New-ScheduledTaskPrincipal -UserId "megaxenon" -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 0)
    Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Settings $settings -Force | Out-Null
    Start-ScheduledTask -TaskName $taskName
    Start-Sleep -Seconds 5
}

# Step 1: Verify current bridge
Log-Update "=== Updating port $TargetPort ==="
$currentVersion = Get-Version
Log-Update "Current version: $currentVersion"
if ($currentVersion -ne $ExpectedOldVersion) {
    Log-Update "ABORT: Expected $ExpectedOldVersion but got $currentVersion"
    exit 1
}

# Step 2: Verify target dir
if (-not (Test-Path (Join-Path $bridgeDir "omp-bridge.mjs"))) {
    Log-Update "ABORT: omp-bridge.mjs not found in $bridgeDir"
    exit 1
}
if (-not (Test-Path $configPath)) {
    Log-Update "ABORT: config.json not found in $bridgeDir"
    exit 1
}

# Step 3: Verify new source
if (-not (Test-Path $NewSource)) {
    Log-Update "ABORT: New source file not found: $NewSource"
    exit 1
}
$versionMatch = Select-String -Path $NewSource -Pattern 'const VERSION = "([^"]+)"'
if (-not $versionMatch) {
    Log-Update "ABORT: No VERSION found in new source file"
    exit 1
}
$newVersion = $versionMatch.Matches[0].Groups[1].Value
Log-Update "New source version: $newVersion"
if ([version]$newVersion -le [version]$ExpectedOldVersion) {
    Log-Update "ABORT: New version $newVersion is not higher than $ExpectedOldVersion"
    exit 1
}

# Step 4: Syntax check
Log-Update "Running syntax check on new source..."
$checkResult = & $node --check $NewSource 2>&1
if ($LASTEXITCODE -ne 0) {
    Log-Update "ABORT: Syntax check failed: $checkResult"
    exit 1
}
Log-Update "Syntax check passed"

# Step 5: Backup
$backupFile = Join-Path $bridgeDir "omp-bridge.mjs.bak-$ExpectedOldVersion"
if (-not (Test-Path $backupFile)) {
    Copy-Item (Join-Path $bridgeDir "omp-bridge.mjs") $backupFile -Force
    Log-Update "Backup created: $backupFile"
}

# Step 6: Stop old bridge
$pidFile = Join-Path $bridgeDir "bridge.pid"
$stopped = $false
if (Test-Path $pidFile) {
    $raw = (Get-Content -Raw $pidFile).Trim()
    $processId = 0
    if ([int]::TryParse($raw, [ref]$processId)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
        if ($process -and $process.Name -eq "node.exe" -and $process.CommandLine -and $process.CommandLine.Contains("omp-bridge.mjs")) {
            try {
                Stop-Process -Id $processId -Force -ErrorAction Stop
                Write-Host "Stopped omp-bridge.mjs (PID $processId)"
                $stopped = $true
            } catch {
                Log-Update "Could not stop: $($_.Exception.Message)"
            }
        }
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

if (Test-Bridge) {
    Log-Update "ABORT: Bridge still responding after stop"
    exit 1
}
Log-Update "Bridge stopped successfully"

# Step 7: Copy new bridge
Copy-Item $NewSource (Join-Path $bridgeDir "omp-bridge.mjs") -Force
Log-Update "New bridge copied"

# Step 8: Start new bridge via Task Scheduler
Start-Bridge

# Step 9: Verify with stabilization period
$started = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Milliseconds 500
    if (Test-Bridge) { $started = $true; break }
}

if (-not $started) {
    Log-Update "ERROR: New bridge did not start. Rolling back."
    # Rollback
    $pidFile2 = Join-Path $bridgeDir "bridge.pid"
    if (Test-Path $pidFile2) {
        $raw2 = (Get-Content -Raw $pidFile2).Trim()
        $pid2 = 0
        if ([int]::TryParse($raw2, [ref]$pid2)) {
            Stop-Process -Id $pid2 -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 2
    Copy-Item $backupFile (Join-Path $bridgeDir "omp-bridge.mjs") -Force
    Start-Bridge
    # Stabilization period for rollback
    $rollbackOk = $true
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Milliseconds 500
        if (-not (Test-Bridge)) { $rollbackOk = $false }
    }
    if ($rollbackOk) {
        Log-Update "Rollback successful. Old bridge running and stable."
        [Environment]::Exit(0)
    } else {
        Log-Update "CRITICAL: Rollback failed! Manual intervention needed."
        [Environment]::Exit(1)
    }
}

Log-Update "New bridge started. Verifying stability..."

# Step 10: Stabilization period
$stable = $true
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Milliseconds 500
    if (-not (Test-Bridge)) { $stable = $false; break }
}

if (-not $stable) {
    Log-Update "ERROR: Bridge unstable after start. Rolling back."
    $pidFile2 = Join-Path $bridgeDir "bridge.pid"
    if (Test-Path $pidFile2) {
        $raw2 = (Get-Content -Raw $pidFile2).Trim()
        $pid2 = 0
        if ([int]::TryParse($raw2, [ref]$pid2)) {
            Stop-Process -Id $pid2 -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 2
    Copy-Item $backupFile (Join-Path $bridgeDir "omp-bridge.mjs") -Force
    Start-Bridge
    $rollbackOk = $true
    for ($i = 0; $i -lt 10; $i++) {
        Start-Sleep -Milliseconds 500
        if (-not (Test-Bridge)) { $rollbackOk = $false }
    }
    if ($rollbackOk) {
        Log-Update "Rollback successful. Old bridge running and stable."
        [Environment]::Exit(0)
    } else {
        Log-Update "CRITICAL: Rollback failed! Manual intervention needed."
        [Environment]::Exit(1)
    }
}

# Step 11: Test API endpoints
$newVersion = Get-Version
Log-Update "New version: $newVersion"

$config = Read-Config
$headers = @{ Authorization = "Bearer $($config.token)" }
try {
    $projects = Invoke-RestMethod -Uri "http://127.0.0.1:$TargetPort/api/projects/registry" -Headers $headers -TimeoutSec 5
    Log-Update "Projects endpoint: OK ($($projects.projects.Count) projects)"
} catch {
    Log-Update "Projects endpoint FAILED: $($_.Exception.Message)"
}
try {
    $fs = Invoke-RestMethod -Uri "http://127.0.0.1:$TargetPort/api/fs/list?path=" -Headers $headers -TimeoutSec 5
    Log-Update "FS list endpoint: OK"
} catch {
    Log-Update "FS list endpoint FAILED: $($_.Exception.Message)"
}

Log-Update "Update complete: $ExpectedOldVersion -> $newVersion (STABLE)"
