# Stops the OMP bridge and the APK server that belong to THIS installation.
#
# The services write <script dir>\*.pid when they start, so they are found
# reliably even when the install path is passed in short 8.3 form
# (C:\Users\USER~1\...). Processes without a matching PID file are left
# alone, so a bridge started from a source checkout is never killed.
param([string]$InstallDir = (Split-Path -Parent $PSScriptRoot))

$stopped = 0
$pidFiles = @(
    @{ File = Join-Path $InstallDir "bridge\bridge.pid"; Marker = "omp-bridge.mjs" },
    @{ File = Join-Path $InstallDir "tools\apk-server.pid"; Marker = "apk-server.mjs" }
)

foreach ($entry in $pidFiles) {
    if (-not (Test-Path $entry.File)) { continue }
    $raw = (Get-Content -Raw $entry.File).Trim()
    $processId = 0
    if (-not [int]::TryParse($raw, [ref]$processId)) {
        Remove-Item $entry.File -Force -ErrorAction SilentlyContinue
        continue
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
    if ($process -and $process.Name -eq "node.exe" -and $process.CommandLine -and $process.CommandLine.Contains($entry.Marker)) {
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
            Write-Host "Stoppet $($entry.Marker) (PID $processId)"
            $stopped++
        } catch {
            Write-Host "Kunne ikke stoppe $($entry.Marker) (PID $processId): $($_.Exception.Message)"
        }
    } elseif ($process) {
        Write-Host "PID $processId tilhorer ikke $($entry.Marker) - hopper over."
    }
    Remove-Item $entry.File -Force -ErrorAction SilentlyContinue
}

if ($stopped -eq 0) { Write-Host "Ingen kjorende Nimay OMP-tjenester for denne installasjonen." }
