package com.vonreyher.medicationtracker.data

/**
 * Extracts the PZN (Pharmazentralnummer) from the codes printed on German medicine packages:
 * - the securPharm DataMatrix in IFA format (PPN "9N11<PZN><check>") or GS1 format (NTIN "01 04150<PZN><check>"),
 * - the PZN barcode (Code 39, "-12345678" or "PZN-12345678"),
 * - or a plain 7/8-digit number.
 */
object PackageCodeParser {

    private val Ppn = Regex("""9N11(\d{8})\d{2}""")
    private val Gs1Ntin = Regex("""(?:^|\u001D|\])(?:01|\(01\))04150(\d{8})\d""")
    private val Code39 = Regex("""^(?:PZN)?-?(\d{7,8})$""")

    fun pznFrom(raw: String): String? {
        val text = raw.trim()
        Ppn.find(text)?.let { return it.groupValues[1].takeIf(::isValidPzn) }
        Gs1Ntin.find(text)?.let { return it.groupValues[1].takeIf(::isValidPzn) }
        Code39.find(text.replace(" ", ""))?.let { match ->
            return match.groupValues[1].padStart(8, '0').takeIf(::isValidPzn)
        }
        return null
    }

    /** PZN-8 check digit: digits weighted 1..7, sum mod 11 equals the last digit. */
    fun isValidPzn(pzn: String): Boolean {
        if (pzn.length != 8 || !pzn.all(Char::isDigit)) return false
        val sum = (0 until 7).sumOf { (it + 1) * (pzn[it] - '0') }
        val check = sum % 11
        return check != 10 && check == pzn[7] - '0'
    }
}
