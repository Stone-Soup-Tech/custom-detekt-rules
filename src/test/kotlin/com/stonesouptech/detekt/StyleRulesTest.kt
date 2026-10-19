package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

class StyleRulesTest {
    @Test fun `KDoc requires spacing only after first constructor property`() {
        val code = """
            data class Model(
                /** first */ val first: String,
                /** second */ val second: String,

                /** third */ val third: String,
            )
        """
        val findings = BlankLineBeforeDocumentedProperty(TestConfig()).lint(code)
        assertEquals(1, findings.size)
        assertEquals(3, findings.single().entity.location.source.line)
    }

    @Test fun `undocumented first property still counts and annotations do not hide KDoc`() {
        assertEquals(1, BlankLineBeforeDocumentedProperty(TestConfig()).lint("""
            data class Model(
                val first: String,
                /** second */
                @Deprecated("old")
                val second:
                    String,
            )
        """).size)
    }

    @Test fun `multiline declarations and CRLF spacing are handled as PSI`() {
        val code = """
            data class Model(
                /** first */
                @Deprecated("old")
                val first: List<String> = listOf(
                    "a",
                    "b",
                ),
                
                /** second */
                @Deprecated("old")
                val second: String,
            )
        """.trimIndent().replace("\n", "\r\n")
        assertTrue(BlankLineBeforeDocumentedProperty(TestConfig()).lint(code).isEmpty())
    }

    @Test fun `ordinary classes and body properties are excluded`() {
        assertTrue(BlankLineBeforeDocumentedProperty(TestConfig()).lint("""
            class Model(/** first */ val a: Int, /** second */ val b: Int)
            data class Other(/** first */ val a: Int) {
                /** body */ val b = 2
            }
        """).isEmpty())
    }

    @Test fun `comments cannot supply blank lines before KDoc`() {
        assertEquals(1, BlankLineBeforeDocumentedProperty(TestConfig()).lint("""
            data class Model(
                val first: String,
                /* comment

                   still comment */
                /** second */ val second: String,
            )
        """).size)
    }

    @Test fun `force unwrap is reported while strings and comments are ignored`() {
        assertEquals(2, NoForceUnwrap(TestConfig()).lint("""
            fun f(a: String?, b: String?) {
                val text = "!!"
                // !!
                a!!.length
                b!!.length
            }
        """).size)
        assertTrue(NoForceUnwrap(TestConfig()).lint("""
            fun f(a: String?) {
                a?.let { println(it) }
                val b = a ?: return
                println(b)
            }
        """).isEmpty())
    }

    @Test fun `blank lines between when branches are rejected but branch contents are allowed`() {
        assertEquals(1, NoBlankLinesBetweenWhenBranches(TestConfig()).lint("""
            fun f(x: Int) = when (x) {
                1 -> {
                    println(x)

                    x
                }

                2 -> 2
                else -> 3
            }
        """).size)
        assertTrue(NoBlankLinesBetweenWhenBranches(TestConfig()).lint("""
            fun f(x: Int) = when (x) {
                1 -> 1
                // explanation
                else -> 2
            }
        """).isEmpty())
    }

    @Test fun `blank lines around branch comments and in nested when are checked`() {
        assertEquals(2, NoBlankLinesBetweenWhenBranches(TestConfig()).lint("""
            fun f(x: Int) = when (x) {
                1 -> when (x) {
                    1 -> 2

                    else -> 3
                }
                // comment

                else -> 4
            }
        """).size)
    }

    @Test fun `multiline if and else branches require braces`() {
        assertEquals(2, BracesRequired(TestConfig()).lint("""
            fun f(x: Boolean) {
                if (x)
                    println(1)
                else
                    println(2)
            }
        """).size)
        assertTrue(BracesRequired(TestConfig()).lint("""
            fun f(x: Boolean) {
                if (x) {
                    println(1)
                } else if (!x) {
                    println(2)
                } else {
                    println(3)
                }
            }
        """).isEmpty())
    }

    @Test fun `single-line conditionals are allowed in any expression context`() {
        assertTrue(BracesRequired(TestConfig()).lint("""
            fun f(x: Boolean): Int {
                if (x) println(1)
                if (x) println(1) else println(2)
                val a = if (x) 1 else 2
                var b: Int
                b = (if (x) 3 else 4)
                val c =
                    if (x) 5 else 6
                return if (x) 7 else 8
            }
        """).isEmpty())
    }

    @Test fun `single-line conditionals may transfer control`() {
        assertTrue(BracesRequired(TestConfig()).lint("""
            fun f(x: Boolean) {
                if (x) return
                if (x) throw IllegalStateException()
                val a = if (x) 1 else return
                while (x) {
                    if (x) break
                    if (!x) continue
                }
            }
            fun callback() {
                rememberLauncherForActivityResult {
                    if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
                }
            }
        """).isEmpty())
    }

    @Test fun `multiline conditions and assignments still require braces`() {
        assertEquals(4, BracesRequired(TestConfig()).lint("""
            fun f(x: Boolean): Int {
                val a = if (x)
                    1
                else 2
                return if (
                    x
                ) 3 else 4
            }
        """).size)
    }

    @Test fun `single-line nested conditionals are allowed but multiline outer bodies need braces`() {
        assertTrue(BracesRequired(TestConfig()).lint("""
            fun f(a: Boolean, b: Boolean) { if (a) if (b) println(1) else println(2) }
        """).isEmpty())
        assertEquals(1, BracesRequired(TestConfig()).lint("""
            fun f(a: Boolean, b: Boolean) {
                if (a)
                    if (b) println(1) else println(2)
            }
        """).size)
    }

    @Test fun `an else on another line makes the whole conditional multiline`() {
        assertEquals(2, BracesRequired(TestConfig()).lint("""
            fun f(x: Boolean) {
                if (x) println(1)
                else println(2)
            }
        """.trimIndent().replace("\n", "\r\n")).size)
    }

    @Test fun `standard Detekt suppression is respected`() {
        assertTrue(NoForceUnwrap(TestConfig()).lint("""
            @Suppress("NoForceUnwrap")
            fun f(a: String?) = a!!.length
        """).isEmpty())
    }

    @Test fun `additional init blocks are reported independently per class`() {
        assertEquals(3, SingleInitBlock(TestConfig()).lint("""
            class A {
                init { println(1) }
                init { println(2) }
                init { println(3) }
                class B {
                    init { println(1) }
                    init { println(2) }
                }
            }
        """).size)
        assertTrue(SingleInitBlock(TestConfig()).lint("""
            class A { init { println(1) }; class B { init { println(2) } } }
            object C { init { println(3) } }
        """).isEmpty())
    }

    @Test fun `visibility includes implicit public and treats internal after public`() {
        assertTrue(MemberVisibilityOrder(TestConfig()).lint("""
            open class A {
                val a = 1
                public fun a() = Unit
                internal val b = 2
                protected fun c() = Unit
                private val d = 4
            }
        """).isEmpty())
        assertEquals(3, MemberVisibilityOrder(TestConfig()).lint("""
            open class A {
                private val a = 1
                fun b() = Unit
                protected val c = 3
                internal fun d() = Unit
            }
        """).size)
    }

    @Test fun `constructor properties participate and nested classes reset order`() {
        assertEquals(1, MemberVisibilityOrder(TestConfig()).lint("""
            class A(private val a: Int) {
                fun publicMember() = Unit
                class B { val publicMember = 1; private val privateMember = 2 }
            }
        """).size)
    }

    @Test fun `provider is discoverable and registers all rules with distinct IDs`() {
        val provider = ServiceLoader.load(RuleSetProvider::class.java).filterIsInstance<AndroidConventionRuleSetProvider>().single()
        assertEquals("android-conventions", provider.ruleSetId)
        val rules = provider.instance(Config.empty).rules
        assertEquals(12, rules.size)
        assertEquals(12, rules.map { it.ruleId }.distinct().size)
        assertTrue(rules.any { it is WhenBranchBracesRequired })
    }
}
