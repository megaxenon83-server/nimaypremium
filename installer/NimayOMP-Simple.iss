#define Payload "build-simple\payload"
; Installerer kun Bridge med autostart (ingen passord kreves)

#define MyAppName "Nimay OMP Bridge (Simple)"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Nimay"

[Setup]
AppId={{B3F7A8C2-1D4E-4F6A-9B8C-7D5E3F2A1B0C}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Nimay\OMPBridge
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=NimayOMP-Bridge-Simple-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
DisableWelcomePage=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "norwegian"; MessagesFile: "compiler:Languages\Norwegian.isl"

[Files]
Source: "{#Payload}\bridge\*"; DestDir: "{app}\bridge"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Payload}\tools\start-bridge-hidden.ps1"; DestDir: "{app}\tools"; Flags: ignoreversion

[Icons]
Name: "{userstartup}\Nimay OMP Bridge"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-hidden.ps1"""; WorkingDir: "{app}"; Comment: "Starter Nimay OMP Bridge"

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-hidden.ps1"""; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent; Description: "Start Bridge now"

[Code]
function FindOmpBinary(): String;
var
  OmpPath: String;
  SearchPaths: array of String;
  I: Integer;
  ResultCode: Integer;
begin
  Result := '';
  // Søk i vanlige stier
  SearchPaths := ['%USERPROFILE%\.bun\bin\omp.exe', '%APPDATA%\npm\omp.cmd', '%LOCALAPPDATA%\Programs\omp\omp.exe', 'C:\Program Files\omp\omp.exe', 'C:\Program Files (x86)\omp\omp.exe'];
  for I := 0 to GetArrayLength(SearchPaths) - 1 do
  begin
    OmpPath := ExpandConstant(SearchPaths[I]);
    if FileExists(OmpPath) then
    begin
      Result := OmpPath;
      break;
    end;
  end;
  // Søk i PATH
  if Result = '' then
  begin
    if Exec('cmd.exe', '/c where omp', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    begin
      Result := 'omp';
    end;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  BridgePath: String;
  ConfigPath: String;
  Token: String;
  OmpBin: String;
  ResultCode: Integer;
  ExecResult: Boolean;
begin
  if CurStep = ssPostInstall then
  begin
    BridgePath := ExpandConstant('{app}\bridge');
    ConfigPath := BridgePath + '\config.json';

    // Opprett config.json hvis den ikke finnes
    if not FileExists(ConfigPath) then
    begin
      Token := 'nimay-' + IntToStr(Random(999999)) + '-' + IntToStr(Random(99999));
      SaveStringToFile(ConfigPath,
        '{' + #13#10 +
        '  "port": 7777,' + #13#10 +
        '  "host": "0.0.0.0",' + #13#10 +
        '  "token": "' + Token + '",' + #13#10 +
        '  "approvalMode": "yolo",' + #13#10 +
        '  "allowBash": true,' + #13#10 +
        '  "projectRoots": [],' + #13#10 +
        '  "maxChildren": 4,' + #13#10 +
        '  "idleMinutes": 90' + #13#10 +
        '}', False);
    end;

    // Finn OMP binary
    OmpBin := FindOmpBinary();

    // Sett OMP_BIN miljøvariabel hvis OMP ble funnet
    if OmpBin <> '' then
    begin
      RegWriteStringValue(HKCU, 'Environment', 'OMP_BIN', OmpBin);
    end;

    // Opprett Task Scheduler-oppgave (uten passord, kun når bruker er logget inn)
    ExecResult := Exec('schtasks.exe',
      '/Create /TN "Nimay OMP Bridge Simple" /TR "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""' + ExpandConstant('{app}\tools\start-bridge-hidden.ps1') + '""" /SC ONLOGON /RL LIMITED /F',
      '', SW_HIDE, ewWaitUntilTerminated, ResultCode);

    // Opprett også en snarvei i Startup-mappen som backup
    // (dette gjøres allerede via [Icons]-seksjonen)
  end;
end;