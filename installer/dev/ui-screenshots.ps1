# Drives the installer wizard like a user and captures a screenshot of every page.
# Must run elevated: the installer elevates itself, and a non-elevated process
# cannot send keystrokes to an elevated window.
#
#   Start-Process powershell -Verb RunAs -ArgumentList '-File', '<this file>'
param(
    [string]$Setup = "C:\Users\Megaxenon\Documents\Opencode\OmpChat\NimayOMP-Setup-2.5.exe",
    [string]$Dir = "$env:TEMP\nimay-ui",
    [string]$OutDir = "$env:TEMP\nimay-ui-shots",
    [string]$LogFile = "$env:TEMP\nimay-ui-driver.log"
)

$ErrorActionPreference = "Continue"
Start-Transcript -Path $LogFile -Force | Out-Null
Add-Type -AssemblyName System.Windows.Forms, System.Drawing, Microsoft.VisualBasic
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

[Win32]::SetProcessDPIAware() | Out-Null

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

function Get-Wizard {
    for ($i = 0; $i -lt 40; $i++) {
        $candidate = Get-Process | Where-Object {
            $_.MainWindowTitle -match "Nimay OMP" -and $_.MainWindowTitle -notmatch "Feil|Error"
        } | Select-Object -First 1
        if ($candidate) { return $candidate }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Capture([string]$name) {
    Start-Sleep -Milliseconds 800
    $wizard = Get-Wizard
    if (-not $wizard) { Write-Host "MISSING WINDOW for $name"; return }
    [Win32]::SetForegroundWindow($wizard.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 500
    $rect = New-Object Win32+RECT
    [Win32]::GetWindowRect($wizard.MainWindowHandle, [ref]$rect) | Out-Null
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -le 0 -or $height -le 0) { Write-Host "BAD RECT for $name"; return }
    $bmp = New-Object System.Drawing.Bitmap($width, $height)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bmp.Size)
    $file = Join-Path $OutDir "$name.png"
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Host "CAPTURED $file ($width x $height) title='$($wizard.MainWindowTitle)'"
}

function Press([string]$keys) {
    $wizard = Get-Wizard
    if (-not $wizard) { Write-Host "no wizard to press $keys"; return }
    [Microsoft.VisualBasic.Interaction]::AppActivate($wizard.Id) | Out-Null
    Start-Sleep -Milliseconds 400
    [System.Windows.Forms.SendKeys]::SendWait($keys)
}

$proc = Start-Process -FilePath $Setup -ArgumentList "/DIR=`"$Dir`"","/COMPONENTS=`"core,stt\whisper_tiny,tts\piper_talesyntese`"","/TASKS=`"`"","/LOG=`"$env:TEMP\nimay-ui-install.log`"" -PassThru
Write-Host "launched pid $($proc.Id)"

$steps = @("01-welcome", "02-destination", "03-components", "04-tasks", "05-ready")
foreach ($step in $steps) {
    Capture $step
    Press "{ENTER}"
}

Write-Host "waiting for the installation to finish..."
for ($i = 0; $i -lt 150; $i++) {
    Start-Sleep -Seconds 2
    $wizard = Get-Wizard
    if (-not $wizard) { Write-Host "wizard gone"; break }
    if ($i -eq 2) { Capture "06-installing" }; Capture "07-mobile"
    $wizard.Refresh()
    if ($wizard.MainWindowTitle -match "Fullfor|Fullf.r|Ferdig|Finish") { Write-Host "reached finish page"; break }
}

Press "{ENTER}"
Capture "08-finished"
Press "{ENTER}"
Start-Sleep -Seconds 2
Write-Host "installed dir exists: $(Test-Path $Dir)"
Get-ChildItem $OutDir | Select-Object Length, Name | Format-Table -AutoSize | Out-String -Width 100
Stop-Transcript | Out-Null


