; ---------------------------------------------------------------------------
; Nimay OMP Bridge - clean per-user installer
; No voice/STT/TTS installers, admin, SYSTEM service or Program Files.
; ---------------------------------------------------------------------------

#define MyAppName "Nimay OMP Bridge"
#define MyAppVersion "3.3.6"
#define MyAppPublisher "Nimay"
#define Payload "build-fresh\payload"

[Setup]
AppId={{B1C2D3E4-F5A6-47B8-9C0D-1E2F3A4B5C6D}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
VersionInfoVersion={#MyAppVersion}
VersionInfoProductVersion={#MyAppVersion}
VersionInfoDescription={#MyAppName} Setup
VersionInfoProductName={#MyAppName}
DefaultDirName={localappdata}\Nimay\OMPBridge
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=Nimay-OMP-Bridge-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
DisableWelcomePage=no
CloseApplications=no
MinVersion=10.0

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "norwegian"; MessagesFile: "compiler:Languages\Norwegian.isl"

[Files]
Source: "{#Payload}\bridge\omp-bridge.mjs"; DestDir: "{app}\bridge"; Flags: ignoreversion
Source: "{#Payload}\tools\start-bridge-hidden.ps1"; DestDir: "{app}\tools"; Flags: ignoreversion
Source: "{#Payload}\tools\stop-bridge.ps1"; DestDir: "{app}\tools"; Flags: ignoreversion
Source: "{#Payload}\tools\omp_qr.py"; DestDir: "{app}\tools"; Flags: ignoreversion
Source: "{#Payload}\tools\qrgen.mjs"; DestDir: "{app}\tools"; Flags: ignoreversion
Source: "{#Payload}\commands\*"; DestDir: "{%USERPROFILE}\.omp\agent\commands"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; The only autostart mechanism: one shortcut in the current user's Startup folder.
; Created fresh on every install/upgrade so stale shortcuts from earlier builds
; (including .disabled copies or shortcuts pointing at temp/test paths) are replaced.
Name: "{userstartup}\Nimay OMP Bridge"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-hidden.ps1"""; WorkingDir: "{app}"; Comment: "Starter Nimay OMP Bridge"

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\stop-bridge.ps1"" -InstallDir ""{app}"""; Flags: runhidden; RunOnceId: "StopBridge"

[Code]
var
  DetectedOmpBin: String;

function ReportsVersion(ExePath: String; Arguments: String): Boolean;
var
  ResultCode: Integer;
begin
  Result := False;
  if not FileExists(ExePath) then Exit;
  if not Exec(ExePath, Arguments, GetEnv('USERPROFILE'), SW_HIDE, ewWaitUntilTerminated, ResultCode) then Exit;
  Result := ResultCode = 0;
end;

function OnPath(ExeName: String): String;
var
  TempFile: String;
  Lines: TArrayOfString;
  ResultCode: Integer;
  I: Integer;
begin
  Result := '';
  TempFile := ExpandConstant('{tmp}\nimay-where-' + ExeName + '.txt');
  Exec(ExpandConstant('{sys}\cmd.exe'), '/d /c where "' + ExeName + '" > "' + TempFile + '" 2>nul', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  if not LoadStringsFromFile(TempFile, Lines) then Exit;
  for I := 0 to GetArrayLength(Lines) - 1 do
    if FileExists(Trim(Lines[I])) then
    begin
      Result := Trim(Lines[I]);
      Exit;
    end;
end;

function ValidBunCli(BunExe: String; CliJs: String): Boolean;
begin
  Result := FileExists(CliJs) and ReportsVersion(BunExe, '"' + CliJs + '" --version');
end;

function FindOmpBinary: String;
var
  Configured: String;
  ExePath: String;
  EntryPath: String;
  CliJs: String;
  BunCandidates: array of String;
  Separator: Integer;
  I: Integer;
begin
  Result := '';

  // Respect a verified custom executable/entrypoint, but never trust the Bun
  // shim merely because it exists. The direct Bun + cli.js form is preferred.
  Configured := Trim(GetEnv('OMP_BIN'));
  Separator := Pos('|', Configured);
  if (Configured <> '') and (Pos('.bun\bin\omp.exe', Lowercase(Configured)) = 0) then
  begin
    if Separator > 0 then
    begin
      ExePath := Copy(Configured, 1, Separator - 1);
      EntryPath := Copy(Configured, Separator + 1, MaxInt);
      if ValidBunCli(ExePath, EntryPath) then
      begin
        Result := Configured;
        Exit;
      end;
    end
    else if ReportsVersion(Configured, '--version') then
    begin
      Result := Configured;
      Exit;
    end;
  end;

  CliJs := GetEnv('USERPROFILE') + '\.bun\install\global\node_modules\@oh-my-pi\pi-coding-agent\dist\cli.js';
  SetArrayLength(BunCandidates, 4);
  BunCandidates[0] := GetEnv('LOCALAPPDATA') + '\Microsoft\WinGet\Packages\Oven-sh.Bun_Microsoft.Winget.Source_8wekyb3d8bbwe\bun-windows-x64\bun.exe';
  BunCandidates[1] := GetEnv('USERPROFILE') + '\.bun\bin\bun.exe';
  if GetEnv('BUN_INSTALL') <> '' then
    BunCandidates[2] := GetEnv('BUN_INSTALL') + '\bin\bun.exe';
  BunCandidates[3] := OnPath('bun.exe');
  for I := 0 to GetArrayLength(BunCandidates) - 1 do
    if (BunCandidates[I] <> '') and ValidBunCli(BunCandidates[I], CliJs) then
    begin
      Result := BunCandidates[I] + '|' + CliJs;
      Exit;
    end;

  // Last resort: a real executable which itself answers --version.
  ExePath := OnPath('omp.exe');
  if (ExePath <> '') and (Pos('.bun\bin\omp.exe', Lowercase(ExePath)) = 0) and ReportsVersion(ExePath, '--version') then
    Result := ExePath;
end;

procedure RemoveOldStartupShortcuts;
var
  StartupFolder: String;
  FindRec: TFindRec;
begin
  StartupFolder := ExpandConstant('{userstartup}');
  if FindFirst(StartupFolder + '\Nimay OMP Bridge*', FindRec) then
  begin
    repeat
      DeleteFile(StartupFolder + '\' + FindRec.Name);
    until not FindNext(FindRec);
    FindClose(FindRec);
  end;
end;

function InitializeSetup: Boolean;
var
  ResultCode: Integer;
begin
  Result := False;
  if not Exec('node.exe', '--version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
  begin
    MsgBox('Node.js 20 eller nyere ma vaere installert og tilgjengelig i PATH.', mbError, MB_OK);
    Exit;
  end;
  DetectedOmpBin := FindOmpBinary;
  if DetectedOmpBin = '' then
  begin
    MsgBox('Fant ingen fungerende OMP-installasjon. Installer OMP eller sett OMP_BIN til Bun og dist\cli.js, og start installasjonen paa nytt.', mbError, MB_OK);
    Exit;
  end;
  Result := True;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  BridgePath: String;
  AppPathJson: String;
  ResultCode: Integer;
  StartupFolder: String;
begin
  if CurStep = ssInstall then
  begin
    // Fjern foreldede/ødelagte oppstartsgenveier fra tidligere bygg.
    // [Icons] oppretter deretter den korrekte genveien på nytt.
    RemoveOldStartupShortcuts;

    // Fjern den gamle per-user Run-posten fra tidligere tester/installer-versjoner.
    RegDeleteValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'Nimay OMP Bridge');
  end;

  if CurStep <> ssPostInstall then Exit;

  BridgePath := ExpandConstant('{app}\bridge');

  // Skriv den oppdagede OMP_BIN-konfigurasjonen til en fil ved siden av bridge.
  // start-bridge-hidden.ps1 leser denne som fallback hvis OMP_BIN-miljøvariabelen
  // ikke er tilgjengelig i oppstartsprosessen etter omstart.
  SaveStringToFile(BridgePath + '\omp-bin.txt', DetectedOmpBin, False);

  // Brukerens bro-eier config.json. Ikke skriv det her – bevar den fullstendige
  // eksisterende konfigurasjonen og la broen generere en kryptografisk token
  // ved clean install. Den lagres før /ompqr kan brukes.

  // Forward slashes holder denne JSON-en gyldig uten maskinspesifikk escaping.
  AppPathJson := ExpandConstant('{app}');
  StringChangeEx(AppPathJson, '\', '/', False);
  SaveStringToFile(
    GetEnv('USERPROFILE') + '\.omp\agent\ompchat-commands.json',
    '{' + #13#10 +
    '  "projectRoot": "' + AppPathJson + '",' + #13#10 +
    '  "bridge": "' + AppPathJson + '/bridge/omp-bridge.mjs"' + #13#10 +
    '}', False
  );

  // Start én gang i den nåværende (ikke-elevate) brukerkonteksten for både
  // interaktive og stille installasjoner. Starteren venter på autentisert /api/health.
  if (not Exec('powershell.exe',
    '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + ExpandConstant('{app}\tools\start-bridge-hidden.ps1') + '"',
    ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, ResultCode)) or (ResultCode <> 0) then
    RaiseException('Nimay OMP Bridge kunne ikke startes. Kontroller Node.js, OMP_BIN og bridge-loggen.');
end;
