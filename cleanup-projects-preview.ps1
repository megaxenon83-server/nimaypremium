# Preview which projects would be removed from registry
# Does NOT modify anything

$projectsFile = "$env:USERPROFILE\.omp\agent\omp-projects.json"
$backup = "$env:USERPROFILE\.omp\agent\omp-projects.json.bak-preview"

# Backup
Copy-Item $projectsFile $backup -Force
Write-Host "Backup created: $backup"

# Load projects
$projects = Get-Content $projectsFile -Raw | ConvertFrom-Json

# Test project IDs (17 confirmed)
$testIds = @(
    "proj-1794009558014", "proj-1794009652456", "proj-1794011903485",
    "proj-1794013262501", "proj-1794013851561", "proj-1794013888452",
    "proj-1794015030756", "proj-1794042052015", "proj-1794042860628",
    "proj-1794053209483", "proj-1794053209486", "proj-1794053269890",
    "proj-1794053635597", "proj-1794053650528", "proj-1794053673788",
    "proj-1794053680250", "proj-1794054387559"
)

Write-Host "`n=== Projects to REMOVE (17 test projects) ==="
foreach ($p in $projects) {
    if ($testIds -contains $p.id) {
        Write-Host "  REMOVE: $($p.id) | $($p.name) | $($p.path)"
    }
}

Write-Host "`n=== Projects to KEEP ==="
foreach ($p in $projects) {
    if ($testIds -notcontains $p.id) {
        Write-Host "  KEEP: $($p.id) | $($p.name) | $($p.path)"
    }
}

Write-Host "`n=== Summary ==="
Write-Host "Total projects: $($projects.Count)"
Write-Host "To remove: $($testIds.Count)"
Write-Host "To keep: $($projects.Count - $testIds.Count)"
Write-Host "`nNo changes made. Review and approve before proceeding."
