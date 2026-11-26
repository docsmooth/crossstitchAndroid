package com.docsmooth.thumbstitch.domain

data class PatternColor(val id: String, val name: String, val argb: Long)

class PatternRow internal constructor(private val runs: IntArray) {
    fun colorIndexAt(column: Int): Int {
        require(column >= 0)
        var end = 0
        for (i in runs.indices step 2) {
            end += runs[i + 1]
            if (column < end) return runs[i]
        }
        throw IndexOutOfBoundsException("Column $column is outside the row")
    }
}

data class Pattern(
    val id: String,
    val revision: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val palette: List<PatternColor>,
    val rows: List<PatternRow>,
)
