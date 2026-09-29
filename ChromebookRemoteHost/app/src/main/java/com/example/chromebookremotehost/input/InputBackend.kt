package com.example.chromebookremotehost.input

interface InputBackend {
    val available: Boolean
    val explanation: String
    fun handlePointer(message: String): String
    fun handleKeyboard(message: String): String
}

class ChromeOsInputBackend : InputBackend {
    override val available = false
    override val explanation = "Global ChromeOS mouse/keyboard injection is not exposed to a normal Android app."
    override fun handlePointer(message: String) = "INPUT_UNAVAILABLE"
    override fun handleKeyboard(message: String) = "INPUT_UNAVAILABLE"
}
