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
spanish.DesktopShortcut=Crear un acceso directo en el &escritorio
spanish.AdditionalShortcuts=Accesos directos:
spanish.LaunchCoronaPoker=Abrir CoronaPoker

[Messages]
english.BeveledLabel=CoronaPoker {#AppVersion}
spanish.BeveledLabel=CoronaPoker {#AppVersion}
