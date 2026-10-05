package com.yasin.vcardly.core.designsystem.component

/**
 * Up to two initials from the first and last word of [name]. Code-point aware so Telugu,
 * Devanagari, Arabic and emoji-led names do not get split mid-character.
 */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    fun first(word: String) = String(Character.toChars(word.codePointAt(0)))
    val letters = if (words.size == 1) first(words[0]) else first(words.first()) + first(words.last())
    return letters.uppercase()
}
