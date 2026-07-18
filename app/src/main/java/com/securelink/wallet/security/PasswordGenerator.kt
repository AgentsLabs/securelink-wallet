package com.securelink.wallet.security

import java.security.SecureRandom

class PasswordGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    private val lower = "abcdefghijkmnopqrstuvwxyz"
    private val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val digits = "23456789"
    private val symbols = "!@#$%^&*()-_=+"
    private val all = lower + upper + digits + symbols

    fun generate(length: Int = 20): String {
        require(length >= 12) { "Password length must be at least 12" }
        val required = listOf(lower.randomChar(), upper.randomChar(), digits.randomChar(), symbols.randomChar())
        val remaining = List(length - required.size) { all.randomChar() }
        return (required + remaining).shuffled(random).joinToString("")
    }

    private fun String.randomChar(): Char = this[random.nextInt(length)]
}
