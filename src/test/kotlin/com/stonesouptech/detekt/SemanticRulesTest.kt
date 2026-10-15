package com.stonesouptech.detekt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SemanticRulesTest {
    @Test fun `non exhaustive when statements require remaining cases`() {
        assertEquals(1, ExhaustiveWhen(TestConfig()).lint("""
            fun f(x: Int) { when (x) { 1 -> println(x) } }
        """, resolve = true).size)
    }

    @Test fun `exhaustive boolean enum and sealed when need no else`() {
        assertTrue(ExhaustiveWhen(TestConfig()).lint("""
            enum class Choice { A, B }
            sealed class State { object A : State(); object B : State() }
            fun bool(x: Boolean) = when (x) { true -> 1; false -> 2 }
            fun choice(x: Choice) { when (x) { Choice.A -> println(1); Choice.B -> println(2) } }
            fun state(x: State) = when (x) { is State.A -> 1; is State.B -> 2 }
            fun other(x: Int) = when (x) { 1 -> 1; else -> 2 }
        """, resolve = true).isEmpty())
    }

    @Test fun `nullable exhaustive when covers null and subjectless when needs else`() {
        assertEquals(1, ExhaustiveWhen(TestConfig()).lint("""
            fun f(x: Boolean?) {
                when (x) { true -> println(1); false -> println(2); null -> println(3) }
                when { x == true -> println(1) }
            }
        """, resolve = true).size)
    }

    @Test fun `default values are matched by resolved parameter for functions and constructors`() {
        assertEquals(4, RedundantDefaultArgument(TestConfig()).lint("""
            class Model(val count: Int = 1, val name: String = "hello")
            fun method(count: Int = 1, name: String = "hello") = Unit
            fun f() {
                Model(1, name = "hello")
                method(name = "hello", count = 1)
            }
        """, resolve = true).size)
    }

    @Test fun `constant expressions null and const references can match defaults`() {
        assertEquals(4, RedundantDefaultArgument(TestConfig()).lint("""
            const val DEFAULT = 2
            fun f(a: Int = DEFAULT, b: String? = null, c: Boolean = true, d: String = "hello") = Unit
            fun caller() { f(a = 1 + 1, b = null, c = true, d = "hello") }
        """, resolve = true).size)
    }

    @Test fun `overloads different values omitted arguments and side effects are preserved`() {
        assertTrue(RedundantDefaultArgument(TestConfig()).lint("""
            fun f(a: Int = 1) = Unit
            fun f(a: String = "one") = Unit
            fun fresh(): String = "one"
            fun g(a: String = fresh()) = Unit
            fun caller() { f(2); f("two"); f(a = 3); g(fresh()); g() }
        """, resolve = true).isEmpty())
    }

    @Test fun `varargs and non constant expressions are not compared by spelling`() {
        assertTrue(RedundantDefaultArgument(TestConfig()).lint("""
            fun f(vararg args: Int) = Unit
            fun g(value: Any = Any()) = Unit
            fun caller() { f(1, 2); g(Any()) }
        """, resolve = true).isEmpty())
    }

    @Test fun `inherited method defaults are resolved`() {
        assertEquals(1, RedundantDefaultArgument(TestConfig()).lint("""
            open class Base { open fun f(a: Int = 1) = Unit }
            class Child : Base() { override fun f(a: Int) = Unit }
            fun caller() { Child().f(1) }
        """, resolve = true).size)
    }

    @Test fun `super and this constructor default arguments are checked`() {
        assertEquals(2, RedundantDefaultArgument(TestConfig()).lint("""
            open class Base(val value: Int = 1)
            class Child : Base(1)
            class Other(val value: Int = 1) { constructor(s: String) : this(1) }
        """, resolve = true).size)
    }

    @Test fun `enum defaults use resolved identity and annotations are constructor calls`() {
        assertEquals(2, RedundantDefaultArgument(TestConfig()).lint("""
            enum class Mode { FAST, SLOW }
            annotation class Marker(val enabled: Boolean = true)
            @Marker(enabled = true)
            fun f(mode: Mode = Mode.FAST) = Unit
            fun caller() { f(Mode.FAST); f(Mode.SLOW) }
        """, resolve = true).size)
    }

    private fun extensionRule() = AppOwnedExtension(TestConfig(mapOf("appOwnedPackages" to listOf("com.example.app"))))

    @Test fun `app owned functions and properties require rationale`() {
        assertEquals(2, extensionRule().lint("""
            package com.example.app.model
            class Model(val name: String)
            fun Model.displayName() = name
            val Model.label: String get() = name
            fun String.label() = this
        """, resolve = true).size)
    }

    @Test fun `rationale may be in KDoc or an adjacent comment`() {
        assertTrue(extensionRule().lint("""
            package com.example.app
            class Model
            // Extension rationale: Keeps Android dependencies out of the domain module.
            fun Model.displayName() = "model"
            /** Extension rationale: Adapter behavior belongs to this dependency layer. */
            val Model.label: String get() = "model"
        """, resolve = true).isEmpty())
    }

    @Test fun `empty detached or unrelated comments are not rationale`() {
        assertEquals(3, extensionRule().lint("""
            package com.example.app
            class Model
            // Extension rationale:
            fun Model.first() = 1
            // Extension rationale: Adapter behavior.

            fun Model.second() = 2
            /** This is a model extension. */
            fun Model.third() = 3
        """, resolve = true).size)
    }

    @Test fun `receiver aliases nullable types and generic receivers resolve to their owners`() {
        assertEquals(3, extensionRule().lint("""
            package com.example.app
            class Model<T>
            typealias Alias = Model<String>
            fun Alias.first() = 1
            fun Model<String>?.second() = 2
            fun <T> Model<T>.third() = 3
        """, resolve = true).size)
    }

    @Test fun `ownership prefix requires a package boundary and must be configured`() {
        val code = """
            package com.example.application
            class Model
            fun Model.first() = 1
        """
        assertTrue(extensionRule().lint(code, resolve = true).isEmpty())
        assertTrue(AppOwnedExtension(TestConfig()).lint("""
            package com.example.app
            class Model
            fun Model.first() = 1
        """, resolve = true).isEmpty())
    }
}
