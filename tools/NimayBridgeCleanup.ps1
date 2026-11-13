# Nimay OMP Bridge Cleanup Tool
# Self-contained PowerShell script with Windows Forms GUI
# Finds and removes all old Nimay OMP Bridge installations

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$Script:LogFile = Join-Path $env:TEMP "NimayBridgeCleanup.log"
$Script:Findings = @()
$Script:BridgePorts = @(7777, 7778, 7779, 7780, 7781, 7782, 7783, 7784, 7785, 7786, 7787)

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $entry = "[$timestamp] [$Level] $Message"
    Add-Content -Path $Script:LogFile -Value $entry -ErrorAction SilentlyContinue
}

function Normalize-Mac {
    param([string]$Mac)
    if (-not $Mac) { return $null }
    $clean = $Mac -replace '[:-]', '' -replace '\.', ''
    if ($clean.Length -ne 12) { return $null }
    if ($clean -notmatch '^[0-9A-Fa-f]+$') { return $null }
    return ($clean -replace '(.{2})', '$1:').TrimEnd(':').ToUpper()
}

function Get-NetworkInterfaces {
    $result = @()
    try {
        $interfaces = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()
        foreach ($iface in $interfaces) {
            $mac = $iface.GetPhysicalAddress().ToString()
            if ($mac -and $mac -ne "000000000000" -and $mac.Length -eq 12) {
                $normalizedMac = Normalize-Mac $mac
                if ($normalizedMac) {
                    $isPhysical = $iface.NetworkInterfaceType -in @('Ethernet', 'Ethernet3Megabit', 'FastEthernetFx', 'FastEthernetT', 'GigabitEthernet', 'Wireless80211')
                    $isLoopback = $iface.NetworkInterfaceType -eq 'Loopback'
                    $isTunnel = $iface.NetworkInterfaceType -in @('Tunnel', 'Ppp')
                    $name = $iface.Name.ToLower()
                    $isVirtual = $name -match 'virtual|vpn|tap|tun|hyper-v|vmware|virtualbox|docker|wsl|tailscale|pseudo|loopback'
                    if (-not $isLoopback -and -not $isTunnel -and -not $isVirtual) {
                        $result += [PSCustomObject]@{
                            Name = $iface.Name
                            Description = $iface.Description
                            Mac = $normalizedMac
                            Type = $iface.NetworkInterfaceType.ToString()
                            IsPhysical = $isPhysical
                            Status = $iface.OperationalStatus.ToString()
                        }
                    }
                }
            }
        }
    } catch {
        Write-Log "Feil ved henting av nettverkskort: $_" "ERROR"
    }
    return $result
}

function Find-BridgeProcesses {
    $result = @()
    try {
        $processes = Get-CimInstance Win32_Process -Filter "Name='node.exe' OR Name='nodejs.exe'" -ErrorAction SilentlyContinue
        foreach ($proc in $processes) {
            $cmdLine = $proc.CommandLine
            if (-not $cmdLine) { continue }
            if ($cmdLine -match 'omp-bridge' -or $cmdLine -match 'omp_bridge') {
                $workingDir = ""
                try {
                    $workingDir = (Get-Process -Id $proc.ProcessId -ErrorAction SilentlyContinue).Path
                } catch { }
                $result += [PSCustomObject]@{
                    PID = $proc.ProcessId
                    Name = $proc.Name
                    CommandLine = $cmdLine
                    WorkingDirectory = $proc.ExecutablePath
                    User = $proc.GetOwner().User
                    ParentPID = $proc.ParentProcessId
                    Confirmed = $true
                }
            }
        }
    } catch {
        Write-Log "Feil ved prosess-søk: $_" "ERROR"
    }
    return $result
}

function Find-BridgeInstallations {
    $result = @()
    $searchPaths = @(
        "$env:ProgramFiles\Nimay OMP",
        "${env:ProgramFiles(x86)}\Nimay OMP",
        "$env:LOCALAPPDATA\Nimay",
        "$env:APPDATA\Nimay",
        "$env:ProgramData\Nimay",
        "$env:LOCALAPPDATA\Nimay\OMPBridge"
    )
    
    # Søk i registrerte avinstallasjonsoppføringer
    $uninstallKeys = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*"
    )
    foreach ($key in $uninstallKeys) {
        try {
            $items = Get-ItemProperty $key -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -match 'Nimay|OMP.*Bridge|OMPBridge' }
            foreach ($item in $items) {
                if ($item.InstallLocation -and (Test-Path $item.InstallLocation)) {
                    $result += [PSCustomObject]@{
                        Type = "Registry"
                        Path = $item.InstallLocation
                        DisplayName = $item.DisplayName
                        UninstallString = $item.UninstallString
                        Confirmed = $true
                    }
                }
            }
        } catch { }
    }
    
    # Søk i vanlige stier
    foreach ($path in $searchPaths) {
        if (Test-Path $path) {
            $bridgeFiles = Get-ChildItem -Path $path -Recurse -Filter "omp-bridge.mjs" -ErrorAction SilentlyContinue
            foreach ($file in $bridgeFiles) {
                $result += [PSCustomObject]@{
                    Type = "File"
                    Path = $file.DirectoryName
                    DisplayName = "Bridge file: $($file.FullName)"
                    UninstallString = $null
                    Confirmed = $true
                }
            }
        }
    }
    
    # Søk i Startup-mapper
    $startupPaths = @(
        "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup",
        "$env:ProgramData\Microsoft\Windows\Start Menu\Programs\Startup"
    )
    foreach ($path in $startupPaths) {
        if (Test-Path $path) {
            $shortcuts = Get-ChildItem -Path $path -Filter "*.lnk" -ErrorAction SilentlyContinue | Where-Object {
                $shell = New-Object -ComObject WScript.Shell
                $target = $shell.CreateShortcut($_.FullName).TargetPath
                $target -match 'omp-bridge|Nimay|OMPBridge'
            }
            foreach ($shortcut in $shortcuts) {
                $result += [PSCustomObject]@{
                    Type = "Startup Shortcut"
                    Path = $shortcut.FullName
                    DisplayName = "Startup shortcut: $($shortcut.Name)"
                    UninstallString = $null
                    Confirmed = $true
                }
            }
        }
    }
    
    return $result
}

function Find-BridgeTasks {
    $result = @()
    try {
        $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { 
            $_.TaskName -match 'Nimay|OMP.*Bridge|OMPBridge' -or 
            ($_.Actions | Where-Object { $_.Execute -match 'omp-bridge|Nimay|OMPBridge' })
        }
        foreach ($task in $tasks) {
            $action = $task.Actions | Select-Object -First 1
            $result += [PSCustomObject]@{
                Name = $task.TaskName
                Path = $task.TaskPath
                State = $task.State.ToString()
                Execute = $action.Execute
                Arguments = $action.Arguments
                WorkingDirectory = $action.WorkingDirectory
                Confirmed = $true
            }
        }
    } catch {
        Write-Log "Feil ved Task Scheduler-søk: $_" "ERROR"
    }
    return $result
}

function Find-BridgeServices {
    $result = @()
    try {
        $services = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'Nimay|OMP.*Bridge|OMPBridge' }
        foreach ($svc in $services) {
            $result += [PSCustomObject]@{
                Name = $svc.Name
                DisplayName = $svc.DisplayName
                Status = $svc.Status.ToString()
                StartType = $svc.StartType.ToString()
                Confirmed = $true
            }
        }
    } catch {
        Write-Log "Feil ved tjeneste-søk: $_" "ERROR"
    }
    return $result
}

function Find-BridgeFirewallRules {
    $result = @()
    try {
        $rules = Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -match 'Nimay|OMP.*Bridge|OMPBridge' -or $_.Name -match 'Nimay|OMP.*Bridge|OMPBridge' }
        foreach ($rule in $rules) {
            $result += [PSCustomObject]@{
                Name = $rule.Name
                DisplayName = $rule.DisplayName
                Enabled = $rule.Enabled.ToString()
                Direction = $rule.Direction.ToString()
                Confirmed = $true
            }
        }
    } catch {
        Write-Log "Feil ved brannmur-søk: $_" "ERROR"
    }
    return $result
}

function Stop-BridgeProcesses {
    param([array]$Processes)
    foreach ($proc in $Processes) {
        try {
            Stop-Process -Id $proc.PID -Force -ErrorAction Stop
            Write-Log "Stoppet prosess PID $($proc.PID): $($proc.CommandLine)"
        } catch {
            Write-Log "Kunne ikke stoppe prosess PID $($proc.PID): $_" "ERROR"
        }
    }
}

function Remove-BridgeTasks {
    param([array]$Tasks)
    foreach ($task in $Tasks) {
        try {
            Unregister-ScheduledTask -TaskName $task.Name -TaskPath $task.Path -Confirm:$false -ErrorAction Stop
            Write-Log "Fjernet Task Scheduler-oppgave: $($task.Name)"
        } catch {
            Write-Log "Kunne ikke fjerne Task Scheduler-oppgave $($task.Name): $_" "ERROR"
        }
    }
}

function Remove-BridgeServices {
    param([array]$Services)
    foreach ($svc in $Services) {
        try {
            $service = Get-Service -Name $svc.Name -ErrorAction SilentlyContinue
            if ($service) {
                if ($service.Status -eq "Running") {
                    Stop-Service -Name $svc.Name -Force -ErrorAction SilentlyContinue
                    Start-Sleep -Seconds 2
                }
                sc.exe delete $svc.Name | Out-Null
                Write-Log "Fjernet tjeneste: $($svc.Name)"
            }
        } catch {
            Write-Log "Kunne ikke fjerne tjeneste $($svc.Name): $_" "ERROR"
        }
    }
}

function Remove-BridgeFirewallRules {
    param([array]$Rules)
    foreach ($rule in $Rules) {
        try {
            Remove-NetFirewallRule -Name $rule.Name -ErrorAction Stop
            Write-Log "Fjernet brannmurregel: $($rule.Name)"
        } catch {
            Write-Log "Kunne ikke fjerne brannmurregel $($rule.Name): $_" "ERROR"
        }
    }
}

function Remove-BridgeFiles {
    param([array]$Installations)
    $pathsToRemove = @()
    foreach ($inst in $Installations) {
        if ($inst.Path -and (Test-Path $inst.Path)) {
            $pathsToRemove += $inst.Path
        }
    }
    $pathsToRemove = $pathsToRemove | Select-Object -Unique
    foreach ($path in $pathsToRemove) {
        try {
            Remove-Item -Path $path -Recurse -Force -ErrorAction Stop
            Write-Log "Fjernet mappe: $path"
        } catch {
            Write-Log "Kunne ikke fjerne mappe $path : $_" "ERROR"
        }
    }
}

function Remove-BridgeShortcuts {
    param([array]$Installations)
    $shortcuts = $Installations | Where-Object { $_.Type -eq "Startup Shortcut" }
    foreach ($sc in $shortcuts) {
        try {
            Remove-Item -Path $sc.Path -Force -ErrorAction Stop
            Write-Log "Fjernet snarvei: $($sc.Path)"
        } catch {
            Write-Log "Kunne ikke fjerne snarvei $($sc.Path): $_" "ERROR"
        }
    }
}

function Remove-RegistryEntries {
    # Fjern avinstallasjonsoppføringer
    $uninstallKeys = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"
    )
    foreach ($key in $uninstallKeys) {
        if (Test-Path $key) {
            $items = Get-ChildItem $key -ErrorAction SilentlyContinue | Where-Object {
                $props = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue
                $props.DisplayName -match 'Nimay.*Bridge|Nimay.*OMP.*Bridge|OMPBridge'
            }
            foreach ($item in $items) {
                try {
                    Remove-Item $item.PSPath -Recurse -Force -ErrorAction Stop
                    Write-Log "Fjernet registeroppføring: $($item.Name)"
                } catch {
                    Write-Log "Kunne ikke fjerne registeroppføring $($item.Name): $_" "ERROR"
                }
            }
        }
    }
}

# --- GUI ---

$form = New-Object System.Windows.Forms.Form
$form.Text = "Nimay OMP Bridge Cleanup"
$form.Size = New-Object System.Drawing.Size(700, 600)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false

$labelTitle = New-Object System.Windows.Forms.Label
$labelTitle.Text = "Nimay OMP Bridge - Oppryddingsverktøy"
$labelTitle.Font = New-Object System.Drawing.Font("Segoe UI", 14, [System.Drawing.FontStyle]::Bold)
$labelTitle.Location = New-Object System.Drawing.Point(20, 20)
$labelTitle.Size = New-Object System.Drawing.Size(660, 30)
$form.Controls.Add($labelTitle)

$labelDesc = New-Object System.Windows.Forms.Label
$labelDesc.Text = "Dette verktøyet finner og fjerner gamle Nimay OMP Bridge-installasjoner.`nDet vil IKKE slette OMP, samtaler, Mnemopi eller modeller."
$labelDesc.Location = New-Object System.Drawing.Point(20, 55)
$labelDesc.Size = New-Object System.Drawing.Size(660, 40)
$form.Controls.Add($labelDesc)

$btnScan = New-Object System.Windows.Forms.Button
$btnScan.Text = "Skann systemet"
$btnScan.Location = New-Object System.Drawing.Point(20, 100)
$btnScan.Size = New-Object System.Drawing.Size(150, 35)
$form.Controls.Add($btnScan)

$listBox = New-Object System.Windows.Forms.ListBox
$listBox.Location = New-Object System.Drawing.Point(20, 145)
$listBox.Size = New-Object System.Drawing.Size(660, 300)
$listBox.Font = New-Object System.Drawing.Font("Consolas", 9)
$form.Controls.Add($listBox)

$labelStatus = New-Object System.Windows.Forms.Label
$labelStatus.Text = "Klar. Trykk 'Skann systemet' for å starte."
$labelStatus.Location = New-Object System.Drawing.Point(20, 455)
$labelStatus.Size = New-Object System.Drawing.Size(660, 20)
$form.Controls.Add($labelStatus)

$btnClean = New-Object System.Windows.Forms.Button
$btnClean.Text = "Rydd opp (bekreft)"
$btnClean.Location = New-Object System.Drawing.Point(20, 485)
$btnClean.Size = New-Object System.Drawing.Size(150, 35)
$btnClean.Enabled = $false
$form.Controls.Add($btnClean)

$btnClose = New-Object System.Windows.Forms.Button
$btnClose.Text = "Lukk"
$btnClose.Location = New-Object System.Drawing.Point(530, 485)
$btnClose.Size = New-Object System.Drawing.Size(150, 35)
$btnClose.Add_Click({ $form.Close() })
$form.Controls.Add($btnClose)

$Script:ScanResults = $null

$btnScan.Add_Click({
    $listBox.Items.Clear()
    $labelStatus.Text = "Skanner..."
    $form.Refresh()
    Write-Log "Starter skanning..."

    $processes = Find-BridgeProcesses
    $installations = Find-BridgeInstallations
    $tasks = Find-BridgeTasks
    $services = Find-BridgeServices
    $firewallRules = Find-BridgeFirewallRules

    $Script:ScanResults = @{
        Processes = $processes
        Installations = $installations
        Tasks = $tasks
        Services = $services
        FirewallRules = $firewallRules
    }

    $total = $processes.Count + $installations.Count + $tasks.Count + $services.Count + $firewallRules.Count
    $labelStatus.Text = "Fant $total elementer."

    if ($processes.Count -gt 0) {
        $listBox.Items.Add("=== BRIDGE-PROSESSER ($($processes.Count)) ===")
        foreach ($p in $processes) {
            $listBox.Items.Add("  PID $($p.PID): $($p.CommandLine)")
        }
    }
    if ($installations.Count -gt 0) {
        $listBox.Items.Add("=== INSTALLASJONER ($($installations.Count)) ===")
        foreach ($i in $installations) {
            $listBox.Items.Add("  [$($i.Type)] $($i.Path)")
        }
    }
    if ($tasks.Count -gt 0) {
        $listBox.Items.Add("=== TASK SCHEDULER ($($tasks.Count)) ===")
        foreach ($t in $tasks) {
            $listBox.Items.Add("  $($t.Name) - $($t.State) - $($t.Execute)")
        }
    }
    if ($services.Count -gt 0) {
        $listBox.Items.Add("=== TJENESTER ($($services.Count)) ===")
        foreach ($s in $services) {
            $listBox.Items.Add("  $($s.Name) - $($s.Status)")
        }
    }
    if ($firewallRules.Count -gt 0) {
        $listBox.Items.Add("=== BRANNMUR ($($firewallRules.Count)) ===")
        foreach ($r in $firewallRules) {
            $listBox.Items.Add("  $($r.Name) - $($r.Enabled)")
        }
    }
    if ($total -eq 0) {
        $listBox.Items.Add("Ingen Bridge-rester funnet. Systemet er rent.")
    }

    $btnClean.Enabled = ($total -gt 0)
    Write-Log "Skanning fullført. Fant $total elementer."
})

$btnClean.Add_Click({
    $confirm = [System.Windows.Forms.MessageBox]::Show(
        "Er du sikker på at du vil fjerne alle funne Bridge-komponenter?`n`nDette kan ikke angres.",
        "Bekreft opprydding",
        [System.Windows.Forms.MessageBoxButtons]::YesNo,
        [System.Windows.Forms.MessageBoxIcon]::Warning
    )
    if ($confirm -ne [System.Windows.Forms.DialogResult]::Yes) { return }

    $labelStatus.Text = "Rydder opp..."
    $form.Refresh()
    Write-Log "Starter opprydding..."

    $results = $Script:ScanResults

    # 1. Stopp prosesser
    if ($results.Processes.Count -gt 0) {
        $listBox.Items.Add("--- Stopper prosesser ---")
        Stop-BridgeProcesses $results.Processes
    }
    # 2. Fjern Task Scheduler-oppgaver
    if ($results.Tasks.Count -gt 0) {
        $listBox.Items.Add("--- Fjerner Task Scheduler-oppgaver ---")
        Remove-BridgeTasks $results.Tasks
    }
    # 3. Fjern tjenester
    if ($results.Services.Count -gt 0) {
        $listBox.Items.Add("--- Fjerner tjenester ---")
        Remove-BridgeServices $results.Services
    }
    # 4. Fjern brannmurregler
    if ($results.FirewallRules.Count -gt 0) {
        $listBox.Items.Add("--- Fjerner brannmurregler ---")
        Remove-BridgeFirewallRules $results.FirewallRules
    }
    # 5. Fjern filer og mapper
    if ($results.Installations.Count -gt 0) {
        $listBox.Items.Add("--- Fjerner filer ---")
        Remove-BridgeShortcuts $results.Installations
        Remove-BridgeFiles $results.Installations
    }
    # 6. Fjern registeroppføringer
    $listBox.Items.Add("--- Fjerner registeroppføringer ---")
    Remove-RegistryEntries

    $labelStatus.Text = "Opprydding fullført. Se logg: $Script:LogFile"
    $btnClean.Enabled = $false
    Write-Log "Opprydding fullført."

    # Verifiser
    $listBox.Items.Add("--- Verifiserer ---")
    $remaining = Find-BridgeProcesses
    if ($remaining.Count -eq 0) {
        $listBox.Items.Add("Ingen Bridge-prosesser funnet.")
    } else {
        $listBox.Items.Add("ADVARSEL: $($remaining.Count) Bridge-prosesser fortsatt aktive.")
    }
    $remainingTasks = Find-BridgeTasks
    if ($remainingTasks.Count -eq 0) {
        $listBox.Items.Add("Ingen Bridge Task Scheduler-oppgaver funnet.")
    } else {
        $listBox.Items.Add("ADVARSEL: $($remainingTasks.Count) oppgaver fortsatt registrert.")
    }
    $listBox.Items.Add("Logg lagret til: $Script:LogFile")
})

[void]$form.ShowDialog()