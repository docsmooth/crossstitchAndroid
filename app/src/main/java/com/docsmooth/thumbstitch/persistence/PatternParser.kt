package com.docsmooth.thumbstitch.persistence

import com.docsmooth.thumbstitch.domain.Pattern
import com.docsmooth.thumbstitch.domain.PatternColor
import com.docsmooth.thumbstitch.domain.PatternRow
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.FilterReader
import java.io.Reader

class InvalidPatternException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PatternParser {
    private const val MAX_DIMENSION = 4096
    private const val MAX_PALETTE = 256
    private const val MAX_RUNS = 1_000_000
    private const val MAX_CHARS = 8 * 1024 * 1024
    private const val MAX_TEXT = 256

    fun parse(source: Reader): Pattern {
        try {
            val json = JsonReader(LimitedReader(source)).apply { strictness = Strictness.STRICT }
            var schema: Int? = null
            var id: String? = null
            var revision: Int? = null
            var name: String? = null
            var width: Int? = null
            var height: Int? = null
            var palette: List<PatternColor>? = null
            var encodedRows: List<String>? = null
            json.objectFields { field ->
                when (field) {
                    "schemaVersion" -> schema = json.integer(field)
                    "id" -> id = json.text(field)
                    "revision" -> revision = json.integer(field)
                    "name" -> name = json.text(field)
                    "width" -> width = json.integer(field)
                    "height" -> height = json.integer(field)
                    "palette" -> palette = readPalette(json)
                    "rows" -> encodedRows = readRows(json)
                    else -> invalid("Unknown field: $field")
                }
            }
            if (json.peek() != JsonToken.END_DOCUMENT) invalid("Trailing JSON content")
            if (schema != 1) invalid("Unsupported or missing schemaVersion: $schema")
            val patternId = id ?: invalid("Missing id")
            val patternName = name ?: invalid("Missing name")
            val patternRevision = revision ?: invalid("Missing revision")
            if (patternRevision < 1) invalid("revision must be positive")
            val w = width ?: invalid("Missing width")
            val h = height ?: invalid("Missing height")
            if (w !in 1..MAX_DIMENSION || h !in 1..MAX_DIMENSION) {
                invalid("width and height must be between 1 and $MAX_DIMENSION")
            }
            val colors = palette ?: invalid("Missing palette")
            val rows = encodedRows ?: invalid("Missing rows")
            if (rows.size != h) invalid("Expected $h rows, found ${rows.size}")
            var runCount = 0
            val decoded = rows.mapIndexed { index, row ->
                val runs = decodeRow(row, index, w, colors.size)
                runCount += runs.size / 2
                if (runCount > MAX_RUNS) invalid("Pattern exceeds $MAX_RUNS runs")
                PatternRow(runs)
            }
            return Pattern(patternId, patternRevision, patternName, w, h, colors, decoded)
        } catch (e: InvalidPatternException) {
            throw e
        } catch (e: Exception) {
            throw InvalidPatternException("Invalid pattern JSON: ${e.message}", e)
        }
    }

    private fun readPalette(json: JsonReader): List<PatternColor> {
        val colors = mutableListOf<PatternColor>()
        val ids = mutableSetOf<String>()
        json.beginArray()
        while (json.hasNext()) {
            if (colors.size == MAX_PALETTE) invalid("Palette exceeds $MAX_PALETTE colors")
            var id: String? = null
            var name: String? = null
            var argb: String? = null
            json.objectFields { field ->
                when (field) {
                    "id" -> id = json.text(field)
                    "name" -> name = json.text(field)
                    "argb" -> argb = json.text(field)
                    else -> invalid("Unknown palette field: $field")
                }
            }
            val colorId = id ?: invalid("Missing palette id")
            val colorName = name ?: invalid("Missing palette name")
            val hex = argb ?: invalid("Missing palette argb")
            if (!Regex("#[0-9A-Fa-f]{8}").matches(hex)) invalid("Invalid ARGB for $colorId")
            if (!ids.add(colorId)) invalid("Duplicate palette id: $colorId")
            colors.add(PatternColor(colorId, colorName, hex.substring(1).toLong(16)))
        }
        json.endArray()
        return colors.toList()
    }

    private fun readRows(json: JsonReader): List<String> {
        val rows = mutableListOf<String>()
        json.beginArray()
        while (json.hasNext()) {
            if (rows.size == MAX_DIMENSION) invalid("Too many rows")
            if (json.peek() != JsonToken.STRING) invalid("Rows must be strings")
            val row = json.nextString()
            if (row.length > MAX_DIMENSION * 9) invalid("Row encoding is too long")
            rows.add(row)
        }
        json.endArray()
        return rows
    }

    private fun decodeRow(row: String, rowIndex: Int, width: Int, paletteSize: Int): IntArray {
        val runs = IntArray(width * 2)
        var count = 0
        var cells = 0
        val tokens = row.split(' ')
        for (token in tokens) {
            val parts = token.split('x')
            if (parts.size != 2 || parts.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) {
                invalid("Invalid run in row $rowIndex: $token")
            }
            val color = parts[0].toIntOrNull() ?: invalid("Invalid color index in row $rowIndex")
            val length = parts[1].toIntOrNull() ?: invalid("Invalid run length in row $rowIndex")
            if (color !in 0..paletteSize) invalid("Unknown color index $color in row $rowIndex")
            if (length < 1 || length > width - cells) invalid("Invalid run length in row $rowIndex")
            runs[count++] = color
            runs[count++] = length
            cells += length
        }
        if (cells != width) invalid("Row $rowIndex has $cells cells, expected $width")
        return runs.copyOf(count)
    }

    private inline fun JsonReader.objectFields(read: (String) -> Unit) {
        val seen = mutableSetOf<String>()
        beginObject()
        while (hasNext()) {
            val field = nextName()
            if (!seen.add(field)) invalid("Duplicate field: $field")
            read(field)
        }
        endObject()
    }

    private fun JsonReader.text(field: String): String {
        if (peek() != JsonToken.STRING) invalid("$field must be a string")
        val value = nextString()
        if (value.isBlank() || value.length > MAX_TEXT) invalid("$field must be nonblank and at most $MAX_TEXT characters")
        return value
    }

    private fun JsonReader.integer(field: String): Int {
        if (peek() != JsonToken.NUMBER) invalid("$field must be an integer")
        val value = nextString()
        if (!Regex("0|[1-9][0-9]*").matches(value)) invalid("$field must be a nonnegative integer")
        return value.toIntOrNull() ?: invalid("$field is too large")
    }

    private fun invalid(message: String): Nothing = throw InvalidPatternException(message)

    private class LimitedReader(reader: Reader) : FilterReader(reader) {
        private var count = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, minOf(length, MAX_CHARS - count + 1))
            if (read > 0) {
                count += read
                if (count > MAX_CHARS) invalid("Pattern exceeds $MAX_CHARS characters")
            }
            return read
        }
    }
}
