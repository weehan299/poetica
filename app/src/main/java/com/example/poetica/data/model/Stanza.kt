package com.example.poetica.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stanza(
    val lines: List<String>,
    val indentation: List<Int> = emptyList(),
    val emphasis: List<Boolean> = emptyList()
) {
    fun getFormattedLines(): List<FormattedLine> {
        return lines.mapIndexed { index, line ->
            FormattedLine(
                text = line,
                indentLevel = indentation.getOrElse(index) { 0 },
                isEmphasized = emphasis.getOrElse(index) { false }
            )
        }
    }
}

data class FormattedLine(
    val text: String,
    val indentLevel: Int = 0,
    val isEmphasized: Boolean = false
)