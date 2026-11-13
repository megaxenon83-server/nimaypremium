#define Payload "build-service\payload"
; Starter Bridge som Windows-tjeneste (SYSTEM), ingen passord, ingen login nødvendig

#define MyAppName "Nimay OMP Bridge (Service)"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Nimay"

[Setup]
AppId={{D4E8F9A1-2B3C-4D5E-6F7A-8B9C0D1E2F3A}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName=C:\Nimay\OMPBridge
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=NimayOMP-Bridge-Service-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
DisableWelcomePage=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "norwegian"; MessagesFile: "compiler:Languages\Norwegian.isl"

[Files]
Source: "{#Payload}\bridge\*"; DestDir: "{app}\bridge"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Payload}\tools\start-bridge-service.ps1"; DestDir: "{app}\tools"; Flags: ignoreversion

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-service.ps1"" -Install"; WorkingDir: "{app}"; Flags: runhidden waituntilterminated; Description: "Installer Bridge-tjeneste"

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-service.ps1"" -Uninstall"; WorkingDir: "{app}"; Flags: runhidden waituntilterminated

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  BridgePath: String;
  ConfigPath: String;
  Token: String;
  OmpBin: String;
  SearchPaths: array of String;
  I: Integer;
  ResultCode: Integer;
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
    OmpBin := '';
    SearchPaths := ['%USERPROFILE%\.bun\bin\omp.exe', '%APPDATA%\npm\omp.cmd', '%LOCALAPPDATA%\Programs\omp\omp.exe', 'C:\Program Files\omp\omp.exe'];
    for I := 0 to GetArrayLength(SearchPaths) - 1 do
    begin
      if FileExists(ExpandConstant(SearchPaths[I])) then
      begin
        OmpBin := ExpandConstant(SearchPaths[I]);
        break;
      end;
    end;

    // Sett system-miljøvariabel for OMP_BIN
    if OmpBin <> '' then
    begin
      RegWriteStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'OMP_BIN', OmpBin);
    end;
  end;
end;