package com.docsmooth.thumbstitch.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PatternParserTest {
    private val valid = """
        {
          "schemaVersion": 1, "id": "test", "revision": 1, "name": "Test",
          "width": 4, "height": 2,
          "palette": [{"id":"red","name":"Red","argb":"#FFE53935"}],
          "rows": ["0x4", "0x1 1x2 0x1"]
        }
    """.trimIndent()

    @Test
    fun parsesRowsAndPalette() {
        val pattern = PatternParser.parse(valid.reader())
        assertEquals("test", pattern.id)
        assertEquals(1, pattern.revision)
        assertEquals(0xFFE53935L, pattern.palette.single().argb)
        assertEquals(listOf(0, 0, 0, 0), (0..3).map(pattern.rows[0]::colorIndexAt))
        assertEquals(listOf(0, 1, 1, 0), (0..3).map(pattern.rows[1]::colorIndexAt))
    }

    @Test
    fun loadsActualHeartAsset() {
        val asset = File("src/main/assets/patterns/heart.json")
        val pattern = asset.reader().use(PatternParser::parse)
        assertEquals(20, pattern.width)
        assertEquals(20, pattern.height)
        assertEquals(0, pattern.rows[0].colorIndexAt(10))
        assertEquals(1, pattern.rows[5].colorIndexAt(10))
        assertEquals(1, pattern.rows[16].colorIndexAt(9))
        assertEquals(0, pattern.rows[19].colorIndexAt(10))
    }

    @Test
    fun rejectsInvalidPatterns() {
        val cases = listOf(
            valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            valid.replace("\"revision\": 1", "\"revision\": 0"),
            valid.replace("\"width\": 4", "\"width\": 4097"),
            valid.replace("\"height\": 2", "\"height\": 1"),
            valid.replace("0x4", "0x3"),
            valid.replace("0x4", "0x5"),
            valid.replace("0x4", "0x0"),
            valid.replace("0x4", "2x4"),
            valid.replace("0x4", "0x-4"),
            valid.replace("0x4", "0x999999999999"),
            valid.replace("0x4", "0x1  0x3"),
            valid.replace("#FFE53935", "#XYZ"),
            valid.replace("\"id\":\"red\"", "\"id\":\"red\",\"id\":\"red\""),
            valid.replace("\"width\": 4", "\"width\": 4, \"width\": 4"),
            valid.replace("\"width\": 4", "\"width\": \"4\""),
            valid.replace("\"width\": 4", "\"width\": 4.0"),
            valid.replace("\"name\": \"Test\"", "\"name\": \" \""),
            valid.replace("\"rows\":", "\"extra\": 1, \"rows\":"),
            valid.dropLast(1),
            "$valid {}",
            valid.replace("[{\"id\":\"red\",\"name\":\"Red\",\"argb\":\"#FFE53935\"}]",
                "[{\"id\":\"red\",\"name\":\"Red\",\"argb\":\"#FFE53935\"},{\"id\":\"red\",\"name\":\"Other\",\"argb\":\"#FF000000\"}]"),
        )
        cases.forEach { input ->
            try {
                PatternParser.parse(input.reader())
                fail("Expected invalid pattern: $input")
            } catch (expected: InvalidPatternException) {
                assertTrue(expected.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun supportsLargeSparseRowsWithoutExpandingCells() {
        val json = valid.replace("\"width\": 4", "\"width\": 4096")
            .replace("\"height\": 2", "\"height\": 1")
            .replace("[\"0x4\", \"0x1 1x2 0x1\"]", "[\"0x4095 1x1\"]")
        val pattern = PatternParser.parse(json.reader())
        assertEquals(0, pattern.rows.single().colorIndexAt(4094))
        assertEquals(1, pattern.rows.single().colorIndexAt(4095))
    }

    @Test
    fun rejectsOversizedInputAndRows() {
        val oversizedInputs = listOf(
            " ".repeat(8 * 1024 * 1024) + valid,
            valid.replace("0x4", "0".repeat(4096 * 9) + "x4"),
        )
        oversizedInputs.forEach { input ->
            try {
                PatternParser.parse(input.reader())
                fail("Expected size limit failure")
            } catch (expected: InvalidPatternException) {
                assertTrue(expected.message!!.contains("too long") || expected.message!!.contains("exceeds"))
            }
        }
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun rejectsColumnOutsideRow() {
        PatternParser.parse(valid.reader()).rows[0].colorIndexAt(4)
    }
}
