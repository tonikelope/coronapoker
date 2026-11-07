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
#ifndef AppImageBytes
  #error AppImageBytes must be supplied by build-installer.ps1
#endif
#ifndef ChileanModSha256
  #error ChileanModSha256 must be supplied by build-installer.ps1
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
ArchiveExtraction=full
WizardStyle=modern dynamic polar
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
Name: "mods"; Description: "🔒  {cm:ModEnable}"; GroupDescription: "MODs"; Flags: unchecked checkedonce
Name: "mods\chilean"; Description: "{cm:ModChilean}"; Flags: exclusive unchecked checkedonce
Name: "mods\local"; Description: "{cm:ModLocal}"; Flags: exclusive unchecked checkedonce

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{code:PreparedModPath}\*"; DestDir: "{code:IncomingModPath}"; Check: HasPreparedMod; BeforeInstall: InstallingModFiles; Flags: external recursesubdirs createallsubdirs ignoreversion uninsneveruninstall skipifsourcedoesntexist

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
english.FirewallAccess=Allow CoronaPoker through Windows Firewall
english.FirewallFailed=Windows Firewall could not be configured. CoronaPoker was installed, but Windows may ask for network access when you host a game.
english.FirewallRemoveFailed=The CoronaPoker Windows Firewall rule could not be removed automatically.
spanish.DesktopShortcut=Crear un acceso directo en el &escritorio
spanish.AdditionalShortcuts=Accesos directos:
spanish.LaunchCoronaPoker=Abrir CoronaPoker
spanish.NetworkAccess=Acceso de red:
spanish.FirewallAccess=Permitir CoronaPoker en el Firewall de Windows
spanish.FirewallFailed=No se pudo configurar el Firewall de Windows. CoronaPoker se ha instalado, pero Windows puede pedir permiso de red al crear una timba.
spanish.FirewallRemoveFailed=No se pudo quitar automáticamente la regla de CoronaPoker del Firewall de Windows.
english.InstallSize=Estimated installation size
english.ModEnable=Install a MOD (optional)
english.ModChilean=Chilean MOD · download from GitHub
english.ModLocal=Personal MOD · choose a ZIP file
english.ModFileDescription=Choose a MOD stored on this computer.
english.ModFileInstructions=Select a ZIP containing mod.xml at its root or inside a mod folder.
english.ModFileLabel=MOD archive
english.ModPasswordTitle=🔒  Protected MOD
english.ModPasswordDescription=The selected ZIP archive is encrypted.
english.ModPasswordInstructions=Enter the archive password to verify and extract the MOD.
english.ModPasswordLabel=ZIP password
english.ModPasswordRequired=Enter the password for the selected ZIP archive.
english.ModPasswordFailed=The MOD could not be extracted. Check the ZIP password and try again.
english.ModCopyFailed=Setup could not read the selected ZIP or copy it to temporary storage.
english.ModInvalid=The ZIP is invalid or unsupported. Select a single ZIP with a mod.xml file and safe file names.
english.ModDownloading=Downloading and verifying Chilean MOD.
english.ModExtracting=Checking and extracting MOD files.
english.ModInstalling=Installing MOD files…
english.ModNoSpace=There is insufficient free disk space to prepare or install the selected MOD.
english.ModInstallFailed=CoronaPoker could not activate the selected MOD.
english.ModBackupRetained=The previous MOD was preserved at %1.
spanish.InstallSize=Tamaño estimado de la instalación
spanish.ModEnable=Instalar un MOD (opcional)
spanish.ModChilean=Chilean MOD · descargar de GitHub
spanish.ModLocal=MOD personal · elegir un archivo ZIP
spanish.ModFileDescription=Elige un MOD guardado en este equipo.
spanish.ModFileInstructions=Selecciona un ZIP con mod.xml en su raíz o dentro de una carpeta mod.
spanish.ModFileLabel=Archivo del MOD
spanish.ModPasswordTitle=🔒  MOD protegido
spanish.ModPasswordDescription=El archivo ZIP seleccionado está cifrado.
spanish.ModPasswordInstructions=Introduce la contraseña del ZIP para verificar y extraer el MOD.
spanish.ModPasswordLabel=Contraseña del ZIP
spanish.ModPasswordRequired=Introduce la contraseña del archivo ZIP seleccionado.
spanish.ModPasswordFailed=No se pudo extraer el MOD. Comprueba la contraseña del ZIP e inténtalo de nuevo.
spanish.ModCopyFailed=No se pudo leer el ZIP seleccionado o copiarlo al almacenamiento temporal.
spanish.ModInvalid=El ZIP no es válido o no es compatible. Selecciona un único ZIP que contenga mod.xml y nombres de archivo válidos.
spanish.ModDownloading=Descargando y verificando Chilean MOD.
spanish.ModExtracting=Comprobando y extrayendo los archivos del MOD.
spanish.ModInstalling=Instalando los archivos del MOD…
spanish.ModNoSpace=No hay espacio libre suficiente para preparar o instalar el MOD seleccionado.
spanish.ModInstallFailed=CoronaPoker no pudo activar el MOD seleccionado.
spanish.ModBackupRetained=El MOD anterior se conservó en %1.

[Messages]
english.BeveledLabel=CoronaPoker {#AppVersion}
spanish.BeveledLabel=CoronaPoker {#AppVersion}
english.StatusExtractFiles=Installing CoronaPoker and Java…
spanish.StatusExtractFiles=Instalando CoronaPoker y Java…

[Code]
#include "ModArchive.iss"
#include "ModInstall.iss"

const
  BCM_SETSHIELD = $160C;
  FirewallRuleName = 'CoronaPoker';
  FirewallRegistryKey = 'Software\CoronaPoker';
  FirewallRegistryValue = 'FirewallRuleInstalled';

procedure CurPageChanged(CurPageID: Integer);
begin
  { Let Windows draw its native, DPI-aware UAC shield only when the selected
    install options will require the later elevated firewall operation. }
  SendMessage(WizardForm.NextButton.Handle, BCM_SETSHIELD, 0,
    Ord((CurPageID = wpReady) and WizardIsTaskSelected('firewall')));
  if CurPageID = wpInstalling then
    WizardForm.PageDescriptionLabel.Caption :=
      CustomMessage('InstallSize') + '  ·  ' + EstimatedInstallSize;
end;

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
  if CurStep = ssPostInstall then
    ActivatePreparedMod;
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
