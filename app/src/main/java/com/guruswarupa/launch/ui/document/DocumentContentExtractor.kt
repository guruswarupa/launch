package com.guruswarupa.launch.ui.document

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.File
import java.io.FileInputStream

object DocumentContentExtractor {

    fun extractWordContent(file: File): String? {
        return try {
            if (file.extension.lowercase() == "docx") {
                extractDocxContent(file)
            } else {
                extractBinaryDocContent(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractPptContent(file: File): String? {
        return try {
            if (file.extension.lowercase() == "pptx") {
                extractPptxContent(file)
            } else {
                extractBinaryPptContent(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractExcelContent(file: File): String? {
        return try {
            if (file.extension.lowercase() == "xlsx") {
                extractXlsxContent(file)
            } else {
                extractBinaryXlsContent(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractRtfContent(file: File): String {
        val content = file.readText()
        val stripped = content
            .replace(Regex("\\\\[a-z]+\\d*\\s?"), "")
            .replace(Regex("[{}]"), "")
            .replace(Regex("\\\\"), "")
            .trim()

        val paragraphs = stripped.split("\n").filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${escapeHtml(it.trim())}</p>" }

        return wrapInHtml("Rich Text", paragraphs, getDocTypeColor("rtf"))
    }

    fun buildTextFileHtml(content: String, extension: String): String {
        val isCode = extension in listOf(
            "json", "xml", "csv", "html", "htm", "css", "js", "kt", "java",
            "py", "c", "cpp", "h", "yaml", "yml", "toml", "ini", "cfg", "conf",
            "sh", "bat", "ps1", "rb", "go", "rs", "swift", "dart", "ts",
            "tsx", "jsx", "vue", "sql", "md"
        )

        val htmlBody = if (isCode) {
            "<pre><code>${escapeHtml(content)}</code></pre>"
        } else {
            content.split("\n").joinToString("\n") {
                if (it.isBlank()) "<br/>" else "<p>${escapeHtml(it)}</p>"
            }
        }

        val accentColor = if (isCode) "#A3BE8C" else "#88C0D0"
        val extraCss = if (isCode) {
            """
                body {
                    background: #FFFFFF !important;
                    color: #1a1a1a;
                }
                pre {
                    background: #FFFFFF;
                    border: 1px solid #d8dee9;
                    border-radius: 0;
                    padding: 20px;
                    overflow-x: auto;
                    -webkit-overflow-scrolling: touch;
                    font-size: 13px;
                    line-height: 1.6;
                    box-shadow: none;
                }
                code {
                    font-family: 'monospace';
                    color: #1a1a1a;
                    white-space: pre;
                }
            """.trimIndent()
        } else {
            """
                body {
                    background: #FFFFFF !important;
                    color: #1a1a1a;
                }
                p {
                    color: #1a1a1a;
                }
            """.trimIndent()
        }

        return wrapInHtml(
            ".${extension.uppercase()} File",
            htmlBody,
            accentColor,
            extraCss
        )
    }

    private fun extractDocxContent(file: File): String {
        FileInputStream(file).use { fis ->
            XWPFDocument(fis).use { document ->
                val blocks = mutableListOf<String>()

                document.bodyElements.forEach { element ->
                    when (element) {
                        is XWPFParagraph -> processParagraph(element, blocks)
                        is XWPFTable -> processTable(element, blocks)
                    }
                }

                document.tables.forEach { table ->
                    processTable(table, blocks)
                }

                return wrapInHtml(
                    "Word Document",
                    blocks.joinToString("\n"),
                    getDocTypeColor("docx"),
                    extraCss = """
                        body {
                            background: #F0F2F5 !important;
                        }
                        .content {
                            max-width: 816px;
                            margin: 24px auto;
                            background: #FFFFFF;
                            padding: 96px 72px;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                            border-radius: 0;
                            min-height: 1056px;
                        }
                        .doc-image {
                            display: block;
                            width: auto;
                            max-width: 100%;
                            max-height: 520px;
                            margin: 24px auto;
                            border-radius: 0;
                            border: none;
                            background: #FFFFFF;
                            padding: 0;
                            object-fit: contain;
                        }
                        .doc-table {
                            width: 100%;
                            border-collapse: collapse;
                            margin: 16px 0;
                        }
                        .doc-table th, .doc-table td {
                            border: 1px solid #000;
                            padding: 8px;
                        }
                        .doc-table th {
                            background-color: #f0f0f0;
                            font-weight: bold;
                        }
                    """.trimIndent()
                )
            }
        }
    }

    private fun processParagraph(para: XWPFParagraph, blocks: MutableList<String>) {
        val textBuilder = StringBuilder()
        var headingLevel = 0

        val styleID = para.styleID
        if (styleID != null) {
            if (styleID.contains("Heading1", ignoreCase = true) || styleID.contains("Heading 1", ignoreCase = true)) headingLevel = 1
            else if (styleID.contains("Heading2", ignoreCase = true) || styleID.contains("Heading 2", ignoreCase = true)) headingLevel = 2
            else if (styleID.contains("Heading3", ignoreCase = true) || styleID.contains("Heading 3", ignoreCase = true)) headingLevel = 3
        }

        para.runs.forEach { run ->
            val text = run.getText(0) ?: return@forEach
            val isBold = run.isBold || run.isEmbossed
            val isItalic = run.isItalic
            val isUnderline = run.underline != UnderlinePatterns.NONE

            val formattedText = buildString {
                if (isBold) append("<strong>")
                if (isItalic) append("<em>")
                if (isUnderline) append("<u>")
                append(escapeHtml(text))
                if (isUnderline) append("</u>")
                if (isItalic) append("</em>")
                if (isBold) append("</strong>")
            }

            textBuilder.append(formattedText)
        }

        if (textBuilder.isNotEmpty()) {
            val paragraphHtml = when (headingLevel) {
                1 -> "<h1>$textBuilder</h1>"
                2 -> "<h2>$textBuilder</h2>"
                3 -> "<h3>$textBuilder</h3>"
                else -> "<p>$textBuilder</p>"
            }
            blocks.add(paragraphHtml)
        } else {
            blocks.add("<br/>")
        }
    }

    private fun processTable(table: XWPFTable, blocks: MutableList<String>) {
        val tableHtml = StringBuilder("<table class=\"doc-table\">")

        table.rows.forEachIndexed { rowIndex, row ->
            tableHtml.append("<tr>")
            row.tableCells.forEach { cell ->
                val tag = if (rowIndex == 0) "th" else "td"
                val cellText = cell.text.replace("\n", "<br/>")
                tableHtml.append("<$tag>${escapeHtml(cellText)}</$tag>")
            }
            tableHtml.append("</tr>")
        }

        tableHtml.append("</table>")
        blocks.add(tableHtml.toString())
    }

    private fun extractBinaryDocContent(file: File): String {
        return try {
            FileInputStream(file).use { fis ->
                HWPFDocument(fis).use { doc ->
                    val range = doc.range
                    val paragraphs = mutableListOf<String>()

                    for (i in 0 until range.numParagraphs()) {
                        val para = range.getParagraph(i)
                        val text = para.text().trim()
                        if (text.isNotEmpty()) {
                            val isHeading = para.getLvl() <= 0 && text.length < 100
                            val htmlText = escapeHtml(text)
                            paragraphs.add(
                                if (isHeading && text.length < 50) "<h2>$htmlText</h2>"
                                else "<p>$htmlText</p>"
                            )
                        }
                    }

                    wrapInHtml("Word Document", paragraphs.joinToString("\n"), getDocTypeColor("doc"))
                }
            }
        } catch (_: Exception) {
            fallbackBinaryDocExtraction(file)
        }
    }

    private fun fallbackBinaryDocExtraction(file: File): String {
        val bytes = file.readBytes()
        val text = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i]
            val ch = b.toInt().toChar()
            if (ch.isLetterOrDigit() || ch.isWhitespace() || ch in ".,;:!?()-'\"@#\$%&*/+=<>[]{}|~`^_") {
                text.append(ch)
            } else if (b.toInt() == 0 && i + 1 < bytes.size) {
                i++
                continue
            }
            i++
        }

        val cleanedText = text.toString()
            .replace(Regex("\\s{3,}"), "\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        val paragraphs = cleanedText.split("\n\n").joinToString("\n") {
            "<p>${escapeHtml(it.trim())}</p>"
        }

        return wrapInHtml("Word Document", paragraphs, getDocTypeColor("doc"))
    }

    private fun extractPptxContent(file: File): String {
        FileInputStream(file).use { fis ->
            XMLSlideShow(fis).use { pptx ->
                val slides = mutableListOf<String>()

                pptx.slides.forEach { slide ->
                    val slideHtml = buildString {
                        append("<div class='slide'>")
                        append("<div class='slide-content'>")

                        var hasTitle = false
                        slide.shapes.forEach { shape ->
                            if (shape is XSLFTextShape) {
                                val text = shape.text
                                if (text.isNotBlank()) {
                                    if (!hasTitle) {
                                        append("<h3>${escapeHtml(text)}</h3>")
                                        hasTitle = true
                                    } else {
                                        text.split("\n")
                                            .filter { it.isNotBlank() }
                                            .forEach { para ->
                                                append("<p>${escapeHtml(para)}</p>")
                                            }
                                    }
                                }
                            }
                        }

                        if (!hasTitle && slide.shapes.isEmpty()) {
                            append("<p class='empty'>(No text content)</p>")
                        }

                        append("</div></div>")
                    }

                    slides.add(slideHtml)
                }

                val content = if (slides.isEmpty()) {
                    "<p>No slides found in this presentation</p>"
                } else {
                    slides.joinToString("\n")
                }

                return wrapInHtml(
                    "Presentation • ${slides.size} slides",
                    content,
                    getDocTypeColor("pptx"),
                    extraCss = """
                        body {
                            background: #F0F2F5 !important;
                        }
                        .content {
                            max-width: 980px;
                            margin: 24px auto;
                            background: transparent;
                            padding: 0;
                        }
                        .slide { 
                            margin: 0 0 32px 0;
                            page-break-inside: avoid;
                        }
                        .slide-header { 
                            color: #555; 
                            font-size: 11px; 
                            font-weight: 700;
                            text-transform: uppercase;
                            letter-spacing: 1px;
                            margin-bottom: 8px;
                            opacity: 0.9;
                        }
                        .slide-content {
                            background: #FFFFFF;
                            border: 1px solid rgba(0,0,0,0.1);
                            border-radius: 0;
                            padding: 48px;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.12);
                            min-height: 400px;
                        }
                        .slide-content h3 {
                            color: #1a1a1a;
                            margin: 0 0 20px 0;
                            font-size: 24px;
                            border-bottom: 2px solid #f0f0f0;
                            padding-bottom: 12px;
                            font-weight: 600;
                        }
                        .slide-content p { 
                            margin: 10px 0; 
                            line-height: 1.6; 
                            color: #333;
                            font-size: 16px;
                        }
                        .slide-images { 
                            margin-top: 20px; 
                            display: grid; 
                            gap: 16px; 
                        }
                        .slide-image {
                            display: block;
                            width: auto;
                            max-width: 100%;
                            max-height: 420px;
                            border-radius: 0;
                            border: none;
                            background: #FFFFFF;
                            padding: 0;
                            object-fit: contain;
                            margin: 0 auto;
                        }
                        .empty { opacity: 0.5; font-style: italic; color: #666; }
                    """.trimIndent()
                )
            }
        }
    }

    private fun extractBinaryPptContent(file: File): String {
        return try {
            FileInputStream(file).use { fis ->
                @Suppress("DEPRECATION")
                HSLFSlideShow(fis).use { ppt ->
                    val slides = mutableListOf<String>()

                    ppt.slides.forEachIndexed { index, slide ->
                        val slideNum = index + 1
                        val texts = mutableListOf<String>()

                        slide.shapes.forEach { shape ->
                            if (shape is HSLFTextShape) {
                                val text = shape.text.trim()
                                if (text.isNotEmpty()) {
                                    texts.add(text)
                                }
                            }
                        }

                        val slideHtml = buildString {
                            append("<div class='slide'>")
                            append("<div class='slide-header'>Slide $slideNum</div>")
                            append("<div class='slide-content'>")

                            if (texts.isNotEmpty()) {
                                append("<h3>${escapeHtml(texts[0])}</h3>")
                                texts.drop(1).forEach { text ->
                                    append("<p>${escapeHtml(text)}</p>")
                                }
                            } else {
                                append("<p class='empty'>(No text content)</p>")
                            }

                            append("</div></div>")
                        }

                        slides.add(slideHtml)
                    }

                    val content = if (slides.isEmpty()) {
                        "<p>No slides found in this presentation</p>"
                    } else {
                        slides.joinToString("\n")
                    }

                    wrapInHtml("Presentation", content, getDocTypeColor("ppt"))
                }
            }
        } catch (_: Exception) {
            fallbackBinaryPptExtraction(file)
        }
    }

    private fun fallbackBinaryPptExtraction(file: File): String {
        val bytes = file.readBytes()
        val text = StringBuilder()
        for (b in bytes) {
            val ch = b.toInt().toChar()
            if (ch.isLetterOrDigit() || ch.isWhitespace() || ch in ".,;:!?()-'\"") {
                text.append(ch)
            }
        }
        val cleanedText = text.toString()
            .replace(Regex("\\s{3,}"), "\n\n")
            .trim()

        val paragraphs = cleanedText.split("\n\n").filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${escapeHtml(it.trim())}</p>" }

        return wrapInHtml("Presentation", paragraphs, getDocTypeColor("ppt"))
    }

    private fun extractXlsxContent(file: File): String {
        FileInputStream(file).use { fis ->
            WorkbookFactory.create(fis).use { workbook ->
                val sheets = mutableListOf<String>()

                for (i in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(i)
                    if (sheet.physicalNumberOfRows > 0) {
                        val tableHtml = buildString {
                            append("<div class='sheet-label'>${sheet.sheetName ?: "Sheet ${i + 1}"}</div>")
                            append("<div class='table-wrapper'><table>")

                            var isFirstRow = true
                            for (row in sheet) {
                                if (row.physicalNumberOfCells == 0) continue

                                append("<tr>")
                                val tag = if (isFirstRow) "th" else "td"
                                var lastCellNum = -1
                                for (cell in row) {
                                    val currentCellNum = cell.columnIndex
                                    if (lastCellNum >= 0 && currentCellNum > lastCellNum + 1) {
                                        repeat(currentCellNum - lastCellNum - 1) {
                                            append("<$tag></$tag>")
                                        }
                                    }

                                    val cellValue = getCellValueAsString(cell)
                                    append("<$tag>${escapeHtml(cellValue)}</$tag>")
                                    lastCellNum = cell.columnIndex
                                }

                                append("</tr>")
                                isFirstRow = false
                            }

                            append("</table></div>")
                        }
                        sheets.add(tableHtml)
                    }
                }

                val content = if (sheets.isEmpty()) {
                    "<p>No data found in this spreadsheet</p>"
                } else {
                    sheets.joinToString("\n")
                }

                return wrapInHtml(
                    "Spreadsheet",
                    content,
                    getDocTypeColor("xlsx"),
                    extraCss = """
                        body {
                            background: #F0F2F5 !important;
                        }
                        .content {
                            max-width: 980px;
                            margin: 24px auto;
                            background: transparent;
                            padding: 0;
                        }
                        .sheet-card {
                            margin: 0 0 32px 0;
                            padding: 0;
                            border-radius: 0;
                            background: transparent;
                        }
                        .sheet-label {
                            color: #555; 
                            font-size: 11px;
                            font-weight: 700;
                            text-transform: uppercase;
                            letter-spacing: 1px;
                            margin: 0 0 12px;
                            opacity: 0.9;
                        }
                        .table-wrapper {
                            overflow-x: auto;
                            -webkit-overflow-scrolling: touch;
                            margin-bottom: 16px;
                            border-radius: 0;
                            border: 1px solid rgba(0,0,0,0.1);
                            background: #FFFFFF;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.12);
                        }
                        table {
                            border-collapse: collapse;
                            width: auto;
                            min-width: 100%;
                            font-size: 14px;
                        }
                        th, td {
                            border: 1px solid #e0e0e0;
                            padding: 12px 16px;
                            text-align: left;
                            white-space: nowrap;
                            color: #333;
                        }
                        th {
                            background: #f5f5f5;
                            font-weight: 700;
                            color: #1a1a1a;
                            position: sticky;
                            top: 0;
                            z-index: 10;
                            border-top: 2px solid #ddd;
                        }
                        tr:nth-child(even) td { background: #fafafa; }
                        tr:hover td { background: #f0f7ff; }
                    """.trimIndent()
                )
            }
        }
    }

    private fun getCellValueAsString(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue.toString()
                } catch (_: Exception) {
                    cell.stringCellValue
                }
            }
            CellType.BLANK -> ""
            else -> ""
        }
    }

    private fun extractBinaryXlsContent(file: File): String {
        return try {
            FileInputStream(file).use { fis ->
                @Suppress("DEPRECATION")
                HSSFWorkbook(fis).use { workbook ->
                    val sheets = mutableListOf<String>()

                    for (i in 0 until workbook.numberOfSheets) {
                        val sheet = workbook.getSheetAt(i)
                        if (sheet.physicalNumberOfRows > 0) {
                            val tableHtml = buildString {
                                append("<div class='sheet-label'>${sheet.sheetName ?: "Sheet ${i + 1}"}</div>")
                                append("<div class='table-wrapper'><table>")

                                var isFirstRow = true
                                for (rowIndex in 0 until sheet.lastRowNum + 1) {
                                    val row = sheet.getRow(rowIndex)
                                    if (row == null || row.physicalNumberOfCells == 0) continue

                                    append("<tr>")
                                    val tag = if (isFirstRow) "th" else "td"

                                    for (cellIndex in 0 until row.lastCellNum) {
                                        val cell = row.getCell(cellIndex.toInt())
                                        val cellValue = if (cell != null) getCellValueAsString(cell) else ""
                                        append("<$tag>${escapeHtml(cellValue)}</$tag>")
                                    }

                                    append("</tr>")
                                    isFirstRow = false
                                }

                                append("</table></div>")
                            }
                            sheets.add(tableHtml)
                        }
                    }

                    val content = if (sheets.isEmpty()) {
                        "<p>No data found in this spreadsheet</p>"
                    } else {
                        sheets.joinToString("\n")
                    }

                    wrapInHtml("Spreadsheet", content, getDocTypeColor("xls"))
                }
            }
        } catch (_: Exception) {
            fallbackBinaryXlsExtraction(file)
        }
    }

    private fun fallbackBinaryXlsExtraction(file: File): String {
        val bytes = file.readBytes()
        val text = StringBuilder()
        for (b in bytes) {
            val ch = b.toInt().toChar()
            if (ch.isLetterOrDigit() || ch.isWhitespace() || ch in ".,;:!?()-'\"") {
                text.append(ch)
            }
        }
        val cleanedText = text.toString()
            .replace(Regex("\\s{3,}"), "\n\n")
            .trim()

        val paragraphs = cleanedText.split("\n\n").filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${escapeHtml(it.trim())}</p>" }

        return wrapInHtml("Spreadsheet", paragraphs, getDocTypeColor("xls"))
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun getDocTypeColor(extension: String): String {
        return when (extension.lowercase()) {
            "doc", "docx" -> "#81a1c1"
            "ppt", "pptx" -> "#d08770"
            "xls", "xlsx" -> "#a3be8c"
            "pdf" -> "#bf616a"
            "rtf" -> "#b48ead"
            else -> "#88c0d0"
        }
    }

    private fun wrapInHtml(docType: String, body: String, accentColor: String, extraCss: String = ""): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
            <style>
                :root {
                    --accent: $accentColor;
                    --text-secondary: #333333;
                    --text-primary: #1a1a1a;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    background: transparent;
                    color: var(--text-secondary);
                    padding: 24px 20px;
                    line-height: 1.7;
                    font-size: 15px;
                    -webkit-text-size-adjust: 100%;
                }
                .doc-badge {
                    display: inline-block;
                    background: var(--accent)22;
                    border: 1px solid var(--accent)44;
                    color: var(--accent);
                    padding: 5px 14px;
                    border-radius: 6px;
                    font-size: 11px;
                    font-weight: 700;
                    text-transform: uppercase;
                    letter-spacing: 1px;
                    margin-bottom: 24px;
                }
                h1 { font-size: 24px; color: var(--text-primary); margin: 24px 0 16px; font-weight: 700; }
                h2 { font-size: 20px; color: var(--text-primary); margin: 20px 0 12px; font-weight: 600; }
                h3 { font-size: 18px; color: var(--text-primary); margin: 16px 0 10px; font-weight: 600; }
                p { margin-bottom: 14px; }
                strong { color: var(--text-primary); font-weight: 600; }
                img {
                    display: block;
                    max-width: 100%;
                    height: auto;
                }
                $extraCss
            </style>
        </head>
        <body>
            <span class="doc-badge">$docType</span>
            $body
            <div style="height: 100px;"></div>
        </body>
        </html>
        """.trimIndent()
    }
}
