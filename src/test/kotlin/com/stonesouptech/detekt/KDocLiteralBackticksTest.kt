package com.stonesouptech.detekt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KDocLiteralBackticksTest {
    @Test fun `reports each standalone literal including tag descriptions`() {
        val findings = KDocLiteralBackticks(TestConfig()).lint("""
            /**
             * Returns true, false, or null.
             * @return null when absent
             */
            fun value() = true
        """)
        assertEquals(4, findings.size)
    }

    @Test fun `ignores code links URLs identifiers strings and ordinary comments`() {
        assertTrue(KDocLiteralBackticks(TestConfig()).lint("""
            /**
             * `true` and ``false or `null` ``.
             * [true] [label](https://example.com/null) https://example.com/false
             * trueValue false_positive nullable True FALSE NULL étrue trueé
             * ```kotlin
             * true false null
             * ```
             * ~~~
             * true false null
             * ~~~
             */
            fun value() = "true false null"
            // true false null
            /* true false null */
        """).isEmpty())
    }

    @Test fun `checks link labels and resumes after fences with CRLF`() {
        assertEquals(2, KDocLiteralBackticks(TestConfig()).lint("""
            /**
             * [true](https://example.com/null)
             * ```
             * false
             * ```
             * null
             */
            val value = false
        """.trimIndent().replace("\n", "\r\n")).size)
    }

    @Test fun `standard suppression works`() {
        assertTrue(KDocLiteralBackticks(TestConfig()).lint("""
            /** true false null */
            @Suppress("KDocLiteralBackticks")
            fun value() = true
        """).isEmpty())
    }
}
