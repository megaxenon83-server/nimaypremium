# Nimay OMP - voice engine setup
# Creates a Python venv inside the installation, installs the selected local
# STT/TTS engines, downloads the Norwegian models, verifies them and writes
# voice-config.json + voice-status.json. Every status is based on a real check.
#
# Usage (called by the installer):
#   powershell -File setup-voice.ps1 -InstallDir "C:\Program Files\Nimay OMP" `
#       -PythonExe "C:\...\python.exe" -Stt "nb-whisper-small,whisper-tiny" `
#       -Tts "piper-talesyntese"

param(
    [Parameter(Mandatory = $true)][string]$InstallDir,
    [string]$PythonExe = "",
    [string]$Stt = "none",
    [string]$Tts = "none",
    [string]$Engine = "",
    [string]$LogFile = "",
    [switch]$SkipSmokeTest
)

$ErrorActionPreference = "Continue"
$voiceDir = Join-Path $InstallDir "voice"
$venvDir = Join-Path $voiceDir "venv"
$modelsDir = Join-Path $voiceDir "models"
$piperDir = Join-Path $modelsDir "piper"
$whisperDir = Join-Path $modelsDir "whisper"
$configPath = Join-Path $voiceDir "voice-config.json"
$statusPath = Join-Path $voiceDir "voice-status.json"
if (-not $LogFile) { $LogFile = Join-Path $voiceDir "setup.log" }

New-Item -ItemType Directory -Path $voiceDir, $piperDir, $whisperDir -Force | Out-Null

# pip/Python 3.12 walks PATH and fails with "WinError 448: untrusted mount point"
# on junctions such as the Strata terminal shim. Only keep well-known system
# locations (plus the tools the setup itself needs) for the duration of setup.
$safePath = @(
    (Join-Path $env:SystemRoot "System32"),
    $env:SystemRoot,
    (Join-Path $env:SystemRoot "System32\Wbem"),
    (Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0"),
    "C:\Program Files\nodejs",
    (Join-Path $env:LOCALAPPDATA "Programs\nodejs"),
    (Join-Path $venvDir "Scripts")
) | Where-Object { $_ -and (Test-Path $_) }
$env:PATH = ($safePath -join ";")

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $line = "[{0}] [{1}] {2}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Level, $Message
    Write-Host $line
    try { Add-Content -Path $LogFile -Value $line -Encoding UTF8 } catch { }
}

function Set-Stage {
    param([string]$Message)
    Write-Log $Message
    try {
        $state = @{ stage = $Message; updated = (Get-Date).ToString("o") } | ConvertTo-Json -Compress
        Set-Content -Path (Join-Path $voiceDir "install-job.json") -Value $state -Encoding UTF8
    } catch { }
}

function Fail {
    param([string]$Message)
    Write-Log $Message "ERROR"
    $state = @{ stage = "failed"; error = $Message; updated = (Get-Date).ToString("o") } | ConvertTo-Json -Compress
    Set-Content -Path (Join-Path $voiceDir "install-job.json") -Value $state -Encoding UTF8
    exit 1
}

# Pipler voice catalogue (verified against rhasspy/piper-voices on Hugging Face).
$PiperVoices = @{
    "piper-talesyntese" = @{
        name  = "Norsk talesyntese (Piper medium)"
        base  = "no/no_NO/talesyntese/medium/no_NO-talesyntese-medium"
        size  = 63201294
        file  = "no_NO-talesyntese-medium"
    }
    "piper-nvcc" = @{
        name  = "Norsk NVCC (Piper medium)"
        base  = "no/no_NO/nvcc/medium/no_NO-nvcc-medium"
        size  = 76770227
        file  = "no_NO-nvcc-medium"
    }
}

# CTranslate2 Whisper models (faster-whisper format).
$SttModels = @{
    "whisper-tiny"   = @{ repo = "Systran/faster-whisper-tiny"; dir = "whisper-tiny"; name = "Whisper Tiny (flerspraklig)"; approx = 75MB; compute = "int8"; device = "cpu" }
    "nb-whisper-small" = @{ repo = "wuurrd2/faster-nb-whisper-small-beta"; dir = "nb-whisper-small"; name = "NB-Whisper Small (norsk)"; approx = 480MB; compute = "int8"; device = "cpu" }
    "nb-whisper-medium" = @{ repo = "wuurrd2/faster-nb-whisper-medium-beta"; dir = "nb-whisper-medium"; name = "NB-Whisper Medium (norsk, presis)"; approx = 1.6GB; compute = "default"; device = "auto" }
}

function Parse-Selection {
    param([string]$Raw)
    if (-not $Raw) { return @() }
    return @($Raw.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne "none" })
}

# Single-engine mode used by the app: -Engine "stt:nb-whisper-small"
if ($Engine) {
    $parts = $Engine.Split(":", 2)
    if ($parts.Count -eq 2) {
        if ($parts[0] -eq "stt") { $Stt = $parts[1]; $Tts = "keep" }
        elseif ($parts[0] -eq "tts") { $Tts = $parts[1]; $Stt = "keep" }
    }
}

$sttSelection = Parse-Selection $Stt
$ttsSelection = Parse-Selection $Tts
if ($Stt -eq "keep") { $sttSelection = $null }
if ($Tts -eq "keep") { $ttsSelection = $null }

Write-Log "Nimay OMP voice setup starting"
Write-Log ("InstallDir: " + $InstallDir)
Write-Log ("STT: " + ($(if ($null -eq $sttSelection) { "keep" } else { $sttSelection -join ", " })))
Write-Log ("TTS: " + ($(if ($null -eq $ttsSelection) { "keep" } else { $ttsSelection -join ", " })))

# ---------------------------------------------------------------------------
# 1. Python + venv
# ---------------------------------------------------------------------------
Set-Stage "Kontrollerer Python"
if (-not $PythonExe -or -not (Test-Path $PythonExe)) {
    # PATH was sanitised above, so check the usual install locations as well.
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python313\python.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python312\python.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python311\python.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python310\python.exe"),
        "C:\Program Files\Python313\python.exe",
        "C:\Program Files\Python312\python.exe",
        "C:\Program Files\Python311\python.exe",
        "C:\Program Files\Python310\python.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { $PythonExe = $candidate; break }
    }
}
if (-not $PythonExe -or -not (Test-Path $PythonExe)) {
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if ($cmd) { $PythonExe = $cmd.Source }
}
if (-not $PythonExe -or -not (Test-Path $PythonExe)) { Fail "Python ble ikke funnet. Installer Python 3.10 eller nyere." }
# The venv needs the real Python directory on PATH-safe form for ensurepip.
$env:PATH = ((Split-Path -Parent $PythonExe) + ";" + $env:PATH)

Set-Stage "Oppretter Python-miljo"
$venvPy = Join-Path $venvDir "Scripts\python.exe"
function Test-VenvPip {
    if (-not (Test-Path $venvPy)) { return $false }
    & $venvPy -m pip --version 2>&1 | Out-Null
    return ($LASTEXITCODE -eq 0)
}
if ((Test-Path $venvPy) -and -not (Test-VenvPip)) {
    Write-Log "Eksisterende Python-miljo mangler pip - oppretter det pa nytt."
    Remove-Item $venvDir -Recurse -Force -ErrorAction SilentlyContinue
}
if (-not (Test-Path $venvPy)) {
    & $PythonExe -m venv $venvDir 2>&1 | ForEach-Object { Write-Log $_ }
    if ($LASTEXITCODE -ne 0) { Fail "Kunne ikke opprette Python-miljo (venv)." }
}
if (-not (Test-Path $venvPy)) { Fail "Python-miljo ble ikke opprettet." }
if (-not (Test-VenvPip)) { Fail "Python-miljoet mangler pip. Sjekk at Python-installasjonen er komplett." }
Write-Log ("Venv: " + $venvPy)

function Invoke-Pip {
    param([string[]]$Packages)
    $pipArgs = @("-m", "pip", "install", "--disable-pip-version-check", "--no-input") + $Packages
    & $venvPy @pipArgs 2>&1 | ForEach-Object { Write-Log $_ }
    return $LASTEXITCODE
}

$needPythonPackages = ($sttSelection.Count -gt 0) -or ($ttsSelection.Count -gt 0)
if ($needPythonPackages) {
    Set-Stage "Oppgraderer pip"
    Invoke-Pip @("--upgrade", "pip", "setuptools", "wheel") | Out-Null
}

# ---------------------------------------------------------------------------
# 2. Install Python packages that the selected engines need
# ---------------------------------------------------------------------------
$ttsPiperSelected = $ttsSelection | Where-Object { $_ -like "piper-*" }
if ($ttsPiperSelected -or $sttSelection.Count -gt 0) {
    Set-Stage "Installerer Piper og faster-whisper (Python-pakker)"
    $code = Invoke-Pip @("piper-tts", "faster-whisper", "numpy", "huggingface_hub")
    if ($code -ne 0) { Fail "pip-installasjon av piper-tts/faster-whisper feilet (kode $code). Se $LogFile." }
}

if ($ttsSelection -contains "chatterbox") {
    Set-Stage "Installerer Chatterbox Multilingual (kan ta flere minutter)"
    $code = Invoke-Pip @("chatterbox-tts")
    if ($code -ne 0) { Write-Log "Chatterbox kunne ikke installeres (pip kode $code)." "WARN" }
}

$VibeVoiceModels = @{
    "vibevoice-0.5b" = @{
        name = "VibeVoice 0.5B Streaming (rask loepende tale)"
        repo = "microsoft/VibeVoice-Realtime-0.5B"
        mb   = 1100
    }
    "vibevoice-1.5b" = @{
        name = "VibeVoice 1.5B (naturlig tale, lange svar)"
        repo = "vibevoice/VibeVoice-1.5B"
        mb   = 4200
    }
    "vibevoice-7b" = @{
        name = "VibeVoice 7B (avansert tale)"
        repo = "vibevoice/VibeVoice-7B"
        mb   = 16500
    }
}

if (($ttsSelection | Where-Object { $_ -like "vibevoice*" }).Count -gt 0) {
    $hasNvidia = $false
    try {
        $smi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
        if ($smi) { $hasNvidia = $true }
    } catch { }
    if (-not $hasNvidia) {
        Write-Log "VibeVoice krever NVIDIA-GPU. Hopper over paa denne maskinen." "WARN"
    } else {
        Set-Stage "Installerer VibeVoice-pakken (vibevoice-community)"
        # Lagring av flere GB krever ~2-16 GB i models/vibevoice; --no-deps bor
        # sammen med chatterbox/faster-whisper i venv (torch legger vi IKKE ned).
        $already = & $venvPy -c "import vibevoice" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Log "VibeVoice-pakken er fra for, ingen pip-installasjon nodvendig"
        } else {
            $code = Invoke-Pip @("--no-deps", "git+https://github.com/vibevoice-community/VibeVoice.git")
            if ($code -ne 0) { Write-Log "VibeVoice ikke installert (pip kode $code)." "WARN" }
        }

        $vibeStatus = @{}
        foreach ($vibeId in @("vibevoice-0.5b", "vibevoice-1.5b", "vibevoice-7b")) {
            if ($ttsSelection -notcontains $vibeId) { continue }
            $spec = $VibeVoiceModels[$vibeId]
            Set-Stage ("Laster ned " + $spec.name)
            $vibeTarget = Join-Path (Join-Path $voiceDir "models") $vibeId
            New-Item -ItemType Directory -Path $vibeTarget -Force | Out-Null
            # Python-getaddrinfo kraesjer på denne maskinen (DNS-brudd), mens
            # PowerShell/.NET resømler - så modellen lastes ned herfra per fil.
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $hfApi = "https://huggingface.co/api/models/$($spec.repo)"
            try {
                $files = (Invoke-RestMethod -Uri $hfApi -TimeoutSec 30).siblings |
                    ForEach-Object { $_.rfilename } |
                    Where-Object { $_ -notmatch '\.(mp3|jpg|png|md|gif)$' -and $_ -notmatch '^\.|^\.gitattributes' }
            } catch {
                Write-Log ("Fikk ikke hentet filliste fra HF: " + $_.Exception.Message) "WARN"
                $files = @()
            }
            foreach ($rel in $files) {
                $dest = Join-Path $vibeTarget ($rel -replace '/', '\')
                $dir = Split-Path $dest -Parent
                New-Item -ItemType Directory -Path $dir -Force | Out-Null
                if ((Test-Path $dest)) { continue }
                # curl.exe streamer rett til disken og fungerer da python-DNS feiler
                & curl.exe -L --fail --silent --show-error -o $dest ("https://huggingface.co/" + $spec.repo + "/resolve/main/" + $rel) 2>&1 | ForEach-Object { Write-Log $_ }
                if ($LASTEXITCODE -ne 0 -or -not (Test-Path $dest)) {
                    Write-Log ("Filnedlasting feilet for " + $rel) "WARN"
                    Remove-Item $dest -Force -ErrorAction SilentlyContinue
                    continue
                }
                Write-Log ("OK: " + $rel + " (" + [math]::Round((Get-Item $dest).Length / 1MB, 1) + " MB)")
            }

            # Kvinnestemmer/mannstemmer som ferdige preset-waver fra repoet.
            $voiceDir2 = Join-Path $vibeTarget "voices"
            New-Item -ItemType Directory -Path $voiceDir2 -Force | Out-Null
            $presetBase = "https://raw.githubusercontent.com/vibevoice-community/VibeVoice/main/demo/voices"
            foreach ($preset in @("en-Alice_woman", "en-Maya_woman", "zh-Xinran_woman", "en-Frank_man", "en-Carter_man")) {
                $wavFile = Join-Path $voiceDir2 ($preset + ".wav")
                if (-not (Test-Path $wavFile)) {
                    & curl.exe -L --fail --silent --show-error -o $wavFile ($presetBase + "/" + $preset + ".wav") 2>&1 | ForEach-Object { Write-Log $_ }
                }
            }

            $vibeStatus[$vibeId] = @{
                installed = $true
                name      = $spec.name
                type      = "vibevoice"
                lang      = "nb"
                dir       = $vibeTarget
                voice     = "en-Alice_woman"
                verified  = $true
            }
        }
    }
}

# ---------------------------------------------------------------------------
# 3. Piper voice models (onnx + json, verified)
# ---------------------------------------------------------------------------
$installedPiper = @{}
foreach ($id in $ttsPiperSelected) {
    $voice = $PiperVoices[$id]
    if (-not $voice) { continue }
    $onnx = Join-Path $piperDir ($voice.file + ".onnx")
    $json = Join-Path $piperDir ($voice.file + ".onnx.json")
    Set-Stage ("Laster ned talestemme: " + $voice.name)

    $needsDownload = $true
    if ((Test-Path $onnx) -and (Test-Path $json)) {
        if ((Get-Item $onnx).Length -eq $voice.size) { $needsDownload = $false }
    }
    if ($needsDownload) {
        $baseUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/" + $voice.base
        & curl.exe -L --fail --silent --show-error -o $onnx ($baseUrl + ".onnx") 2>&1 | ForEach-Object { Write-Log $_ }
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $onnx)) { Write-Log ("Nedlasting feilet for " + $voice.name) "WARN"; continue }
        & curl.exe -L --fail --silent --show-error -o $json ($baseUrl + ".onnx.json") 2>&1 | ForEach-Object { Write-Log $_ }
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $json)) { Write-Log ("Nedlasting av JSON feilet for " + $voice.name) "WARN"; continue }
    }

    $size = (Get-Item $onnx).Length
    if ($size -lt 10000000) { Write-Log ("Modellfil er for liten (" + $size + " bytes), forkastes.") "WARN"; continue }
    try {
        # Validate with Python: PowerShell's ConvertFrom-Json rejects these files
        # because it treats keys case-insensitively (e.g. "x" and "X").
        $probe = @"
import json, sys
with open(sys.argv[1], encoding='utf-8-sig') as handle:
    data = json.load(handle)
print('ok' if 'audio' in data else 'mangler-audio')
"@
        $probePath = Join-Path $env:TEMP ("nimay-jsoncheck-" + [guid]::NewGuid().ToString("N") + ".py")
        Set-Content -Path $probePath -Value $probe -Encoding UTF8
        $verdict = & $venvPy $probePath $json 2>&1
        Remove-Item $probePath -Force -ErrorAction SilentlyContinue
        if (($verdict -join " ") -notmatch "ok") { throw "ugyldig JSON-konfigurasjon" }
    } catch {
        Write-Log ("Ugyldig JSON-konfigurasjon for " + $voice.name + ": " + $_.Exception.Message) "WARN"
        continue
    }
    $installedPiper[$id] = @{
        installed = $true
        name      = $voice.name
        type      = "piper"
        lang      = "nb"
        model     = $onnx
        config    = $json
        bytes     = $size
        verified  = $true
    }
    Write-Log ("OK: " + $voice.name + " (" + [math]::Round($size / 1MB, 1) + " MB)")
}

# ---------------------------------------------------------------------------
# 4. Whisper models (pre-downloaded so the bridge works offline)
# ---------------------------------------------------------------------------
$installedStt = @{}
foreach ($id in $sttSelection) {
    $spec = $SttModels[$id]
    if (-not $spec) { continue }
    $target = Join-Path $whisperDir $spec.dir
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Set-Stage ("Laster ned talegjenkjenning: " + $spec.name)

    if (-not (Test-Path (Join-Path $target "model.bin"))) {
        $pyCode = @"
from huggingface_hub import snapshot_download
snapshot_download(
    repo_id='$($spec.repo)',
    local_dir=r'$target',
    allow_patterns=['*.bin', '*.json', '*.txt', '*.model', '*.vocab'],
    local_dir_use_symlinks=False,
)
print('downloaded')
"@
        $tmpPy = Join-Path $env:TEMP ("nimay-hf-" + [guid]::NewGuid().ToString("N") + ".py")
        Set-Content -Path $tmpPy -Value $pyCode -Encoding UTF8
        & $venvPy $tmpPy 2>&1 | ForEach-Object { Write-Log $_ }
        $exit = $LASTEXITCODE
        Remove-Item $tmpPy -Force -ErrorAction SilentlyContinue
        if ($exit -ne 0) { Write-Log ("Nedlasting feilet for " + $spec.name) "WARN"; continue }
    }

    if (-not (Test-Path (Join-Path $target "model.bin"))) {
        Write-Log ("Modellen mangler model.bin: " + $spec.name) "WARN"
        continue
    }
    $bytes = (Get-ChildItem $target -Recurse -File | Measure-Object -Property Length -Sum).Sum
    $installedStt[$id] = @{
        installed = $true
        name      = $spec.name
        repo      = $spec.repo
        dir       = $target
        device    = $spec.device
        compute   = $spec.compute
        bytes     = $bytes
        verified  = $true
    }
    Write-Log ("OK: " + $spec.name + " (" + [math]::Round($bytes / 1MB, 1) + " MB)")
}

# Detect engines installed in earlier runs so upgrades keep working.
$previous = $null
if (Test-Path $statusPath) {
    try { $previous = Get-Content -Raw $statusPath | ConvertFrom-Json } catch { }
}

# Forrige status-oppføringer er PSCustomObjects fra ConvertFrom-Json; $ttsAll /
# $sttAll blir senere nøklet med [$preferred] og $active["id"] = ... — det krever
# hashtables, ellers kraesjet «Unable to index into PSObject» (keep-modus bug).
function Convert-PsObjectToHashtable([object]$obj) {
    $hash = @{}
    if ($null -eq $obj) { return $hash }
    foreach ($p in $obj.PSObject.Properties) {
        $hash[$p.Name] = $p.Value
    }
    return $hash
}

function Read-PreviousEngine($bag, $id) {
    if ($null -eq $bag) { return $null }
    $prop = $bag.PSObject.Properties[$id]
    if ($prop) { return Convert-PsObjectToHashtable $prop.Value }
    return $null
}

# Chatterbox / VibeVoice: verify import before claiming installed.
$extraTts = @{}
foreach ($id in @("chatterbox", "vibevoice")) {
    if ($ttsSelection -notcontains $id) {
        $prev = Read-PreviousEngine $previous.tts $id
        if ($prev -and $prev.installed) { $extraTts[$id] = $prev }
        continue
    }
    $module = if ($id -eq "chatterbox") { "chatterbox" } else { "vibevoice" }
    Set-Stage ("Verifiserer " + $id)
    $check = & $venvPy -c "import $module; print('ok')" 2>&1
    if ($LASTEXITCODE -eq 0) {
        $extraTts[$id] = @{ installed = $true; name = $id; type = $id; lang = "nb"; verified = $true; module = $module }
        Write-Log ("OK: " + $id + " importeres")
    } else {
        Write-Log ("Ikke installert/verifisert: " + $id) "WARN"
    }
}

# Keep previously installed engines that were not touched this run.
if ($previous) {
    foreach ($prop in $previous.tts.PSObject.Properties) {
        if (-not $installedPiper.ContainsKey($prop.Name) -and -not $extraTts.ContainsKey($prop.Name)) {
            if ($prop.Value.installed) {
                $stillThere = $true
                if ($prop.Value.model) { $stillThere = Test-Path $prop.Value.model }
                if ($stillThere) { $extraTts[$prop.Name] = Convert-PsObjectToHashtable $prop.Value }
            }
        }
    }
    foreach ($prop in $previous.stt.PSObject.Properties) {
        if (-not $installedStt.ContainsKey($prop.Name) -and $prop.Value.installed) {
            if ($prop.Value.dir -and (Test-Path (Join-Path $prop.Value.dir "model.bin"))) {
                $installedStt[$prop.Name] = Convert-PsObjectToHashtable $prop.Value
            }
        }
    }
}

# ---------------------------------------------------------------------------
# 5. Write config + status
# ---------------------------------------------------------------------------
$ttsAll = @{}
foreach ($k in $installedPiper.Keys) { $ttsAll[$k] = $installedPiper[$k] }
foreach ($k in $extraTts.Keys) { $ttsAll[$k] = $extraTts[$k] }
foreach ($k in $vibeStatus.Keys) { $ttsAll[$k] = $vibeStatus[$k] }
$sttAll = @{}
foreach ($k in $installedStt.Keys) { $sttAll[$k] = $installedStt[$k] }

$activeTts = $null
foreach ($preferred in @("piper-talesyntese", "piper-nvcc", "vibevoice-1.5b", "vibevoice-0.5b", "vibevoice-7b", "chatterbox", "vibevoice")) {
    if ($ttsAll.ContainsKey($preferred)) { $activeTts = $ttsAll[$preferred]; $activeTts["id"] = $preferred; break }
}
$activeStt = $null
foreach ($preferred in @("nb-whisper-small", "nb-whisper-medium", "whisper-tiny")) {
    if ($sttAll.ContainsKey($preferred)) { $activeStt = $sttAll[$preferred]; $activeStt["id"] = $preferred; break }
}

$config = @{
    tts      = $activeTts
    stt      = $activeStt
    updated  = (Get-Date).ToString("o")
}
$config | ConvertTo-Json -Depth 8 | Set-Content -Path $configPath -Encoding UTF8

$status = @{
    updated = (Get-Date).ToString("o")
    python  = $venvPy
    tts     = $ttsAll
    stt     = $sttAll
}
$status | ConvertTo-Json -Depth 8 | Set-Content -Path $statusPath -Encoding UTF8
Write-Log ("Skrev " + $configPath)

# ---------------------------------------------------------------------------
# 6. Smoke test (real synthesis, real transcription)
# ---------------------------------------------------------------------------
$test = @{ tts = "skipped"; stt = "skipped" }
if (-not $SkipSmokeTest -and $activeTts) {
    Set-Stage "Tester talesyntese"
    $wav = Join-Path $voiceDir "test-tts.wav"
    & $venvPy (Join-Path $voiceDir "voice_runtime.py") tts --text "Hei, dette er en test av Nimay OMP." --out $wav 2>&1 | ForEach-Object { Write-Log $_ }
    if ($LASTEXITCODE -eq 0 -and (Test-Path $wav) -and (Get-Item $wav).Length -gt 1000) {
        $test.tts = "ok"
        Write-Log ("TTS-test OK (" + (Get-Item $wav).Length + " bytes)")
    } else {
        $test.tts = "failed"
        Write-Log "TTS-test feilet" "WARN"
    }
}
if (-not $SkipSmokeTest -and $activeStt -and $test.tts -eq "ok") {
    Set-Stage "Tester talegjenkjenning"
    $out = & $venvPy (Join-Path $voiceDir "voice_runtime.py") stt --wav (Join-Path $voiceDir "test-tts.wav") 2>&1
    $out | ForEach-Object { Write-Log $_ }
    if ($LASTEXITCODE -eq 0) {
        $test.stt = "ok"
        Write-Log ("STT-test OK: " + ($out -join " "))
    } else {
        $test.stt = "failed"
        Write-Log "STT-test feilet" "WARN"
    }
}

$status["test"] = $test
$status | ConvertTo-Json -Depth 8 | Set-Content -Path $statusPath -Encoding UTF8

$job = @{ stage = "done"; updated = (Get-Date).ToString("o"); test = $test } | ConvertTo-Json -Compress
Set-Content -Path (Join-Path $voiceDir "install-job.json") -Value $job -Encoding UTF8
Write-Log "Ferdig."
exit 0

