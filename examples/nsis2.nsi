!include "MUI.nsh"
!define PRODUCT "Legacy Example"
Name "${PRODUCT}"
OutFile "legacy-setup.exe"
InstallDir "$PROGRAMFILES\Legacy Example"
SetCompressor /SOLID lzma
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
Var Destination
Function Prepare
  StrCpy $Destination "$INSTDIR"
FunctionEnd
Section "Install"
  Call Prepare
  SetOutPath "$Destination"
  DetailPrint "Legacy ANSI script"
  nsExec::Exec '"$INSTDIR\app.exe" /register'
done:
SectionEnd
