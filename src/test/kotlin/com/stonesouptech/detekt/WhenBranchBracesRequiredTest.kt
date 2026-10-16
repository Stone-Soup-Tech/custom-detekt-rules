package com.stonesouptech.detekt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WhenBranchBracesRequiredTest {
    @Test fun `shared options only require braces when the result wraps`() {
        val code = """
            fun preview(fileType: FileType) = when (fileType) {
                FileType.PNG,
                FileType.JPEG -> "Image"
                FileType.CSV,
                FileType.TSV ->
                    buildTablePreview(file)
                FileType.TEXT -> "Plain text"
            }
        """.trimIndent()
        val findings = WhenBranchBracesRequired(TestConfig()).lint(code)
        assertEquals(1, findings.size)
        assertEquals(6, findings.single().entity.location.source.line)
        assertTrue(WhenBranchBracesRequired(TestConfig()).lint(code.replace(
            "->\n        buildTablePreview(file)", "-> \"Table\""
        )).isEmpty())
    }

    @Test fun `multiline calls and else bodies require braces`() {
        assertEquals(2, WhenBranchBracesRequired(TestConfig()).lint("""
            fun f(x: Int) = when (x) {
                1 -> format(
                    x
                )
                else ->
                    x
            }
        """).size)
    }

    @Test fun `braced bodies and multiline conditions with inline results are allowed`() {
        assertTrue(WhenBranchBracesRequired(TestConfig()).lint("""
            fun f(x: Int) = when {
                x == 1 ||
                    x == 2 -> x
                x == 3 -> {
                    format(
                        x
                    )
                }
                else ->
                    {
                        x
                    }
            }
        """).isEmpty())
    }

    @Test fun `comments between arrow and body and CRLF are checked`() {
        assertEquals(1, WhenBranchBracesRequired(TestConfig()).lint("""
            fun f(x: Int) = when (x) {
                1 -> // explanation
                    x
                else -> /* inline comment */ x
            }
        """.trimIndent().replace("\n", "\r\n")).size)
    }

    @Test fun `nested branches are visited independently`() {
        assertEquals(1, WhenBranchBracesRequired(TestConfig()).lint("""
            fun f(x: Int) = when (x) {
                1 -> {
                    when (x) {
                        else ->
                            x
                    }
                }
                else -> x
            }
        """).size)
    }

    @Test fun `standard suppression is respected`() {
        assertTrue(WhenBranchBracesRequired(TestConfig()).lint("""
            @Suppress("WhenBranchBracesRequired")
            fun f(x: Int) = when (x) {
                else ->
                    x
            }
        """).isEmpty())
    }
}
