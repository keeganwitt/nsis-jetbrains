package dev.nsis.jetbrains.model

object NsisIdentifiers {
    const val VARIABLE_CHARACTERS = "A-Za-z_0-9."
    fun isVariablePart(character: Char) = character in 'a'..'z' || character in 'A'..'Z' ||
        character in '0'..'9' || character == '_' || character == '.'
}
