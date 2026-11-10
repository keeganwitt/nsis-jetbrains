package dev.nsis.jetbrains.model

import java.util.Locale

data class NsisParameter(
    val name: String,
    val description: String,
    val optional: Boolean = false,
    val choices: List<String> = emptyList(),
    val combined: Boolean = false,
    val repeated: Boolean = false
) {
    val presentation: String get() = (if (optional) "[$name]" else name) + if (repeated) " ..." else ""
}

data class NsisSwitch(
    val name: String,
    val description: String,
    val takesValue: Boolean = false,
    val choices: List<String> = emptyList(),
    val afterOperands: Int = 0
) {
    fun matches(value: String): Boolean = if (name.endsWith('=')) value.startsWith(name, true) else value.equals(name, true)
    val presentation: String get() = "[$name${if (takesValue) " value" else if (name.endsWith('=')) "value" else ""}]"
}

data class NsisCommandInfo(
    val name: String,
    val summary: String,
    val parameters: List<NsisParameter>,
    val example: String,
    val url: String,
    val switches: List<NsisSwitch> = emptyList()
) {
    private val signatureOrder: List<Pair<Boolean, Int>> get() = buildList {
        for (index in 0..parameters.size) {
            switches.indices.filter { switches[it].afterOperands == index }.forEach { add(true to it) }
            if (index < parameters.size) add(false to index)
        }
    }
    val signatureParts: List<String> get() = signatureOrder.map { (switch, index) -> if (switch) switches[index].presentation else parameters[index].presentation }
    val signature: String get() = (listOf(name) + signatureParts).joinToString(" ")
    fun isSwitch(value: String): Boolean = switches.any { it.matches(value) }
    fun signatureIndex(operand: Int, switch: Int?): Int = signatureOrder.indexOf(
        if (switch != null) true to switch else false to if (parameters.lastOrNull()?.repeated == true) operand.coerceAtMost(parameters.lastIndex) else operand
    )

    fun forOperands(operands: List<NsisArgument>): NsisCommandInfo {
        if (name != "!define") return this
        val mode = operands.takeWhile { isSwitch(it.value) }.firstOrNull { it.value.lowercase(Locale.ROOT) in defineModes }?.value?.lowercase(Locale.ROOT)
        val extra = when (mode) {
            "/file" -> listOf(NsisParameter("filename", "Read the definition's value from this text file."))
            "/intfmt" -> listOf(NsisParameter("format", "Integer formatting pattern."), NsisParameter("value", "Integer to format."))
            "/math" -> listOf(NsisParameter("left", "Left integer operand."), NsisParameter("operator", "Integer operation.", choices = mathOperators), NsisParameter("right", "Right integer operand."))
            else -> return this
        }
        return copy(parameters = parameters.take(1) + extra)
    }

    companion object {
        private val defineModes = setOf("/date", "/utcdate", "/file", "/intfmt", "/math")
        private val mathOperators = listOf("+", "-", "*", "/", "%", "<<", ">>", ">>>", "&", "|", "^", "~", "!", "&&", "||")
    }
}

object NsisCommandMetadata {
    private const val scripting = "https://nsis.sourceforge.io/Docs/Chapter4.html"
    private const val preprocessor = "https://nsis.sourceforge.io/Docs/Chapter5.html"
    private val registryRoots = listOf("HKCR", "HKLM", "HKCU", "HKU", "HKCC", "HKDD", "HKPD", "SHCTX", "HKEY_CLASSES_ROOT", "HKEY_LOCAL_MACHINE", "HKEY_CURRENT_USER", "HKEY_USERS", "HKEY_CURRENT_CONFIG", "HKEY_DYN_DATA", "HKEY_PERFORMANCE_DATA", "SHELL_CONTEXT", "HKCR32", "HKCR64", "HKCU32", "HKCU64", "HKLM32", "HKLM64")
    private val buttons = listOf("IDOK", "IDCANCEL", "IDABORT", "IDRETRY", "IDIGNORE", "IDYES", "IDNO")
    private val messageFlags = listOf("MB_OK", "MB_OKCANCEL", "MB_ABORTRETRYIGNORE", "MB_RETRYCANCEL", "MB_YESNO", "MB_YESNOCANCEL", "MB_ICONEXCLAMATION", "MB_ICONINFORMATION", "MB_ICONQUESTION", "MB_ICONSTOP", "MB_USERICON", "MB_TOPMOST", "MB_SETFOREGROUND", "MB_RIGHT", "MB_RTLREADING", "MB_DEFBUTTON1", "MB_DEFBUTTON2", "MB_DEFBUTTON3", "MB_DEFBUTTON4")
    private fun p(name: String, description: String, optional: Boolean = false, choices: List<String> = emptyList(), combined: Boolean = false, repeated: Boolean = false) = NsisParameter(name, description, optional, choices, combined, repeated)
    private fun root() = p("root_key", "Registry root or shell-context root.", choices = registryRoots)
    private fun subkey() = p("subkey", "Registry key path below the selected root.")
    private fun valueName() = p("value_name", "Registry value name; an empty string selects the default value.")
    private fun output() = p("output", "Variable receiving the result.")
    private fun command(name: String, summary: String, anchor: String, example: String, vararg parameters: NsisParameter, switches: List<NsisSwitch> = emptyList()) =
        NsisCommandInfo(name, summary, parameters.toList(), example, "${if (name.startsWith('!')) preprocessor else scripting}#$anchor", switches)

    val entries: List<NsisCommandInfo> = listOf(
        command("File", "Package source files for extraction to the current output directory.", "file", "File /r \"assets\\*.*\"",
            p("source", "Source file or wildcard on the build system.", repeated = true), switches = listOf(
                NsisSwitch("/nonfatal", "Allow unmatched source files without a fatal error."),
                NsisSwitch("/a", "Preserve source file attributes."), NsisSwitch("/r", "Include matching files recursively."),
                NsisSwitch("/x", "Exclude a filename or wildcard.", takesValue = true),
                NsisSwitch("/oname=", "Set the destination filename; use with a single source file.")
            )),
        command("Exec", "Start a process and continue without waiting for it.", "exec", "Exec '\"${'$'}INSTDIR\\app.exe\"'", p("command", "Executable command line, including any arguments.")),
        command("ExecWait", "Start a process and wait for it to finish.", "execwait", "ExecWait '\"${'$'}INSTDIR\\app.exe\"' ${'$'}0",
            p("command", "Executable command line, including any arguments."), p("exit_code", "Optional variable receiving the process exit code.", true)),
        command("ExecShell", "Open a file or URL through a Windows shell action.", "execshell", "ExecShell open \"https://example.com\"",
            p("action", "Shell verb, or an empty string for the default verb.", choices = listOf("open", "print", "edit", "explore", "find", "properties", "runas")),
            p("file", "File, directory, or URL to open."), p("parameters", "Arguments for the shell operation.", true),
            p("show", "Initial window state.", true, listOf("SW_SHOWDEFAULT", "SW_SHOWNORMAL", "SW_SHOWMAXIMIZED", "SW_SHOWMINIMIZED", "SW_HIDE")),
            switches = listOf(NsisSwitch("/ALLOWERRORUI", "Allow shell error dialogs."), NsisSwitch("/DOENVSUBST", "Expand environment strings."), NsisSwitch("/INVOKEIDLIST", "Invoke through the shell item list."))),
        command("MessageBox", "Display a message and optionally branch on the selected button.", "messagebox", "MessageBox MB_YESNO|MB_ICONQUESTION \"Continue?\" IDNO cancel",
            p("flags", "Button, icon, and display flags joined with |.", choices = messageFlags, combined = true),
            p("text", "Message displayed to the user."), p("button1", "First result button to test.", true, buttons),
            p("label1", "Jump target for the first result.", true), p("button2", "Second result button to test.", true, buttons), p("label2", "Jump target for the second result.", true),
            switches = listOf(NsisSwitch("/SD", "Default button result during silent installation; place after the message text.", true, buttons, afterOperands = 2))),
        command("ReadRegStr", "Read a registry string into a variable.", "readregstr", "ReadRegStr ${'$'}0 HKCU \"Software\\Example\" \"Path\"", output(), root(), subkey(), valueName()),
        command("ReadRegDWORD", "Read a 32-bit integer registry value into a variable.", "readregdword", "ReadRegDWORD ${'$'}0 HKCU \"Software\\Example\" \"Enabled\"", output(), root(), subkey(), valueName()),
        command("WriteRegStr", "Write a string value, creating its registry key if needed.", "writeregstr", "WriteRegStr HKCU \"Software\\Example\" \"Path\" \"${'$'}INSTDIR\"", root(), subkey(), valueName(), p("value", "String data to store.")),
        command("WriteRegDWORD", "Write a 32-bit integer registry value.", "writeregdword", "WriteRegDWORD HKCU \"Software\\Example\" \"Enabled\" 1", root(), subkey(), valueName(), p("value", "32-bit integer data to store.")),
        command("DeleteRegKey", "Remove a registry key and its children, or require an empty key.", "deleteregkey", "DeleteRegKey /ifempty HKCU \"Software\\Example\"", root(), subkey(), switches = listOf(NsisSwitch("/ifempty", "Remove the key only if it has no values or subkeys."))),
        command("DeleteRegValue", "Remove one named registry value.", "deleteregvalue", "DeleteRegValue HKCU \"Software\\Example\" \"Path\"", root(), subkey(), valueName()),
        command("SetRegView", "Select the registry view used by following registry instructions.", "setregview", "SetRegView 64", p("view", "32-bit, 64-bit, or the installer's default registry view.", choices = listOf("32", "64", "default", "lastused"))),
        command("SetOutPath", "Set the extraction directory, creating it if necessary.", "setoutpath", "SetOutPath \"${'$'}INSTDIR\"", p("directory", "Output directory assigned to ${'$'}OUTDIR.")),
        command("StrCpy", "Copy all or part of a string into a variable.", "StrCpy", "StrCpy ${'$'}0 \"Example\" 3", output(), p("text", "Source string."), p("length", "Maximum characters to copy; negative values omit characters from the end.", true), p("start", "Source offset; negative values count from the end.", true)),
        command("StrCmp", "Compare two strings without case sensitivity and branch.", "strcmp", "StrCmp ${'$'}0 \"ready\" ready_label", p("left", "First string."), p("right", "Second string."), p("equal", "Jump target when equal."), p("different", "Jump target when different.", true)),
        command("IntCmp", "Compare signed integers and branch according to their ordering.", "intcmp", "IntCmp ${'$'}0 10 equal less greater", p("left", "First integer."), p("right", "Second integer."), p("equal", "Jump target when equal."), p("less", "Jump target when left is smaller.", true), p("greater", "Jump target when left is larger.", true)),
        command("Goto", "Jump to a label, relative instruction, or address stored in a variable.", "goto", "Goto finished", p("target", "Label, relative offset, or address variable.")),
        command("Call", "Call a function, :label, or address stored in a variable.", "call", "Call Initialize", p("target", "Function name, colon-prefixed label, or address variable.")),
        command("DetailPrint", "Append text to the installation details log.", "detailprint", "DetailPrint \"Installing files\"", p("text", "Text to append.")),
        command("Name", "Set the product name displayed by the installer.", "aname", "Name \"Example\"", p("name", "Displayed product name."), p("double_ampersand_name", "Optional name with doubled ampersands.", true)),
        command("OutFile", "Choose the installer executable produced by compilation.", "aoutfile", "OutFile \"setup.exe\"", p("filename", "Output executable path on the build system.")),
        command("InstallDir", "Set the initial installation directory.", "ainstalldir", "InstallDir \"${'$'}PROGRAMFILES\\Example\"", p("directory", "Default value of ${'$'}INSTDIR.")),
        command("RequestExecutionLevel", "Choose the Windows execution level requested by the installer.", "arequestexecutionlevel", "RequestExecutionLevel user", p("level", "Manifest execution level, or none to omit it.", choices = listOf("none", "user", "highest", "admin"))),
        command("SetShellVarContext", "Select current-user or all-users shell folder paths.", "setshellvarcontext", "SetShellVarContext current", p("context", "Scope for shell folder variables.", choices = listOf("current", "all", "lastused"))),
        command("Unicode", "Choose whether to build a Unicode installer.", "aunicodetarget", "Unicode true", p("enabled", "Enable or disable Unicode output before other instructions.", choices = listOf("true", "false"))),
        command("!include", "Read another source file during preprocessing.", "include", "!include \"MUI2.nsh\"", p("file", "Literal file path searched through the include directories."), switches = listOf(
            NsisSwitch("/NONFATAL", "Warn instead of failing when the file cannot be found."),
            NsisSwitch("/CHARSET=", "Encoding for a file without a byte-order mark.", choices = listOf("ACP", "OEM", "CP1252", "UTF8", "UTF16LE", "UTF16BE"))
        )),
        command("!addincludedir", "Add a directory to the compiler's include search path.", "addincludedir", "!addincludedir \"headers\"", p("directory", "Directory to search for includes.")),
        command("!define", "Define a preprocessor symbol, optionally deriving its value.", "define", "!define APP_NAME \"Example\"", p("name", "Preprocessor symbol name."), p("value", "Symbol value or format for date modes; omitted values are empty.", true), switches = listOf(
            NsisSwitch("/ifndef", "Only define a symbol if it does not exist."), NsisSwitch("/redef", "Permit replacing an existing symbol."),
            NsisSwitch("/date", "Format the local date and time."), NsisSwitch("/utcdate", "Format the UTC date and time."),
            NsisSwitch("/file", "Read a symbol value from a text file."), NsisSwitch("/intfmt", "Format an integer."), NsisSwitch("/math", "Compute an integer expression.")
        )),
        command("!insertmacro", "Expand a declared macro with the supplied parameters.", "insertmacro", "!insertmacro Configure \"Example\"", p("macro", "Name of a declared macro."), p("parameter", "Arguments required by the macro declaration.", true, repeated = true))
    )

    private val byName = entries.associateBy { it.name.lowercase(Locale.ROOT) }
    fun find(name: String): NsisCommandInfo? = byName[name.lowercase(Locale.ROOT)]
    fun referenceUrl(name: String): String = if (name.startsWith('!')) preprocessor else scripting
}
