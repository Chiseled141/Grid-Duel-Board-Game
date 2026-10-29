; Deck Duel Windows installer (Inno Setup 6.3 or newer).
; Built by tools\launcher\build-windows-installer.ps1, which passes these defines:
;   AppVersion      the release version, for example 1.1.0 or 1.2.0-beta.1
;   NumericVersion  the same without its label, for the file properties (1.2.0)
;   Staging         a folder holding runtime\, deck-duel-launcher.jar and the update package
;   PackageName     the update package's file name, deck-duel-<version>-windows-x64.zip
;
; Per-user install without administrator rights. The program folder holds the bundled Java
; runtime and the package; the setup step unpacks the game into %LOCALAPPDATA%\DeckDuel, the
; folder the automatic updater owns (docs/README.md#updates), and the shortcuts start its launcher copy.

#ifndef AppVersion
  #error Build with tools\launcher\build-windows-installer.ps1
#endif

; The updater's per-user installation root (launcher.InstallLayout.defaultRoot on Windows).
#define Root "{localappdata}\DeckDuel"
; Q is a doubled quote: a quote inside a quoted [Icons]/[Run] value.
#define Q '""'
#define Launch "-jar " + Q + Root + "\launcher\deck-duel-launcher.jar" + Q + " launch --root " + Q + Root + Q

[Setup]
; Never change AppId: later installers use it to upgrade this installation in place.
AppId={{2A13B2D0-E52A-40E5-BE48-4E26295A3EC4}
AppName=Deck Duel
AppVersion={#AppVersion}
VersionInfoVersion={#NumericVersion}
UninstallDisplayName=Deck Duel
UninstallDisplayIcon={app}\deck-duel.ico
SetupIconFile=deck-duel.ico
DefaultDirName={autopf}\Deck Duel
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputBaseFilename=DeckDuel-Setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
; Close a running game before its runtime is replaced or removed.
CloseApplications=yes
RestartApplications=no

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; An upgrade replaces the whole runtime and the previous installer's package.
Type: filesandordirs; Name: "{app}\runtime"
Type: files; Name: "{app}\deck-duel-*-windows-x64.zip"

[Files]
Source: "{#Staging}\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Staging}\deck-duel-launcher.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#Staging}\{#PackageName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "deck-duel.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Deck Duel"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "{#Launch}"; WorkingDir: "{#Root}"; IconFilename: "{app}\deck-duel.ico"
Name: "{autodesktop}\Deck Duel"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "{#Launch}"; WorkingDir: "{#Root}"; IconFilename: "{app}\deck-duel.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "{#Launch}"; WorkingDir: "{#Root}"; Description: "{cm:LaunchProgram,Deck Duel}"; Flags: nowait postinstall skipifsilent; Check: GameReady

[UninstallDelete]
; Installed game versions, downloads and logs. Preferences in %USERPROFILE%\.deckduel stay.
Type: filesandordirs; Name: "{#Root}"

[Code]
var
  Ready: Boolean;

function GameReady: Boolean;
begin
  Result := Ready;
end;

{ Unpacks the bundled package with the launcher's setup command, which keeps a newer version
  that an automatic update already installed. }
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  Arguments: String;
begin
  if CurStep <> ssPostInstall then exit;
  WizardForm.StatusLabel.Caption := 'Preparing Deck Duel...';
  Arguments := '-jar "' + ExpandConstant('{app}\deck-duel-launcher.jar') + '" setup --root "'
    + ExpandConstant('{#Root}') + '" --package "' + ExpandConstant('{app}\{#PackageName}') + '"';
  Ready := Exec(ExpandConstant('{app}\runtime\bin\java.exe'), Arguments, ExpandConstant('{app}'),
    SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
  if not Ready then
    MsgBox('Deck Duel could not finish setting up (code ' + IntToStr(ResultCode) + ').' + #13#10
      + 'Details are in ' + ExpandConstant('{#Root}\logs\launcher.log') + '.' + #13#10
      + 'Close Deck Duel if it is open and run this installer again.', mbError, MB_OK);
end;
