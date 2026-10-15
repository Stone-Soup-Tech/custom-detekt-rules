package com.stonesouptech.detekt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NamedArgumentsForLargeCallsTest {
    private fun lint(code: String) = NamedArgumentsForLargeCalls(TestConfig()).lint(code, resolve = true)

    @Test fun `functions and methods with three supplied parameters require names`() {
        val findings = lint("""
            fun top(first: Int, second: Int, third: Int) = Unit
            class Owner { fun method(first: Int, second: Int, third: Int, fourth: Int = 4) = Unit }
            fun caller() { top(1, 2, 3); Owner().method(1, 2, 3) }
        """)
        assertEquals(2, findings.size)
        assertTrue(findings.all { it.message.contains("first, second, third") })
    }

    @Test fun `fully named calls and calls supplying fewer than three parameters are allowed`() {
        assertTrue(lint("""
            fun f(first: Int, second: Int = 2, third: Int = 3, fourth: Int = 4) = Unit
            fun small(first: Int, second: Int) = Unit
            fun caller() {
                f(1)
                f(1, 2)
                f(first = 1, third = 3)
                f(first = 1, second = 2, third = 3)
                f(first = 1, second = 2, third = 3, fourth = 4)
                small(1, 2)
            }
        """).isEmpty())
    }

    @Test fun `mixed calls require names only for remaining positional arguments`() {
        val findings = lint("""
            fun f(first: Int, second: Int, third: Int) = Unit
            fun caller() { f(1, second = 2, third = 3) }
        """)
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("for: first."))
    }

    @Test fun `overloads are checked using the resolved signature`() {
        assertEquals(1, lint("""
            fun f(first: Int, second: Int) = Unit
            fun f(first: String, second: String, third: String) = Unit
            fun caller() { f(1, 2); f("a", "b", "c") }
        """).size)
    }

    @Test fun `constructors aliases and super and this delegation require names`() {
        assertEquals(4, lint("""
            open class Model(val first: Int, val second: Int, val third: Int) {
                constructor(text: String) : this(1, 2, 3)
            }
            typealias Alias = Model
            class Child : Model(1, 2, 3)
            fun caller() { Model(1, 2, 3); Alias(1, 2, 3) }
        """).size)
    }

    @Test fun `named constructor and delegation arguments are allowed`() {
        assertTrue(lint("""
            open class Model(val first: Int, val second: Int, val third: Int) {
                constructor(text: String) : this(first = 1, second = 2, third = 3)
            }
            class Child : Model(first = 1, second = 2, third = 3)
            fun caller() { Model(first = 1, second = 2, third = 3) }
        """).isEmpty())
    }

    @Test fun `annotations and enum entry constructors are checked`() {
        assertEquals(2, lint("""
            annotation class Marker(val first: Int, val second: Int, val third: Int)
            @Marker(1, 2, 3)
            class Model
            enum class Choice(val first: Int, val second: Int, val third: Int) { A(1, 2, 3) }
        """).size)
    }

    @Test fun `Java methods constructors and function types need no names or comments`() {
        assertTrue(lint("""
            fun caller(callback: (first: Int, second: Int, third: Int) -> Unit) {
                java.lang.StringBuilder().append(charArrayOf('a', 'b', 'c'), 0, 2)
                java.lang.String(charArrayOf('a', 'b', 'c'), 0, 2)
                callback(1, 2, 3)
            }
        """).isEmpty())
    }

    @Test fun `extension methods and custom invoke operators have usable names`() {
        assertEquals(2, lint("""
            fun String.extend(first: Int, second: Int, third: Int) = Unit
            class Callable { operator fun invoke(first: Int, second: Int, third: Int) = Unit }
            fun caller() { "text".extend(1, 2, 3); Callable()(1, 2, 3) }
        """).size)
    }

    @Test fun `trailing lambdas count as supplied parameters but can stay outside parentheses`() {
        assertEquals(1, lint("""
            fun f(first: Int, second: Int, action: () -> Unit) = Unit
            fun caller() {
                f(1, 2) { }
                f(first = 1, second = 2) { }
                f(first = 1, second = 2, action = {})
            }
        """).size)
    }

    @Test fun `lambda arguments inside parentheses require names`() {
        assertEquals(1, lint("""
            fun f(first: Int, second: Int, action: () -> Unit) = Unit
            fun caller() { f(first = 1, second = 2, {}) }
        """).size)
    }

    @Test fun `multiple vararg elements do not count as multiple supplied parameters`() {
        assertTrue(lint("""
            fun f(first: Int = 1, second: Int = 2, vararg items: String) = Unit
            fun only(vararg items: Int) = Unit
            fun caller() { f(1, items = arrayOf("a", "b", "c")); only(1, 2, 3, 4) }
        """).isEmpty())
    }

    @Test fun `expanded varargs are exempt but surrounding parameters still require names`() {
        assertEquals(1, lint("""
            fun f(first: Int, second: Int, vararg items: String) = Unit
            fun caller() {
                f(1, 2, "a", "b")
                f(first = 1, second = 2, "a", "b")
            }
        """).size)
    }

    @Test fun `spread varargs can and must be named when the threshold is reached`() {
        assertEquals(1, lint("""
            fun f(first: Int, second: Int, vararg items: String) = Unit
            fun caller(items: Array<String>) {
                f(first = 1, second = 2, *items)
                f(first = 1, second = 2, items = *items)
            }
        """).size)
    }

    @Test fun `standard rule suppression is respected`() {
        assertTrue(lint("""
            fun f(first: Int, second: Int, third: Int) = Unit
            @Suppress("NamedArgumentsForLargeCalls")
            fun caller() { f(1, 2, 3) }
        """).isEmpty())
    }
}
