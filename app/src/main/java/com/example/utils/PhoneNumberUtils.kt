package com.example.utils

object PhoneNumberUtils {

    /**
     * Normalizes a phone number by stripping all non-digit characters
     * except a leading '+' symbol if present.
     */
    fun normalize(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return ""
        val trimmed = rawNumber.trim()
        val hasPlus = trimmed.startsWith("+")
        val sb = StringBuilder(trimmed.length)
        if (hasPlus) sb.append('+')
        for (i in 0 until trimmed.length) {
            val ch = trimmed[i]
            if (ch.isDigit()) sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Extracts the core national digits (typically the last 10 digits for Indian & US numbers).
     * Handles +91XXXXXXXXXX, 0XXXXXXXXXX, and plain XXXXXXXXXX with minimal allocations.
     */
    fun extractCoreDigits(number: String?): String {
        if (number.isNullOrBlank()) return ""
        val buf = CharArray(10)
        var count = 0
        var i = number.length - 1
        while (i >= 0 && count < 10) {
            val ch = number[i]
            if (ch.isDigit()) {
                buf[9 - count] = ch
                count++
            }
            i--
        }
        return if (count == 0) "" else String(buf, 10 - count, count)
    }

    /**
     * Compares two phone numbers robustly by matching their core digits.
     */
    fun areNumbersEqual(num1: String?, num2: String?): Boolean {
        if (num1.isNullOrBlank() || num2.isNullOrBlank()) return false
        val core1 = extractCoreDigits(num1)
        if (core1.isEmpty()) return false
        val core2 = extractCoreDigits(num2)
        return core2.isNotEmpty() && core1 == core2
    }

    /**
     * Formats a phone number for elegant human-readable display.
     */
    fun formatDisplay(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return "Unknown"
        val clean = rawNumber.trim()
        val digits = clean.filter { it.isDigit() }

        // Indian 10-digit format
        return if (clean.startsWith("+91") && digits.length >= 12) {
            val national = digits.takeLast(10)
            "+91 ${national.substring(0, 5)} ${national.substring(5)}"
        } else if (digits.length == 10) {
            "${digits.substring(0, 5)} ${digits.substring(5)}"
        } else {
            clean
        }
    }

    /**
     * Validates that a dialpad field string contains only dialable characters (0-9, +, *, #)
     * and standard phone formatting separators (' ', '-', '(', ')', '.') with no text letters
     * or invalid symbols, and contains at least one dialable digit or symbol.
     */
    fun isValidDialpadFieldText(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val validChars = raw.all { ch -> ch.isDigit() || ch in "+*# -()." }
        if (!validChars) return false
        return raw.any { ch -> ch.isDigit() || ch in "+*#" }
    }

    /**
     * Strictly validates pasted or typed dialpad strings.
     * Completely rejects strings containing letters or non-phone characters by returning null;
     * otherwise returns the cleaned dialable characters (digits and +, *, #).
     */
    fun sanitizeDialpadInput(raw: String?): String? {
        if (!isValidDialpadFieldText(raw)) return null
        val cleaned = raw!!.filter { ch -> ch.isDigit() || ch in "+*#" }
        return cleaned.ifEmpty { null }
    }

    /**
     * Identifies commercial / telemarketing prefix series (140, 180, 14, 18) such as 140xxx and 1800xxx,
     * while explicitly allowing normal personal/mobile numbers starting with 6, 7, 8, or 9.
     */
    fun isCommercialTelemarketingNumber(rawNumber: String?): Boolean {
        if (rawNumber.isNullOrBlank()) return false
        val digits = buildString(rawNumber.length) {
            for (ch in rawNumber) {
                if (ch.isDigit()) append(ch)
            }
        }
        if (digits.isEmpty()) return false

        var local = digits.trimStart('0')
        if (local.startsWith("91") && local.length > 10) {
            local = local.substring(2).trimStart('0')
        }
        if (local.isEmpty()) return false

        val first = local[0]
        if (first == '6' || first == '7' || first == '8' || first == '9') {
            return false
        }

        return local.startsWith("140") ||
            local.startsWith("180") ||
            local.startsWith("14") ||
            local.startsWith("18")
    }
}
