package com.guruswarupa.launch.ui.document

import java.io.File

object DocumentContentExtractor {

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

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
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
