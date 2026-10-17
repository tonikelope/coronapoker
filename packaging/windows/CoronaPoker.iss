#ifndef AppVersion
  #error AppVersion must be supplied by build-installer.ps1
#endif
#ifndef AppImage
  #error AppImage must be supplied by build-installer.ps1
#endif
#ifndef OutputDir
  #error OutputDir must be supplied by build-installer.ps1
#endif
#ifndef OutputBase
  #error OutputBase must be supplied by build-installer.ps1
#endif
#ifndef AppArchitecture
  #error AppArchitecture must be supplied by build-installer.ps1
#endif

[Setup]
AppId={{8E6B1337-F015-4F21-A920-73ACF3EF0A55}
AppName=CoronaPoker
AppVersion={#AppVersion}
AppVerName=CoronaPoker {#AppVersion}
AppPublisher=tonikelope
AppPublisherURL=https://github.com/tonikelope/coronapoker
AppSupportURL=https://github.com/tonikelope/coronapoker/issues
AppUpdatesURL=https://github.com/tonikelope/coronapoker/releases/latest
AppCopyright=Copyright (C) tonikelope
VersionInfoCompany=tonikelope
VersionInfoDescription=CoronaPoker Windows Setup
VersionInfoProductName=CoronaPoker
VersionInfoProductVersion={#AppVersion}.0.0
VersionInfoVersion={#AppVersion}.0.0
DefaultDirName={localappdata}\Programs\CoronaPoker
DefaultGroupName=CoronaPoker
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
SetupIconFile=CoronaPoker.ico
UninstallDisplayIcon={app}\CoronaPoker.exe
LicenseFile=..\..\LICENSE
OutputDir={#OutputDir}
OutputBaseFilename={#OutputBase}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
WizardImageFile=installer-wizard.png
WizardSmallImageFile=installer-small.png
WizardImageStretch=yes
WizardKeepAspectRatio=yes
WizardImageBackColor=$081B27
WizardSmallImageBackColor=$081B27
ShowLanguageDialog=auto
LanguageDetectionMethod=uilanguage
UsePreviousLanguage=yes
UsePreviousAppDir=yes
UsePreviousTasks=yes
CloseApplications=yes
RestartApplications=no
RestartIfNeededByRun=no
ArchitecturesAllowed={#AppArchitecture}compatible
ArchitecturesInstallIn64BitMode={#AppArchitecture}compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopShortcut}"; GroupDescription: "{cm:AdditionalShortcuts}"; Flags: checkedonce
Name: "firewall"; Description: "{cm:FirewallAccess}"; GroupDescription: "{cm:NetworkAccess}"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\CoronaPoker"; Filename: "{app}\CoronaPoker.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\CoronaPoker"; Filename: "{app}\CoronaPoker.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\CoronaPoker.exe"; Description: "{cm:LaunchCoronaPoker}"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent

[CustomMessages]
english.DesktopShortcut=Create a &desktop shortcut
english.AdditionalShortcuts=Shortcuts:
english.LaunchCoronaPoker=Launch CoronaPoker
english.NetworkAccess=Network access:
english.FirewallAccess=🛡 Allow CoronaPoker through Windows Firewall
english.FirewallFailed=Windows Firewall could not be configured. CoronaPoker was installed, but Windows may ask for network access when you host a game.
english.FirewallRemoveFailed=The CoronaPoker Windows Firewall rule could not be removed automatically.
spanish.DesktopShortcut=Crear un acceso directo en el &escritorio
spanish.AdditionalShortcuts=Accesos directos:
spanish.LaunchCoronaPoker=Abrir CoronaPoker
spanish.NetworkAccess=Acceso de red:
spanish.FirewallAccess=🛡 Permitir CoronaPoker en el Firewall de Windows
spanish.FirewallFailed=No se pudo configurar el Firewall de Windows. CoronaPoker se ha instalado, pero Windows puede pedir permiso de red al crear una timba.
spanish.FirewallRemoveFailed=No se pudo quitar automáticamente la regla de CoronaPoker del Firewall de Windows.

[Messages]
english.BeveledLabel=CoronaPoker {#AppVersion}
spanish.BeveledLabel=CoronaPoker {#AppVersion}

[Code]
const
  FirewallRuleName = 'CoronaPoker';
  FirewallRegistryKey = 'Software\CoronaPoker';
  FirewallRegistryValue = 'FirewallRuleInstalled';

function PowerShellQuote(Value: String): String;
begin
  StringChangeEx(Value, '''', '''''', True);
  Result := '''' + Value + '''';
end;

function RunElevatedPowerShell(const Script: String): Boolean;
var
  ExitCode: Integer;
  Parameters: String;
begin
  Parameters := '-NoLogo -NoProfile -NonInteractive ' +
    '-ExecutionPolicy Bypass -Command "' + Script + '"';
  Result := ShellExec('runas',
    ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'),
    Parameters, '', SW_HIDE, ewWaitUntilTerminated, ExitCode)
    and (ExitCode = 0);
end;

function InstallFirewallRule(): Boolean;
var
  ProgramPath: String;
  Script: String;
begin
  ProgramPath := PowerShellQuote(
    ExpandConstant('{app}\CoronaPoker.exe'));
  Script := '$ErrorActionPreference = ''Stop''; ' +
    'Get-NetFirewallApplicationFilter -Program ' + ProgramPath +
    ' -ErrorAction SilentlyContinue | Get-NetFirewallRule ' +
    '-ErrorAction SilentlyContinue | Where-Object { $_.Direction -eq ''Inbound'' } ' +
    '| Remove-NetFirewallRule; ' +
    'Get-NetFirewallRule -DisplayName ' +
    PowerShellQuote(FirewallRuleName) +
    ' -ErrorAction SilentlyContinue | Remove-NetFirewallRule; ' +
    'New-NetFirewallRule -DisplayName ' +
    PowerShellQuote(FirewallRuleName) +
    ' -Direction Inbound -Action Allow -Profile Private,Public -Program ' +
    ProgramPath + ' -Protocol TCP -Enabled True | Out-Null';
  Result := RunElevatedPowerShell(Script);
end;

function RemoveFirewallRule(): Boolean;
var
  ProgramPath: String;
  Script: String;
begin
  ProgramPath := PowerShellQuote(
    ExpandConstant('{app}\CoronaPoker.exe'));
  Script := '$ErrorActionPreference = ''Stop''; ' +
    'Get-NetFirewallApplicationFilter -Program ' + ProgramPath +
    ' -ErrorAction SilentlyContinue | Get-NetFirewallRule ' +
    '-ErrorAction SilentlyContinue | Where-Object { $_.Direction -eq ''Inbound'' } ' +
    '| Remove-NetFirewallRule; ' +
    'Get-NetFirewallRule -DisplayName ' +
    PowerShellQuote(FirewallRuleName) +
    ' -ErrorAction SilentlyContinue | Remove-NetFirewallRule';
  Result := RunElevatedPowerShell(Script);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and WizardIsTaskSelected('firewall') then
  begin
    if InstallFirewallRule() then
      RegWriteStringValue(HKCU, FirewallRegistryKey,
        FirewallRegistryValue, '1')
    else
      SuppressibleMsgBox(ExpandConstant('{cm:FirewallFailed}'),
        mbError, MB_OK, IDOK);
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if (CurUninstallStep = usUninstall) and
    RegValueExists(HKCU, FirewallRegistryKey,
      FirewallRegistryValue) then
  begin
    if RemoveFirewallRule() then
    begin
      RegDeleteValue(HKCU, FirewallRegistryKey,
        FirewallRegistryValue);
      RegDeleteKeyIfEmpty(HKCU, FirewallRegistryKey);
    end
    else
      SuppressibleMsgBox(ExpandConstant('{cm:FirewallRemoveFailed}'),
        mbError, MB_OK, IDOK);
  end;
end;
