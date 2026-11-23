package com.manima.foodlens.core.gtin

import com.manima.foodlens.core.model.Symbology

/**
 * GTIN normalisation, SPEC §7. Mirrors the Python implementation in `services/api/app/gtin`;
 * both must pass `packages/fixtures/gtin.json`.
 *
 * GTINs are always strings. Leading zeros are never stripped.
 */
object Gtin {
    const val GTIN14_LENGTH = 14

    private const val EAN8_LENGTH = 8
    private const val UPCA_LENGTH = 12
    private const val EAN13_LENGTH = 13
    private const val MOD = 10
    private const val ODD_WEIGHT = 3

    /** Restricted-circulation GTIN-13 prefixes (`02`, `04`, `20`–`29`). */
    private val RESTRICTED_PREFIXES = setOf("02", "04") + (20..29).map { it.toString() }

    /** Mod-10 check digit for [body] (all digits except the check digit), weights 3,1,3,… from the right. */
    fun computeCheckDigit(body: String): Int {
        require(body.isNotEmpty() && body.all(Char::isAsciiDigit)) { "body must be non-empty digits" }
        val sum = body.reversed().withIndex().sumOf { (i, c) -> c.digitToInt() * if (i % 2 == 0) ODD_WEIGHT else 1 }
        return (MOD - sum % MOD) % MOD
    }

    fun hasValidCheckDigit(code: String): Boolean = code.length >= 2 &&
        code.all(Char::isAsciiDigit) &&
        computeCheckDigit(code.dropLast(1)) == code.last().digitToInt()

    /**
     * Expands an 8-digit UPC-E code (`N d1..d6 C`) to its 12-digit UPC-A form.
     * Returns null when the number system digit is not 0 or 1.
     */
    @Suppress("MagicNumber") // digit positions transcribe the SPEC §7 UPC-E table literally
    fun expandUpcE(code: String): String? {
        require(code.length == EAN8_LENGTH && code.all(Char::isAsciiDigit)) { "UPC-E must be 8 digits" }
        val n = code[0]
        if (n != '0' && n != '1') return null
        val d = code.substring(1, 7)
        val check = code[7]
        val (manufacturer, product) = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}00" to "00${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00" to "000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}0" to "0000${d[4]}"
            else -> d.substring(0, 5) to "0000${d[5]}"
        }
        return "$n$manufacturer$product$check"
    }

    /**
     * Normalises a scanned or typed code to GTIN-14.
     *
     * `original_code` keeps the entered digits (whitespace and hyphens removed) for display.
     *
     * @param symbology the scanner-reported symbology; `null` or [Symbology.MANUAL] for manual entry.
     */
    fun normalize(input: String, symbology: Symbology? = null): GtinResult {
        val digits = input.filterNot { it.isWhitespace() || it == '-' }
        if (digits.isEmpty() ||
            !digits.all(Char::isAsciiDigit)
        ) {
            return GtinResult.Invalid(GtinErrorCode.INVALID_BARCODE)
        }

        return when (symbology) {
            null, Symbology.MANUAL -> normalizeManual(digits, digits)
            Symbology.EAN_8 -> requireLength(digits, EAN8_LENGTH) { fromPlain(digits, it, Symbology.EAN_8) }
            Symbology.UPC_E -> requireLength(digits, EAN8_LENGTH) { fromUpcE(digits, it) }
            Symbology.UPC_A -> requireLength(digits, UPCA_LENGTH) { fromPlain(digits, it, Symbology.UPC_A) }
            Symbology.EAN_13 -> requireLength(digits, EAN13_LENGTH) { fromPlain(digits, it, Symbology.EAN_13) }
            Symbology.GTIN_14 -> requireLength(digits, GTIN14_LENGTH) { fromPlain(digits, it, Symbology.GTIN_14) }
        }
    }

    private fun normalizeManual(original: String, digits: String): GtinResult = when (digits.length) {
        EAN8_LENGTH -> normalizeManualEight(original, digits)
        UPCA_LENGTH -> fromPlain(original, digits, Symbology.UPC_A)
        EAN13_LENGTH -> fromPlain(original, digits, Symbology.EAN_13)
        GTIN14_LENGTH -> fromPlain(original, digits, Symbology.GTIN_14)
        else -> GtinResult.Invalid(GtinErrorCode.INVALID_BARCODE)
    }

    /** SPEC §7 step 2: try EAN-8 first, then UPC-E (only if first digit is 0 or 1); both valid → ambiguous. */
    private fun normalizeManualEight(original: String, digits: String): GtinResult {
        val ean8 = fromPlain(original, digits, Symbology.EAN_8) as? GtinResult.Valid
        val upcE = if (digits[0] == '0' || digits[0] == '1') fromUpcE(original, digits) as? GtinResult.Valid else null
        return when {
            ean8 != null && upcE != null && ean8.gtin14 != upcE.gtin14 -> GtinResult.Ambiguous(
                original,
                listOf(ean8, upcE)
            )
            ean8 != null -> ean8
            upcE != null -> upcE
            else -> GtinResult.Invalid(GtinErrorCode.INVALID_CHECK_DIGIT)
        }
    }

    private inline fun requireLength(digits: String, length: Int, block: (String) -> GtinResult): GtinResult =
        if (digits.length == length) block(digits) else GtinResult.Invalid(GtinErrorCode.INVALID_BARCODE)

    private fun fromUpcE(original: String, digits: String): GtinResult {
        val upcA = expandUpcE(digits) ?: return GtinResult.Invalid(GtinErrorCode.INVALID_BARCODE)
        return fromPlain(original, upcA, Symbology.UPC_E)
    }

    private fun fromPlain(original: String, digits: String, symbology: Symbology): GtinResult {
        if (!hasValidCheckDigit(digits)) return GtinResult.Invalid(GtinErrorCode.INVALID_CHECK_DIGIT)
        val gtin14 = digits.padStart(GTIN14_LENGTH, '0')
        return GtinResult.Valid(
            gtin14 = gtin14,
            originalCode = original,
            symbology = symbology,
            isRestrictedCirculation = isRestrictedCirculation(gtin14)
        )
    }

    /** Only GTIN-13-shaped codes (indicator digit 0) carry restricted-circulation prefixes. */
    @Suppress("MagicNumber") // characters 1..2 of the GTIN-14 = the GTIN-13 prefix
    fun isRestrictedCirculation(gtin14: String): Boolean =
        gtin14.length == GTIN14_LENGTH && gtin14[0] == '0' && gtin14.substring(1, 3) in RESTRICTED_PREFIXES
}

/** Error codes are the SPEC §8.2 wire codes verbatim. */
enum class GtinErrorCode { INVALID_BARCODE, INVALID_CHECK_DIGIT, AMBIGUOUS_BARCODE }

sealed interface GtinResult {
    data class Valid(
        val gtin14: String,
        val originalCode: String,
        val symbology: Symbology,
        val isRestrictedCirculation: Boolean
    ) : GtinResult

    data class Invalid(val code: GtinErrorCode) : GtinResult

    /** SPEC §7 step 2: client must let the user choose between [candidates]. */
    data class Ambiguous(val originalCode: String, val candidates: List<Valid>) : GtinResult {
        val code: GtinErrorCode get() = GtinErrorCode.AMBIGUOUS_BARCODE
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
