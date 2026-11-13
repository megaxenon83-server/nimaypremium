; Nimay OMP Mobile 2.4 - Windows Installer Script
; Generated automatically - do not edit manually

[Setup]
AppName=Nimay OMP Mobile
AppVersion=2.9.1
AppPublisher=Nimay OMP
AppVersionDate=2026-10-28
UsePreviousAppDir=false
DirectoryMode=normal
DisableProgramGroupPage=yes
LicenseFile=
WizardImageFile=embedded\WizardImage.bmp
WizardSmallImageFile=embedded\WizardSmall.bmp
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\OmpChat-2.3-release.apk
OutputDir=..
OutputBaseFilename=NimayOMP-Setup
SetupIconFile=embedded\OmpChat.ico
Compression=lzma
SolidCompression=true
ChangesAssociations=no
ChangesEnvironment=false

[Languages]
Name: "norsk"; MessagesFile: "compiler:Languages\Norwegian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startmenu"; Description: "Lag Start-meny-snarvei"; GroupDescription="{cm:AdditionalIcons}"; Flags: checked
Name: "autostart"; Description: "Start OMP-agent automatisk ved oppstart"; GroupDescription="{cm:AdditionalIcons}"; Flags: unchecked

[Dirs]
Name: "{app}\#MyAppDir"; Permissions: users-modify
Name: "{app}\apk"; Permissions: users-read
Name: "{app}\bridge"; Permissions: users-modify
Name: "{app}\commands"; Permissions: users-modify

[Files]
; Hoved-APK (inkludert i installeren)
Name: "{app}\apk\OmpChat-2.3-release.apk"; Source: "..\OmpChat-2.3-release.apk"; Flags: ignoreversion

; Bridge-kode
Name: "{app}\bridge\omp-bridge.mjs"; Source: "bridge\omp-bridge.mjs"; Flags: ignoreversion
; node-pty (ConPTY) for the terminal
Name: "{app}\bridge\node_modules\node-pty\*"; Source: "bridge\node_modules\node-pty\*"; Flags: ignoreversion recursesubdirs createallsubdirs
Name: "{app}\bridge\config.json"; Source: "bridge\config.json"; Flags: ignoreversion;noreplace

; OMP-kommandoer (globale)
Name: "{app}\commands\ompqr"; Source: "omp-commands\ompqr"; Flags: recursesubdirs
Name: "{app}\commands\ompserver"; Source: "omp-commands\ompserver"; Flags: recursesubdirs
Name: "{app}\commands\ompchat-shared"; Source: "omp-commands\ompchat-shared"; Flags: recursesubdirs

; Brukerdokumentasjon
Name: "{app}\doc\README.md"; Source: "README.md"; Flags: ignoreversion
Name: "{app}\doc\CHANGELOG.md"; Source: "CHANGELOG.md"; Flags: ignoreversion
Name: "{app}\doc\DECISIONS.md"; Source: "DECISIONS.md"; Flags: ignoreversion
Name: "{app}\doc\PROJECT_STATUS.md"; Source: "PROJECT_STATUS.md"; Flags: ignoreversion

[Icons]
Name: "{group}\Nimay OMP Mobile"; Filename: "{app}\OmpChat-2.3-release.apk"; Comment: "Android-chatklient for OMP-agent"
Name: "{group}\OmpChat Bridge"; Filename: "{app}\bridge\omp-bridge.mjs"; Comment: "Start OMP-bridge (Node.js)"
Name: "{group}\Prosjektstatus"; Filename: "{app}\doc\PROJECT_STATUS.md"; Comment: "Prosjektstatus og dokumentasjon"
Name: "{group}\Odinstaller"; Filename: "{uninstallexe}"; Comment: "Odinstaller Nimay OMP Mobile"
Name: "{autodesktop}\Nimay OMP Mobile"; Filename: "{app}\OmpChat-2.3-release.apk"; Tasks: desktopicon

[Run]
Filename: "{app}\bridge\omp-bridge.mjs"; Description: "Start OMP-bridge nå"; Flags: nowait postinstall skipifsilent; WorkingDir="{app}\bridge"
Filename: "{app}\OmpChat-2.3-release.apk"; Description: "Åpne APK-filen"; Flags: nowait postinstall skipifsilent

[Code]
function InitializeSetup(): Boolean;
begin
  Result := True;
end;

function CurStepChanged(CurStep: TSetupStep): Boolean;
begin
  Result := True;
  if CurStep = ssInstall then
  begin
    // Opprett nødvendige mapper
    if not DirExists(ExpandConstant('{app}\apk')) then
      CreateDir(ExpandConstant('{app}\apk'));
    if not DirExists(ExpandConstant('{app}\bridge')) then
      CreateDir(ExpandConstant('{app}\bridge'));
    if not DirExists(ExpandConstant('{app}\commands')) then
      CreateDir(ExpandConstant('{app}\commands'));
    if not DirExists(ExpandConstant('{app}\doc')) then
      CreateDir(ExpandConstant('{app}\doc'));
  end;
end;
