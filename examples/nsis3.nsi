Unicode true
ManifestDPIAware true
ManifestLongPathAware true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!define PRODUCT "Unicode Example"
Name "${PRODUCT}"
OutFile "unicode-setup.exe"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
Section "Install"
  SetOutPath "$INSTDIR"
  ${If} $LANGUAGE == 1033
    DetailPrint "Installing ${PRODUCT}: café ${U+263A}"
  ${EndIf}
  FileOpen $0 "$INSTDIR\message.txt" w
  FileWriteUTF16LE $0 "Welcome$\r$\n"
  FileClose $0
SectionEnd
