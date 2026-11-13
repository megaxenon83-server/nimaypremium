# Verifies that the voice stack the installer ships actually works on this PC.
# Creates a throwaway venv, installs Piper TTS + faster-whisper, synthesises a
# Norwegian sentence, then transcribes it back and prints both results.
$ErrorActionPreference = "Stop"
$root = Join-Path $env:TEMP "nimay-voice-test"
New-Item -ItemType Directory -Path $root -Force | Out-Null
$venv = Join-Path $root "venv"
$py = Join-Path $venv "Scripts\python.exe"

if (-not (Test-Path $py)) {
    Write-Host "[1/5] Creating venv..."
    python -m venv $venv
} else {
    Write-Host "[1/5] Reusing venv"
}

Write-Host "[2/5] Installing piper-tts and faster-whisper..."
& $py -m pip install --disable-pip-version-check --quiet --upgrade pip
& $py -m pip install --disable-pip-version-check --quiet piper-tts faster-whisper
if ($LASTEXITCODE -ne 0) { throw "pip install failed" }

$voices = Join-Path $root "voices"
New-Item -ItemType Directory -Path $voices -Force | Out-Null
$onnx = Join-Path $voices "no_NO-talesyntese-medium.onnx"
$json = Join-Path $voices "no_NO-talesyntese-medium.onnx.json"
if (-not (Test-Path $onnx)) {
    Write-Host "[3/5] Downloading Norwegian Piper voice (63 MB)..."
    curl.exe -s -L -o $onnx "https://huggingface.co/rhasspy/piper-voices/resolve/main/no/no_NO/talesyntese/medium/no_NO-talesyntese-medium.onnx"
    curl.exe -s -L -o $json "https://huggingface.co/rhasspy/piper-voices/resolve/main/no/no_NO/talesyntese/medium/no_NO-talesyntese-medium.onnx.json"
} else {
    Write-Host "[3/5] Voice already downloaded"
}

$wav = Join-Path $root "test.wav"
Write-Host "[4/5] Synthesising Norwegian speech..."
& $py -c @"
import wave, sys
from piper import PiperVoice
v = PiperVoice.load(r'$onnx', config_path=r'$json')
with wave.open(r'$wav', 'wb') as w:
    v.synthesize_wav('Hei, dette er en test av norsk talesyntese fra Nimay OMP.', w)
print('ok')
"@
if ($LASTEXITCODE -ne 0) { throw "piper synthesis failed" }
Write-Host ("      wav bytes: " + (Get-Item $wav).Length)

Write-Host "[5/5] Transcribing with faster-whisper (nb-whisper small)..."
& $py -c @"
from faster_whisper import WhisperModel
m = WhisperModel('wuurrd2/faster-nb-whisper-small-beta', device='cpu', compute_type='int8')
segs, info = m.transcribe(r'$wav', language='no', beam_size=1)
text = ' '.join(s.text.strip() for s in segs)
print('TRANSCRIPT:', text)
"@
if ($LASTEXITCODE -ne 0) { throw "faster-whisper transcription failed" }
Write-Host "SMOKE TEST OK"
