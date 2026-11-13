; ---------------------------------------------------------------------------
; Nimay OMP - Windows installer (Inno Setup 6)
;
; Build with:
;   ISCC.exe /Qp NimayOMP.iss
; or use installer\build-installer.ps1 which stages the payload first.
; ---------------------------------------------------------------------------

#define AppName "Nimay OMP"
#define AppVersion "3.2.8"
#define AppPublisher "Nimay"
#define AppURL "https://nimay.net"
#define Payload "build\payload"

[Setup]
AppId={{6F1B31A0-6C42-4C19-9F52-1A7C9C0E52D1}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
DefaultDirName={localappdata}\Nimay\OMPBridge
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
AllowNoIcons=yes
OutputDir=dist
OutputBaseFilename=NimayOMP-Setup-{#AppVersion}
SetupIconFile=assets\nimay.ico
UninstallDisplayIcon={app}\assets\nimay.ico
UninstallDisplayName={#AppName} {#AppVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern dynamic
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UsePreviousAppDir=yes
UsePreviousTasks=yes
UsePreviousSetupType=yes
SetupLogging=yes
CloseApplications=no
MinVersion=10.0
VersionInfoVersion=2.6.8.0
VersionInfoCompany={#AppPublisher}
VersionInfoDescription=Nimay OMP installer
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}

[Languages]
Name: "norwegian"; MessagesFile: "compiler:Languages\Norwegian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Types]
Name: "full"; Description: "Full installasjon - alle norske talemotorer og verktoy"
Name: "compact"; Description: "Kompakt - rask talegjenkjenning og norsk talesyntese"
Name: "custom"; Description: "Tilpasset"; Flags: iscustom

[Components]
Name: "core"; Description: "OMP Bridge, web-APK-server og fellesfiler (pakrevd)"; Types: full compact custom; Flags: fixed
Name: "stt"; Description: "Talegjenkjenning (STT) - lokalt pa denne PC-en"; Types: full compact custom
Name: "stt\whisper_tiny"; Description: "Whisper Tiny - rask, flerspraklig (nedlasting ca. 75 MB, CPU)"; Types: full compact
Name: "stt\nb_whisper_small"; Description: "NB-Whisper Small - norsk, god balanse (nedlasting ca. 480 MB, CPU)"; Types: full
Name: "stt\nb_whisper_medium"; Description: "NB-Whisper Medium - best presisjon (nedlasting ca. 1,6 GB, GPU anbefalt)"; Types: full
Name: "tts"; Description: "Talesyntese (TTS) - norsk tale pa denne PC-en"; Types: full compact custom
Name: "tts\piper_talesyntese"; Description: "Norsk talesyntese (Piper) - 63 MB, rask pa CPU"; Types: full compact
Name: "tts\piper_nvcc"; Description: "Norsk NVCC (Piper) - alternativ stemme, 77 MB, CPU"; Types: full
Name: "tts\chatterbox"; Description: "Chatterbox Multilingual - mer naturlig (ca. 1,5 GB, GPU anbefalt)"; Types: custom
Name: "tts\vibevoice"; Description: "VibeVoice - eksperimentell (3,5 GB+, krever NVIDIA GPU med 8 GB VRAM)"; Types: custom

[Tasks]
Name: "desktopicon"; Description: "Lag snarvei pa skrivebordet"; GroupDescription: "Snarveier:"
Name: "autostart"; Description: "Start OMP Bridge automatisk nar du logger inn"; GroupDescription: "Oppstart:"; Flags: checkedonce
Name: "startbridge"; Description: "Start OMP Bridge etter installasjonen"; GroupDescription: "Oppstart:"; Flags: checkedonce
Name: "registercommands"; Description: "Registrer /ompserver og /ompqr i OMP"; GroupDescription: "OMP:"; Flags: checkedonce

[Files]
; Android client
Source: "{#Payload}\apk\OmpChat.apk"; DestDir: "{app}\apk"; Flags: ignoreversion
; Bridge + commands
Source: "{#Payload}\bridge\*"; DestDir: "{app}\bridge"; Excludes: "config.json,sessions-meta.json,*.log"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Payload}\commands\*"; DestDir: "{app}\commands"; Flags: ignoreversion recursesubdirs createallsubdirs
; Voice scripts (runtime data such as venv/models/config is never overwritten)
Source: "{#Payload}\voice\voice_runtime.py"; DestDir: "{app}\voice"; Flags: ignoreversion
Source: "{#Payload}\voice\setup-voice.ps1"; DestDir: "{app}\voice"; Flags: ignoreversion
; Tools (QR generator, APK server, helper scripts)
Source: "{#Payload}\tools\*"; DestDir: "{app}\tools"; Flags: ignoreversion recursesubdirs createallsubdirs
; Documentation and shortcuts
Source: "{#Payload}\*.cmd"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#Payload}\README.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme
Source: "assets\nimay.ico"; DestDir: "{app}\assets"; Flags: ignoreversion

[Icons]
Name: "{group}\Start OMP Bridge"; Filename: "{app}\Start-OMP-Bridge.cmd"; WorkingDir: "{app}"; Comment: "Starter broen mellom OMP og telefonen"
Name: "{group}\Del APK med telefonen"; Filename: "{app}\Del-APK.cmd"; WorkingDir: "{app}"; Comment: "Lokal nedlasting av Android-appen"
Name: "{group}\Nimay OMP-mappen"; Filename: "{app}"
Name: "{group}\Avinstaller Nimay OMP"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Nimay OMP"; Filename: "{app}\Del-APK.cmd"; WorkingDir: "{app}"; Tasks: desktopicon
Name: "{userstartup}\Nimay OMP Bridge"; Filename: "{sys}\wscript.exe"; Parameters: """{app}\tools\start-bridge-hidden.vbs"""; WorkingDir: "{app}\tools"; Tasks: autostart

[Run]
; 1. Install selected voice engines (real downloads, real verification)
Filename: "{app}\tools\powershell-run.cmd"; Parameters: "-File ""{app}\voice\setup-voice.ps1"" -InstallDir ""{app}"" -PythonExe ""{code:GetPythonExe}"" -Stt ""{code:GetSttList}"" -Tts ""{code:GetTtsList}"" -LogFile ""{app}\voice\setup.log"""; StatusMsg: "Installerer valgte talemotorer (kan ta flere minutter)..."; Flags: runhidden waituntilterminated; Check: VoiceSelected
; 2. Register OMP slash commands
Filename: "{app}\tools\powershell-run.cmd"; Parameters: "-File ""{app}\tools\install-commands.ps1"" -AppDir ""{app}"""; StatusMsg: "Registrerer OMP-kommandoer..."; Flags: runhidden waituntilterminated
; 3. Firewall: allow the phone to reach the bridge and the APK server
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Nimay OMP Bridge"""; Flags: runhidden; StatusMsg: "Konfigurerer brannmur..."
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""Nimay OMP Bridge"" dir=in action=allow protocol=TCP localport=7777,7787 profile=private,domain"; Flags: runhidden
; 4. Start the bridge so the phone page works right away. explorer.exe is used
;    on purpose: the installer is elevated, and the bridge must NOT run as
;    administrator because the phone can drive OMP through it.
Filename: "{win}\explorer.exe"; Parameters: """{app}\tools\start-bridge-hidden.vbs"""; StatusMsg: "Starter OMP Bridge..."; Flags: runhidden nowait

[UninstallRun]
Filename: "{app}\tools\powershell-run.cmd"; Parameters: "-File ""{app}\tools\stop-bridge.ps1"" -InstallDir ""{app}"""; Flags: runhidden; RunOnceId: "StopNimayServices"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Nimay OMP Bridge"""; Flags: runhidden; RunOnceId: "RemoveFirewallRule"

[UninstallDelete]
Type: filesandordirs; Name: "{app}\voice"
Type: filesandordirs; Name: "{app}\bridge"
Type: filesandordirs; Name: "{app}\tools"
Type: filesandordirs; Name: "{app}\apk"
Type: filesandordirs; Name: "{app}\commands"
Type: filesandordirs; Name: "{app}\assets"

[Code]
var
  DownloadPage: TDownloadWizardPage;
  MobilePage: TWizardPage;
  QrApkImage: TBitmapImage;
  QrPairImage: TBitmapImage;
  ApkCaption: TNewStaticText;
  PairCaption: TNewStaticText;
  StatusCaption: TNewStaticText;
  VoiceCaption: TNewStaticText;
  MobilePrepared: Boolean;
  NodeMissing: Boolean;

{ ------------------------------------------------------------------------- }
{ Small helpers                                                             }
{ ------------------------------------------------------------------------- }

function FirstExisting(const Candidates: array of String): String;
var
  I: Integer;
begin
  Result := '';
  for I := Low(Candidates) to High(Candidates) do
    if FileExists(Candidates[I]) then
    begin
      Result := Candidates[I];
      Exit;
    end;
end;

function FindOnPath(const ExeName, Marker: String): String;
var
  TempFile: String;
  Lines: TArrayOfString;
  I: Integer;
begin
  Result := '';
  TempFile := ExpandConstant('{tmp}\where-' + Marker + '.txt');
  Exec(ExpandConstant('{sys}\cmd.exe'), '/c where ' + ExeName + ' > "' + TempFile + '" 2>nul',
    '', SW_HIDE, ewWaitUntilTerminated, I);
  if LoadStringsFromFile(TempFile, Lines) then
    for I := 0 to GetArrayLength(Lines) - 1 do
      if FileExists(Trim(Lines[I])) then
      begin
        Result := Trim(Lines[I]);
        Exit;
      end;
end;

function GetNodeExe: String;
begin
  Result := FirstExisting([
    ExpandConstant('{commonpf}\nodejs\node.exe'),
    ExpandConstant('{localappdata}\Programs\nodejs\node.exe')
  ]);
  if Result = '' then
    Result := FindOnPath('node.exe', 'node');
end;

function GetPythonFromRegistry: String;
var
  Versions: array of String;
  I: Integer;
  Path: String;
begin
  Result := '';
  Versions := ['3.13', '3.12', '3.11', '3.10', '3.9'];
  for I := 0 to GetArrayLength(Versions) - 1 do
  begin
    Path := '';
    if RegQueryStringValue(HKLM64, 'SOFTWARE\Python\PythonCore\' + Versions[I] + '\InstallPath', '', Path) then
    begin
      Path := AddBackslash(Path) + 'python.exe';
      if FileExists(Path) then
      begin
        Result := Path;
        Exit;
      end;
    end;
    Path := '';
    if RegQueryStringValue(HKCU, 'SOFTWARE\Python\PythonCore\' + Versions[I] + '\InstallPath', '', Path) then
    begin
      Path := AddBackslash(Path) + 'python.exe';
      if FileExists(Path) then
      begin
        Result := Path;
        Exit;
      end;
    end;
  end;
end;

function GetPythonExe(Param: String): String;
begin
  Result := GetPythonFromRegistry;
  if Result = '' then
    Result := FirstExisting([
      ExpandConstant('{commonpf}\Python312\python.exe'),
      ExpandConstant('{commonpf}\Python313\python.exe'),
      ExpandConstant('{localappdata}\Programs\Python\Python312\python.exe'),
      ExpandConstant('{localappdata}\Programs\Python\Python313\python.exe')
    ]);
  if Result = '' then
    Result := FindOnPath('python.exe', 'python');
end;

{ ------------------------------------------------------------------------- }
{ Component selection -> helper arguments                                    }
{ ------------------------------------------------------------------------- }

function JoinList(const Items: TArrayOfString): String;
var
  I: Integer;
begin
  Result := '';
  for I := 0 to GetArrayLength(Items) - 1 do
  begin
    if Items[I] = '' then
      Continue;
    if Result <> '' then
      Result := Result + ',';
    Result := Result + Items[I];
  end;
  if Result = '' then
    Result := 'none';
end;

function GetSttList(Param: String): String;
var
  Items: TArrayOfString;
  Count: Integer;
begin
  SetArrayLength(Items, 3);
  Count := 0;
  if WizardIsComponentSelected('stt\whisper_tiny') then
  begin
    Items[Count] := 'whisper-tiny';
    Count := Count + 1;
  end;
  if WizardIsComponentSelected('stt\nb_whisper_small') then
  begin
    Items[Count] := 'nb-whisper-small';
    Count := Count + 1;
  end;
  if WizardIsComponentSelected('stt\nb_whisper_medium') then
  begin
    Items[Count] := 'nb-whisper-medium';
    Count := Count + 1;
  end;
  SetArrayLength(Items, Count);
  Result := JoinList(Items);
end;

function GetTtsList(Param: String): String;
var
  Items: TArrayOfString;
  Count: Integer;
begin
  SetArrayLength(Items, 4);
  Count := 0;
  if WizardIsComponentSelected('tts\piper_talesyntese') then
  begin
    Items[Count] := 'piper-talesyntese';
    Count := Count + 1;
  end;
  if WizardIsComponentSelected('tts\piper_nvcc') then
  begin
    Items[Count] := 'piper-nvcc';
    Count := Count + 1;
  end;
  if WizardIsComponentSelected('tts\chatterbox') then
  begin
    Items[Count] := 'chatterbox';
    Count := Count + 1;
  end;
  if WizardIsComponentSelected('tts\vibevoice') then
  begin
    Items[Count] := 'vibevoice';
    Count := Count + 1;
  end;
  SetArrayLength(Items, Count);
  Result := JoinList(Items);
end;

function VoiceSelected: Boolean;
begin
  Result := (GetSttList('') <> 'none') or (GetTtsList('') <> 'none');
end;

{ ------------------------------------------------------------------------- }
{ Prerequisites: Node.js and (only when voice is selected) Python           }
{ ------------------------------------------------------------------------- }

function EnsureNode: Boolean;
var
  ResultCode: Integer;
begin
  Result := GetNodeExe <> '';
  if Result then
    Exit;
  Log('Node.js mangler, laster ned MSI.');
  DownloadPage.Clear;
  DownloadPage.Add('https://nodejs.org/dist/v22.20.0/node-v22.20.0-x64.msi', 'node.msi', '');
  DownloadPage.Show;
  try
    try
      DownloadPage.Download;
    except
      Log('Node.js-nedlasting feilet: ' + GetExceptionMessage);
      Result := False;
      Exit;
    end;
  finally
    DownloadPage.Hide;
  end;

  if not Exec('msiexec.exe', '/i "' + ExpandConstant('{tmp}\node.msi') + '" /qn /norestart',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
  begin
    Log('Node.js-installasjon feilet, kode ' + IntToStr(ResultCode));
    Result := False;
    Exit;
  end;
  Result := GetNodeExe <> '';
end;

function EnsurePython: Boolean;
var
  ResultCode: Integer;
  Installer: String;
begin
  Result := GetPythonExe('') <> '';
  if Result then
    Exit;
  Log('Python mangler, laster ned offisiell installer.');
  DownloadPage.Clear;
  DownloadPage.Add('https://www.python.org/ftp/python/3.12.10/python-3.12.10-amd64.exe', 'python-setup.exe', '');
  DownloadPage.Show;
  try
    try
      DownloadPage.Download;
    except
      Log('Python-nedlasting feilet: ' + GetExceptionMessage);
      Result := False;
      Exit;
    end;
  finally
    DownloadPage.Hide;
  end;

  Installer := ExpandConstant('{tmp}\python-setup.exe');
  if not Exec(Installer,
    '/quiet InstallAllUsers=1 PrependPath=1 Include_pip=1 Include_test=0 Include_launcher=1',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
  begin
    Log('Python-installasjon feilet, kode ' + IntToStr(ResultCode));
    Result := False;
    Exit;
  end;
  Result := GetPythonExe('') <> '';
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';
  { Stop running services from a previous installation before overwriting files. }
  if FileExists(ExpandConstant('{app}\tools\stop-bridge.ps1')) then
    Exec(ExpandConstant('{app}\tools\powershell-run.cmd'),
      '-File "' + ExpandConstant('{app}\tools\stop-bridge.ps1') + '" -InstallDir "' + ExpandConstant('{app}') + '"',
      '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  if not EnsureNode then
  begin
    NodeMissing := True;
    Result := 'Node.js kunne ikke installeres automatisk. Installer Node.js LTS fra https://nodejs.org og kjor installasjonen pa nytt.';
    Exit;
  end;
  if VoiceSelected and not EnsurePython then
  begin
    if MsgBox('Python kunne ikke installeres automatisk.' + #13#10 +
      'Du kan fortsette uten talemotorer og legge dem til senere ved a kjore installasjonen pa nytt.' + #13#10#13#10 +
      'Vil du fortsette uten tale?', mbConfirmation, MB_YESNO) = IDNO then
    begin
      Result := 'Installasjonen ble avbrutt fordi Python mangler.';
      Exit;
    end;
  end;
end;

{ ------------------------------------------------------------------------- }
{ Mobile page (QR codes + verified status)                                  }
{ ------------------------------------------------------------------------- }

procedure ReadInfoFile(const Path: String; var ApkUrl, PairUrl, Host, Verify, VerifyDetail,
  Status, Message, VoiceTts, VoiceStt, VoiceTest: String);
var
  Lines: TArrayOfString;
  I, P: Integer;
  Key, Value: String;
begin
  ApkUrl := ''; PairUrl := ''; Host := ''; Verify := ''; VerifyDetail := '';
  Status := ''; Message := ''; VoiceTts := ''; VoiceStt := ''; VoiceTest := '';
  if not LoadStringsFromFile(Path, Lines) then
    Exit;
  for I := 0 to GetArrayLength(Lines) - 1 do
  begin
    P := Pos('=', Lines[I]);
    if P <= 0 then
      Continue;
    Key := Copy(Lines[I], 1, P - 1);
    Value := Copy(Lines[I], P + 1, Length(Lines[I]));
    if Key = 'APK_URL' then ApkUrl := Value
    else if Key = 'PAIR_URL' then PairUrl := Value
    else if Key = 'HOST' then Host := Value
    else if Key = 'VERIFY' then Verify := Value
    else if Key = 'VERIFY_DETAIL' then VerifyDetail := Value
    else if Key = 'STATUS' then Status := Value
    else if Key = 'MESSAGE' then Message := Value
    else if Key = 'VOICE_TTS' then VoiceTts := Value
    else if Key = 'VOICE_STT' then VoiceStt := Value
    else if Key = 'VOICE_TEST' then VoiceTest := Value;
  end;
end;

procedure PrepareMobilePage;
var
  ResultCode: Integer;
  OutDir, ApkUrl, PairUrl, Host, Verify, VerifyDetail, Status, Message: String;
  VoiceTts, VoiceStt, VoiceTest: String;
  QrApkPath, QrPairPath: String;
  Summary: String;
begin
  if MobilePrepared then
    Exit;
  MobilePrepared := True;
  OutDir := ExpandConstant('{tmp}\mobile');
  ForceDirectories(OutDir);
  StatusCaption.Caption := 'Starter lokal server og genererer QR-koder...';
  WizardForm.Refresh;

  Exec(ExpandConstant('{app}\tools\powershell-run.cmd'),
    '-File "' + ExpandConstant('{app}\tools\prepare-mobile-page.ps1') + '" -AppDir "' +
    ExpandConstant('{app}') + '" -OutDir "' + OutDir + '"',
    ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, ResultCode);

  ReadInfoFile(OutDir + '\qr-info.txt', ApkUrl, PairUrl, Host, Verify, VerifyDetail,
    Status, Message, VoiceTts, VoiceStt, VoiceTest);

  QrApkPath := OutDir + '\qr-apk.bmp';
  if FileExists(QrApkPath) then
  begin
    QrApkImage.Bitmap.LoadFromFile(QrApkPath);
    QrApkImage.Visible := True;
    ApkCaption.Caption := 'Appen (APK)';
  end
  else
    ApkCaption.Caption := 'Appen (APK): QR-koden kunne ikke lages.';

  QrPairPath := OutDir + '\qr-pair.bmp';
  if FileExists(QrPairPath) then
  begin
    QrPairImage.Bitmap.LoadFromFile(QrPairPath);
    QrPairImage.Visible := True;
    PairCaption.Caption := 'OMP-paring';
  end
  else
    PairCaption.Caption := 'OMP-paring: start broen og kjor /ompqr.';

  if Verify = 'ok' then
    StatusCaption.Caption := 'APK-adresse: ' + ApkUrl + #13#10 +
      'Verifisert fra ' + Host + ' (' + VerifyDetail + ')'
  else
    StatusCaption.Caption := 'APK-adresse: ' + ApkUrl + #13#10 +
      'Advarsel: kunne ikke verifisere nedlastingen (' + VerifyDetail + ').';

  Summary := '';
  if VoiceTts <> '' then
    Summary := 'Talesyntese: ' + VoiceTts;
  if VoiceStt <> '' then
  begin
    if Summary <> '' then Summary := Summary + #13#10;
    Summary := Summary + 'Talegjenkjenning: ' + VoiceStt;
  end;
  if VoiceTest <> '' then
  begin
    if Summary <> '' then Summary := Summary + #13#10;
    Summary := Summary + 'Selvtest: ' + VoiceTest;
  end;
  if Summary = '' then
    Summary := 'Ingen talemotorer valgt. Du kan legge dem til senere ved a kjore installasjonen pa nytt.';
  VoiceCaption.Caption := Summary;
end;

procedure InitializeWizard;
begin
  Log('InitializeWizard: oppretter egendefinerte sider');

  DownloadPage := CreateDownloadPage('Laster ned forutsetninger',
    'Noen komponenter mangler pa denne PC-en og lastes ned na.', nil);
  DownloadPage.ShowBaseNameInsteadOfUrl := True;
  Log('InitializeWizard: DownloadPage opprettet, ID=' + IntToStr(DownloadPage.ID));

  { wpReady er den siste veiviser-siden for installeringen starter.
    wpInstalling er installasjonsfasen og er IKKE en gyldig AfterID for
    en veiviser-side som opprettes i InitializeWizard. }
  MobilePage := CreateCustomPage(wpReady, 'Mobilen',
    'Skann en av kodene med telefonen. Begge virker pa samme nettverk (LAN eller Tailscale).');
  Log('InitializeWizard: MobilePage opprettet, ID=' + IntToStr(MobilePage.ID));

  ApkCaption := TNewStaticText.Create(MobilePage.Surface);
  ApkCaption.Parent := MobilePage.Surface;
  ApkCaption.Left := 0;
  ApkCaption.Top := 0;
  ApkCaption.Width := ScaleX(200);
  ApkCaption.AutoSize := False;
  ApkCaption.WordWrap := True;
  ApkCaption.Caption := 'Appen (APK)';

  QrApkImage := TBitmapImage.Create(MobilePage.Surface);
  QrApkImage.Parent := MobilePage.Surface;
  QrApkImage.Left := 0;
  QrApkImage.Top := ScaleY(20);
  QrApkImage.Width := ScaleX(170);
  QrApkImage.Height := ScaleY(170);
  QrApkImage.Stretch := True;
  QrApkImage.Center := True;
  QrApkImage.Visible := False;

  PairCaption := TNewStaticText.Create(MobilePage.Surface);
  PairCaption.Parent := MobilePage.Surface;
  PairCaption.Left := MobilePage.SurfaceWidth - ScaleX(170);
  PairCaption.Top := 0;
  PairCaption.Width := ScaleX(170);
  PairCaption.AutoSize := False;
  PairCaption.WordWrap := True;
  PairCaption.Caption := 'OMP-paring';

  QrPairImage := TBitmapImage.Create(MobilePage.Surface);
  QrPairImage.Parent := MobilePage.Surface;
  QrPairImage.Left := MobilePage.SurfaceWidth - ScaleX(170);
  QrPairImage.Top := ScaleY(20);
  QrPairImage.Width := ScaleX(170);
  QrPairImage.Height := ScaleY(170);
  QrPairImage.Stretch := True;
  QrPairImage.Center := True;
  QrPairImage.Visible := False;

  StatusCaption := TNewStaticText.Create(MobilePage.Surface);
  StatusCaption.Parent := MobilePage.Surface;
  StatusCaption.Left := 0;
  StatusCaption.Top := ScaleY(204);
  StatusCaption.Width := MobilePage.SurfaceWidth;
  StatusCaption.AutoSize := False;
  StatusCaption.WordWrap := True;
  StatusCaption.Height := ScaleY(50);
  StatusCaption.Caption := '';

  VoiceCaption := TNewStaticText.Create(MobilePage.Surface);
  VoiceCaption.Parent := MobilePage.Surface;
  VoiceCaption.Left := 0;
  VoiceCaption.Top := ScaleY(262);
  VoiceCaption.Width := MobilePage.SurfaceWidth;
  VoiceCaption.AutoSize := False;
  VoiceCaption.WordWrap := True;
  VoiceCaption.Height := ScaleY(96);
  VoiceCaption.Caption := '';
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (CurPageID = MobilePage.ID) and not WizardSilent then
    PrepareMobilePage;
end;

{ ------------------------------------------------------------------------- }
{ Write access for the runtime folders                                      }
{                                                                           }
{ The install folder may be "C:\Program Files\Nimay OMP", which a normal     }
{ user cannot write to. The bridge, the APK server and the voice runtime all }
{ need to create their own state (config.json, *.pid, voice output, models,  }
{ venv), so those three folders are made writable for the local Users group. }
{ Only the folders below the install folder are changed - program files stay }
{ read-only.                                                                }
{ ------------------------------------------------------------------------- }

procedure GrantFolderWriteAccess(const Folder: String);
var
  ResultCode: Integer;
begin
  if not DirExists(Folder) then
    Exit;
  Exec(ExpandConstant('{sys}\icacls.exe'),
    '"' + Folder + '" /grant *S-1-5-32-545:(OI)(CI)M /T /C /Q',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

procedure GrantWriteAccess;
begin
  GrantFolderWriteAccess(ExpandConstant('{app}\bridge'));
  GrantFolderWriteAccess(ExpandConstant('{app}\voice'));
  GrantFolderWriteAccess(ExpandConstant('{app}\tools'));
end;

{ Creates bridge\config.json with a random pairing token before the bridge
  starts, so the phone pairing QR is stable from the very first start and the
  file is owned by the installer instead of failing a later non-elevated write. }

function EnsureBridgeConfig: Boolean;
var
  ResultCode: Integer;
  NodeExe, ConfigFile: String;
begin
  Result := True;
  ConfigFile := ExpandConstant('{app}\bridge\config.json');
  if FileExists(ConfigFile) then
    Exit;

  NodeExe := GetNodeExe;
  if NodeExe = '' then
  begin
    Result := False;
    Exit;
  end;

  { Let node write the file: random token + the same defaults the bridge uses. }
  if not Exec(NodeExe,
    '-e "const fs=require(''fs'');fs.writeFileSync(process.argv[1],JSON.stringify({token:require(''crypto'').randomBytes(32).toString(''hex''),port:Number(process.argv[2]),approvalMode:''always-ask''}))" "' +
    ConfigFile + '" 7777',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    Result := False;
    Exit;
  end;

  Result := (ResultCode = 0) and FileExists(ConfigFile);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    GrantWriteAccess;
    EnsureBridgeConfig;
  end;
end;

