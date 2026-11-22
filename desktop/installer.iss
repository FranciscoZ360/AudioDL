; Instalador de El Sebastián DJ para Windows (Inno Setup 6).
; Se instala solo para el usuario actual: no pide permisos de administrador,
; así la app puede actualizarse sola desde GitHub Releases.

#ifndef BuildNumber
  #define BuildNumber "0"
#endif

[Setup]
AppId={{8C1B0F7E-5D7A-4C3B-9B53-2E4C6A1D9F10}
AppName=El Sebastián DJ
AppVersion=2.1.{#BuildNumber}
AppVerName=El Sebastián DJ (build {#BuildNumber})
AppPublisher=FranciscoZ360
AppPublisherURL=https://github.com/FranciscoZ360/AudioDL
DefaultDirName={localappdata}\Programs\El Sebastian DJ
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=lowest
OutputDir=Output
OutputBaseFilename=ElSebastianDJ-Setup
SetupIconFile=assets\icon.ico
UninstallDisplayIcon={app}\ElSebastianDJ.exe
UninstallDisplayName=El Sebastián DJ
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; Borra los archivos de la versión anterior antes de copiar los nuevos.
Type: filesandordirs; Name: "{app}\_internal"

[Files]
Source: "dist\ElSebastianDJ\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\El Sebastián DJ"; Filename: "{app}\ElSebastianDJ.exe"
Name: "{autodesktop}\El Sebastián DJ"; Filename: "{app}\ElSebastianDJ.exe"; Tasks: desktopicon

[Run]
; Sin skipifsilent: tras una actualización automática la app se vuelve a abrir sola.
Filename: "{app}\ElSebastianDJ.exe"; Description: "{cm:LaunchProgram,El Sebastián DJ}"; Flags: nowait postinstall
