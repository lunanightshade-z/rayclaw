package com.rayclaw.app.ui

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object MarkdownRenderer {
    private val extensions = listOf(TablesExtension.create())

    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .softbreak("<br />\n")
        .build()

    fun buildHtmlDocument(markdown: String): String {
        val body = if (markdown.isBlank()) {
            "<div class=\"placeholder\">等待 AI 回复…</div>"
        } else {
            renderer.render(parser.parse(markdown))
        }

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="utf-8" />
                <meta
                    name="viewport"
                    content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"
                />
                <link rel="stylesheet" href="markdown.css" />
            </head>
            <body>
                <div class="markdown-shell">
                    $body
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
