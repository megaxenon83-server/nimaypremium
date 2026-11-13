#define Payload "build-new\payload"
; Clean, standalone installer from scratch
; Uses registry Run key (no admin, no password) + optional SYSTEM service

#define MyAppName "Nimay OMP Bridge"
#define MyAppVersion "3.3.0"
#define MyAppPublisher "Nimay"

[Setup]
AppId={{E5F6A7B8-C9D0-4E1F-2A3B-4C5D6E7F8A9B}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Nimay\OMPBridge
DisableProgramGroupPage=yes
OutputDir=dist
OutputBaseFilename=Nimay-OMP-Bridge-Setup
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
Source: "{#Payload}\commands\*"; DestDir: "{%USERPROFILE}\.omp\agent\commands"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{userstartup}\Nimay OMP Bridge"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-hidden.ps1"""; WorkingDir: "{app}"; Comment: "Starter Nimay OMP Bridge"

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\tools\start-bridge-hidden.ps1"""; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent; Description: "Start Bridge now"

[Code]
#define Payload "build-new\payload"

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

    // Create config.json if it doesn't exist
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

    // Find OMP binary
    OmpBin := '';
    SearchPaths := [
      ExpandConstant('%USERPROFILE%') + '\.bun\bin\omp.exe',
      ExpandConstant('%APPDATA%') + '\npm\omp.cmd',
      ExpandConstant('%LOCALAPPDATA%') + '\Programs\omp\omp.exe',
      'C:\Program Files\omp\omp.exe'
    ];
    for I := 0 to GetArrayLength(SearchPaths) - 1 do
    begin
      if FileExists(SearchPaths[I]) then
      begin
        OmpBin := SearchPaths[I];
        break;
      end;
    end;
    // If found Bun shim, prefer Bun + cli.js
    if OmpBin = ExpandConstant('%USERPROFILE%') + '\.bun\bin\omp.exe' then
    begin
      if FileExists(ExpandConstant('%USERPROFILE%') + '\.bun\install\global\node_modules\@oh-my-pi\pi-coding-agent\dist\cli.js') then
      begin
        OmpBin := 'C:\Users\' + ExpandConstant('{username}') + '\AppData\Local\Microsoft\WinGet\Packages\Oven-sh.Bun_Microsoft.Winget.Source_8wekyb3d8bbwe\bun-windows-x64\bun.exe' + '|' + ExpandConstant('%USERPROFILE%') + '\.bun\install\global\node_modules\@oh-my-pi\pi-coding-agent\dist\cli.js';
      end;
    end;

    // Set OMP_BIN environment variable (user-level)
    if OmpBin <> '' then
    begin
      RegWriteStringValue(HKCU, 'Environment', 'OMP_BIN', OmpBin);
    end;

    // Write ompchat-commands.json to point to installed location
    SaveStringToFile(
      ExpandConstant('%USERPROFILE%') + '\.omp\agent\ompchat-commands.json',
      '{' + #13#10 +
      '  "projectRoot": "' + ExpandConstant('{app}') + '",' + #13#10 +
      '  "bridge": "' + ExpandConstant('{app}') + '\\bridge\\omp-bridge.mjs"' + #13#10 +
      '}',
      False
    );

    // Register autostart via registry Run key (no admin, no password)
    RegWriteStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'Nimay OMP Bridge', 'powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + ExpandConstant('{app}\tools\start-bridge-hidden.ps1') + '"');

    // Copy OMP commands to ~/.omp/agent/commands/
    Exec('cmd.exe', '/c xcopy /E /I /Y "' + ExpandConstant('{app}\commands') + '" "' + ExpandConstant('%USERPROFILE%') + '\.omp\agent\commands"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  end;
end;