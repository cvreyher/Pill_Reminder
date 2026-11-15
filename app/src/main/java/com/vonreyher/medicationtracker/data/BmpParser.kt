package com.vonreyher.medicationtracker.data

import com.vonreyher.medicationtracker.placeholderName
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BmpFormatException(message: String) : Exception(message)

/** One scanned page of a BMP. Plans with many entries are printed on several pages, each with its own code. */
data class BmpPage(
    val instanceId: String?,
    val page: Int,
    val pageCount: Int,
    val patientName: String?,
    val patientGivenName: String?,
    val patientFamilyName: String?,
    val patientBirthDate: String?,
    val author: String?,
    val printedOn: String?,
    val entries: List<PlanEntry>
)

/**
 * Parses the DataMatrix content of the Bundeseinheitlicher Medikationsplan
 * (BMP, Anlage 3 der Vereinbarung nach § 31a SGB V, "Ultrakurzformat").
 *
 * The payload is a compact XML document:
 * ```
 * <MP v="026" U="…" l="de-DE" a="1" z="1">
 *   <P g="Max" f="Mustermann" b="19500101"/>
 *   <A n="Praxis Dr. Müller" t="20260115"/>
 *   <S t="Dauermedikation">
 *     <M p="1234567" m="1" d="0" v="1" h="0" r="Blutdruck"/>
 *     <M a="Metformin 500" f="TAB" m="1" v="1"><W w="Metformin" s="500 mg"/></M>
 *   </S>
 * </MP>
 * ```
 */
object BmpParser {

    /** The BMP code is ISO-8859-1 encoded; fall back to UTF-8 if the bytes are valid UTF-8. */
    fun decode(rawBytes: ByteArray?, rawValue: String?): String {
        if (rawBytes == null || rawBytes.isEmpty()) {
            return rawValue ?: throw BmpFormatException("Empty barcode")
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(rawBytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(rawBytes, Charsets.ISO_8859_1)
        }
    }

    /** Decodes and parses a scanned code, or returns null if it is not a BMP. */
    fun parseOrNull(rawBytes: ByteArray?, rawValue: String?): BmpPage? =
        try {
            parse(decode(rawBytes, rawValue))
        } catch (_: BmpFormatException) {
            null
        }

    fun parse(content: String): BmpPage {
        val start = content.indexOf("<MP")
        if (start < 0) throw BmpFormatException("Not a medication plan")
        val xml = content.substring(start)

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var instanceId: String? = null
        var page = 1
        var pageCount = 1
        var patientName: String? = null
        var patientGivenName: String? = null
        var patientFamilyName: String? = null
        var birthDate: String? = null
        var author: String? = null
        var printedOn: String? = null
        var section: String? = null
        val entries = mutableListOf<PlanEntry>()
        var current: PlanEntry? = null
        val ingredients = mutableListOf<Pair<String?, String?>>()

        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "MP" -> {
                            instanceId = parser.attr("U")
                            page = parser.attr("a")?.toIntOrNull() ?: 1
                            pageCount = parser.attr("z")?.toIntOrNull() ?: 1
                        }
                        "P" -> {
                            patientName = listOfNotNull(parser.attr("t"), parser.attr("g"), parser.attr("v"), parser.attr("f"))
                                .joinToString(" ")
                                .ifBlank { null }
                            patientGivenName = parser.attr("g")
                            patientFamilyName = listOfNotNull(parser.attr("v"), parser.attr("f")).joinToString(" ").ifBlank { null }
                            birthDate = parser.attr("b")?.toDisplayDate()
                        }
                        "A" -> {
                            author = parser.attr("n")
                            printedOn = parser.attr("t")?.toDisplayDate()
                        }
                        "S" -> section = parser.attr("t")
                        "M" -> {
                            ingredients.clear()
                            current = PlanEntry(
                                name = parser.attr("a") ?: "",
                                pzn = parser.attr("p"),
                                form = parser.attr("fd") ?: parser.attr("f"),
                                morning = parser.attr("m"),
                                noon = parser.attr("d"),
                                evening = parser.attr("v"),
                                night = parser.attr("h"),
                                freeTextDosage = parser.attr("t"),
                                unit = parser.attr("dud"),
                                note = parser.attr("i"),
                                reason = parser.attr("r"),
                                section = section
                            )
                        }
                        "W" -> if (current != null) ingredients += parser.attr("w") to parser.attr("s")
                        // Recipes (R) and free-text lines (X) have no PZN, only a text.
                        "R" -> parser.attr("t")?.let {
                            entries += PlanEntry(name = it, note = parser.attr("x"), section = section)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "M" -> current?.let { entry ->
                            entries += entry.withIngredients(ingredients)
                            current = null
                        }
                        "S" -> section = null
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            throw BmpFormatException("Invalid plan data: ${e.message}")
        }

        return BmpPage(instanceId, page, pageCount, patientName, patientGivenName, patientFamilyName, birthDate, author, printedOn, entries)
    }

    private fun PlanEntry.withIngredients(ingredients: List<Pair<String?, String?>>): PlanEntry {
        val ingredientNames = ingredients.mapNotNull { it.first }.joinToString(", ").ifBlank { null }
        val strengths = ingredients.mapNotNull { it.second }.joinToString(" / ").ifBlank { null }
        return copy(
            // Plans may only carry the PZN; the trade name then has to be looked up in a drug database.
            name = name.ifBlank { ingredientNames ?: pzn?.let(::placeholderName) ?: "Unbekanntes Medikament" },
            strength = strengths,
            ingredient = ingredientNames
        )
    }

    private fun XmlPullParser.attr(name: String): String? =
        getAttributeValue(null, name)?.trim()?.ifBlank { null }

    private val InputDate = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DisplayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun String.toDisplayDate(): String =
        runCatching { LocalDate.parse(take(8), InputDate).format(DisplayDate) }.getOrDefault(this)
}
