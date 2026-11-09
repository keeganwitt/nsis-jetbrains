package dev.nsis.jetbrains.model

import java.util.Locale

object NsisCatalog {
    val commands: List<String> = NsisCatalog::class.java.getResourceAsStream("/nsis-commands.txt")!!
        .bufferedReader().useLines { it.filter(String::isNotBlank).toList() }
    val commandNames = commands.mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
    val directives = listOf(
        "!include", "!addincludedir", "!addplugindir", "!appendfile", "!cd", "!delfile", "!echo", "!error",
        "!assert", "!execute", "!makensis", "!packhdr", "!finalize", "!uninstfinalize", "!system", "!tempfile",
        "!getdllversion", "!gettlbversion", "!warning", "!pragma", "!verbose", "!define", "!undef", "!ifdef",
        "!ifndef", "!if", "!ifmacrodef", "!ifmacrondef", "!else", "!endif", "!insertmacro", "!macro",
        "!macroend", "!macroundef", "!searchparse", "!searchreplace"
    )
    val variables = (0..9).map { "${'$'}$it" } + (0..9).map { "${'$'}R$it" } + listOf(
        "CMDLINE", "INSTDIR", "OUTDIR", "LANGUAGE", "PROGRAMFILES", "PROGRAMFILES32", "PROGRAMFILES64",
        "COMMONFILES", "COMMONFILES32", "COMMONFILES64", "DESKTOP", "EXEDIR", "EXEFILE", "EXEPATH",
        "TEMP", "WINDIR", "SYSDIR", "STARTMENU", "SMPROGRAMS", "SMSTARTUP", "QUICKLAUNCH", "DOCUMENTS",
        "SENDTO", "RECENT", "FAVORITES", "MUSIC", "PICTURES", "VIDEOS", "NETHOOD", "FONTS", "TEMPLATES",
        "APPDATA", "LOCALAPPDATA", "PRINTHOOD", "INTERNET_CACHE", "COOKIES", "HISTORY", "PROFILE",
        "ADMINTOOLS", "RESOURCES", "RESOURCES_LOCALIZED", "CDBURN_AREA", "HWNDPARENT", "PLUGINSDIR"
    ).map { "${'$'}$it" }
    val defines = listOf(
        "__FILE__", "__FILEDIR__", "__LINE__", "__DATE__", "__TIME__", "__TIMESTAMP__", "__COUNTER__",
        "__FUNCTION__", "__SECTION__", "__PAGEEX__", "NSISDIR", "NSIS_VERSION", "NSIS_PACKEDVERSION",
        "NSIS_CHAR_SIZE", "NSIS_PTR_SIZE", "NSIS_MAX_STRLEN", "NSIS_UNICODE"
    )
}
