package com.yasin.vcardly.core.export

/** Layout-neutral description of a PDF report. The Android renderer turns this into pages. */
sealed interface PdfBlock {
    data class Heading(val text: String) : PdfBlock
    data class Paragraph(val text: String) : PdfBlock
    data class KeyValues(val rows: List<Pair<String, String>>) : PdfBlock
    /** [fraction] is 0..1 of the largest bar. */
    data class Bars(val rows: List<BarRow>) : PdfBlock
    /** Column [weights] are relative widths. */
    data class TableBlock(val header: List<String>, val rows: List<List<String>>, val weights: List<Float>) : PdfBlock
}

data class BarRow(val label: String, val valueText: String, val fraction: Float)

data class PdfDoc(val title: String, val subtitle: String, val blocks: List<PdfBlock>)
