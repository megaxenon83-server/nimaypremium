# Registrerer OMP-bridgen som en oppgave i Windows Task Scheduler.
# Modus:
#   Boot  - starter ved Windows-oppstart uten innlogging (krever passord)
#   Login - starter ved innlogging (ingen passord nødvendig)
param(
    [string]$BridgeDir = "C:\Program Files\Nimay OMP\bridge",
    [ValidateSet("Boot","Login")]
    [string]$Mode = "Login",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"
$taskName = "Nimay OMP Bridge"
$bridgeScript = Join-Path $BridgeDir "omp-bridge.mjs"
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) { $node = "node" }
if (-not (Test-Path $bridgeScript)) { throw "Bridge ikke funnet: $bridgeScript" }

$action = New-ScheduledTaskAction -Execute $node -Argument "`"$bridgeScript`"" -WorkingDirectory $BridgeDir

if ($Mode -eq "Boot") {
    if ([string]::IsNullOrWhiteSpace($Password)) { throw "Passord kreves for oppstart uten innlogging." }
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Password -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -Hidden
    $task = New-ScheduledTask -Action $action -Trigger $trigger -Principal $principal -Settings $settings
    Register-ScheduledTask -TaskName $taskName -InputObject $task -Password $Password -Force | Out-Null
    Write-Host "Oppgave '$taskName' registrert (starter ved boot som $env:USERNAME, uten innlogging)."
} else {
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -Hidden
    $task = New-ScheduledTask -Action $action -Trigger $trigger -Principal $principal -Settings $settings
    Register-ScheduledTask -TaskName $taskName -InputObject $task -Force | Out-Null
    Write-Host "Oppgave '$taskName' registrert (starter ved innlogging som $env:USERNAME)."
}
