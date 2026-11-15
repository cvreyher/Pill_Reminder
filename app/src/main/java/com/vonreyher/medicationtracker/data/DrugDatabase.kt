package com.vonreyher.medicationtracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** One medicine from the local drug database. */
data class Drug(
    val pzn: String,
    val name: String,
    val manufacturer: String? = null,
    val form: String? = null,
    val strength: String? = null
)

data class DrugImportResult(val imported: Int, val skipped: Int)

/**
 * Local, offline drug database mapping PZN to product name. The app ships without data: the user
 * imports a CSV file they are licensed to use (e.g. an IFA export or their own list).
 */
class DrugDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "drugs.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE drug (
                pzn TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                name_search TEXT NOT NULL,
                manufacturer TEXT,
                form TEXT,
                strength TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX drug_name ON drug(name_search)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun find(pzn: String): Drug? {
        val normalized = normalizePzn(pzn) ?: return null
        readableDatabase.query("drug", Columns, "pzn = ?", arrayOf(normalized), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toDrug() else null
        }
    }

    /** Finds medicines by PZN prefix or by a part of the name; names starting with the query come first. */
    fun search(query: String, limit: Int = 20): List<Drug> {
        val text = query.trim()
        if (text.length < 2) return emptyList()
        val digits = text.filter(Char::isDigit)
        val (selection, args, order) = if (digits.length == text.length) {
            Triple("pzn LIKE ?", arrayOf("$digits%"), "pzn")
        } else {
            val term = text.lowercase(Locale.GERMAN).escapeLike()
            Triple(
                "name_search LIKE ? ESCAPE '\\'",
                arrayOf("%$term%"),
                // Prefix matches first, then alphabetically.
                "CASE WHEN name_search LIKE '${term.replace("'", "''")}%' ESCAPE '\\' THEN 0 ELSE 1 END, name"
            )
        }
        readableDatabase.query("drug", Columns, selection, args, null, null, order, limit.toString()).use { cursor ->
            return buildList { while (cursor.moveToNext()) add(cursor.toDrug()) }
        }
    }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM drug", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun lastImport(): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = 'lastImport'", null)
            .use { if (it.moveToFirst()) it.getString(0) else null }

    fun clear() {
        writableDatabase.apply {
            delete("drug", null, null)
            delete("meta", null, null)
        }
    }

    /**
     * Imports a CSV file. Recognises the delimiter (; , or tab), the encoding (UTF-8 or ISO-8859-1)
     * and the columns by their header (PZN, Name/Bezeichnung, Hersteller, Darreichungsform, Stärke);
     * without a header the first column is the PZN and the second the name. Existing PZNs are updated.
     */
    fun importCsv(input: InputStream, importedOn: String, onProgress: (Int) -> Unit = {}): DrugImportResult {
        val stream = BufferedInputStream(input)
        val reader = stream.bufferedReader(detectCharset(stream))
        val firstLine = reader.readLine() ?: return DrugImportResult(0, 0)
        val delimiter = listOf(';', '\t', ',').maxBy { d -> firstLine.count { it == d } }
        val header = splitCsv(firstLine, delimiter).map { it.lowercase(Locale.GERMAN) }

        fun column(vararg names: String) = header.indexOfFirst { cell -> names.any { cell.contains(it) } }
        val hasHeader = column("pzn") >= 0
        val pznCol = if (hasHeader) column("pzn") else 0
        val nameCol = if (hasHeader) column("name", "bezeichnung", "produkt", "artikel", "handelsname").takeIf { it >= 0 } ?: 1 else 1
        val manufacturerCol = if (hasHeader) column("hersteller", "anbieter", "manufacturer", "firma") else -1
        val formCol = if (hasHeader) column("darreichung", "form") else -1
        val strengthCol = if (hasHeader) column("stärke", "staerke", "strength", "wirkstärke") else -1

        var imported = 0
        var skipped = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            fun handle(line: String) {
                val cells = splitCsv(line, delimiter)
                val pzn = cells.getOrNull(pznCol)?.let(::normalizePzn)
                val name = cells.getOrNull(nameCol)?.trim().orEmpty()
                if (pzn == null || name.isEmpty()) {
                    skipped++
                    return
                }
                db.insertWithOnConflict(
                    "drug",
                    null,
                    ContentValues().apply {
                        put("pzn", pzn)
                        put("name", name)
                        put("name_search", name.lowercase(Locale.GERMAN))
                        put("manufacturer", cells.getOrNull(manufacturerCol)?.trim()?.ifEmpty { null })
                        put("form", cells.getOrNull(formCol)?.trim()?.ifEmpty { null })
                        put("strength", cells.getOrNull(strengthCol)?.trim()?.ifEmpty { null })
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                imported++
                if (imported % 5_000 == 0) onProgress(imported)
            }
            if (!hasHeader) handle(firstLine)
            reader.forEachLine { if (it.isNotBlank()) handle(it) }
            db.insertWithOnConflict(
                "meta", null,
                ContentValues().apply { put("key", "lastImport"); put("value", importedOn) },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return DrugImportResult(imported, skipped)
    }

    companion object {
        private val Columns = arrayOf("pzn", "name", "manufacturer", "form", "strength")

        @Volatile
        private var instance: DrugDatabase? = null

        fun get(context: Context): DrugDatabase =
            instance ?: synchronized(this) {
                instance ?: DrugDatabase(context.applicationContext).also { instance = it }
            }

        /** Digits only, 7-digit PZNs padded to 8; null if it is no valid PZN. */
        fun normalizePzn(raw: String): String? {
            val digits = raw.filter(Char::isDigit)
            if (digits.length !in 7..8) return null
            return digits.padStart(8, '0').takeIf(PackageCodeParser::isValidPzn)
        }

        private fun android.database.Cursor.toDrug() = Drug(
            pzn = getString(0),
            name = getString(1),
            manufacturer = getString(2),
            form = getString(3),
            strength = getString(4)
        )

        private fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        /** German exports are often ISO-8859-1; use UTF-8 only when the beginning decodes cleanly. */
        private fun detectCharset(stream: BufferedInputStream): Charset {
            val sampleSize = 64 * 1024
            stream.mark(sampleSize)
            val sample = ByteArray(sampleSize)
            val read = stream.read(sample).coerceAtLeast(0)
            stream.reset()
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(sample, 0, (read - 4).coerceAtLeast(0)))
                Charsets.UTF_8
            } catch (_: CharacterCodingException) {
                Charsets.ISO_8859_1
            }
        }

        /** Splits a CSV line, honouring double-quoted cells with escaped quotes. */
        private fun splitCsv(line: String, delimiter: Char): List<String> {
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == delimiter && !quoted -> { cells += cell.toString(); cell.clear() }
                    else -> cell.append(c)
                }
                i++
            }
            cells += cell.toString()
            return cells
        }
    }
}
