package com.stonesouptech.detekt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SemanticRulesTest {
    @Test fun `non exhaustive class subjects require remaining cases`() {
        assertEquals(1, ExhaustiveWhen(TestConfig()).lint("""
            interface Message
            class Ready : Message
            fun f(x: Message) { when (x) { is Ready -> println(x) } }
        """, resolve = true).size)
    }

    @Test fun `primitive string and nullable subjects are ignored`() {
        val cases = listOf(
            "Byte" to "1.toByte()", "Short" to "1.toShort()", "Int" to "1",
            "Long" to "1L", "Float" to "1f", "Double" to "1.0",
            "Char" to "'a'", "Boolean" to "true", "String" to "\"hello\"",
            "UByte" to "1.toUByte()", "UShort" to "1.toUShort()",
            "UInt" to "1u", "ULong" to "1uL"
        )
        val code = cases.flatMap { (type, value) ->
            listOf(type, "$type?").mapIndexed { index, parameterType ->
                "fun check$type$index(value: $parameterType) { when (value) { $value -> println(value) } }"
            }
        }.joinToString("\n")
        assertTrue(ExhaustiveWhen(TestConfig()).lint(code, resolve = true).isEmpty())
    }

    @Test fun `subject variable primitive types and aliases are ignored`() {
        assertTrue(ExhaustiveWhen(TestConfig()).lint("""
            typealias Label = String
            fun f(label: Label) {
                when (val size = label.length) { 1 -> println(size) }
                when (label) { "hello" -> println(label) }
            }
        """, resolve = true).isEmpty())
    }

    @Test fun `exhaustive enum and sealed expressions and explicit else are accepted`() {
        assertTrue(ExhaustiveWhen(TestConfig()).lint("""
            enum class Choice { A, B }
            sealed class State { object A : State(); object B : State() }
            fun choice(x: Choice) = when (x) { Choice.A -> 1; Choice.B -> 2 }
            fun state(x: State) = when (x) { is State.A -> 1; is State.B -> 2 }
            fun other(x: Any) = when (x) { is String -> 1; else -> 2 }
        """, resolve = true).isEmpty())
    }

    @Test fun `non nullable enum property covers all entries inside nested lambdas`() {
        assertTrue(ExhaustiveWhen(TestConfig()).lint("""
            enum class TransferPhase { IDLE, SENDING, RECEIVING, FAILED }
            data class Snapshot(val phase: TransferPhase = TransferPhase.IDLE)
            class View { var text = ""; var visible = true }
            fun observe(block: (Snapshot) -> Unit) { block(Snapshot()) }
            fun render(view: View) {
                observe {
                    when (it.phase) {
                        TransferPhase.SENDING -> view.apply { text = "sending"; visible = true }
                        TransferPhase.RECEIVING -> view.apply { text = "receiving"; visible = false }
                        TransferPhase.IDLE -> { view.text = "idle" }
                        TransferPhase.FAILED -> { view.text = "failed" }
                    }
                }
            }
        """, resolve = true).isEmpty())
    }

    @Test fun `enum property requires missing entries and null branches`() {
        assertEquals(2, ExhaustiveWhen(TestConfig()).lint("""
            enum class TransferPhase { IDLE, SENDING }
            class Snapshot(val phase: TransferPhase = TransferPhase.IDLE)
            class OptionalSnapshot(val phase: TransferPhase? = TransferPhase.IDLE)
            fun incomplete(snapshot: Snapshot) {
                when (snapshot.phase) { TransferPhase.IDLE -> println("idle") }
            }
            fun nullable(snapshot: OptionalSnapshot) {
                when (snapshot.phase) {
                    TransferPhase.IDLE -> println("idle")
                    TransferPhase.SENDING -> println("sending")
                }
            }
        """, resolve = true).size)
    }

    @Test fun `sealed interface statements handle every subtype`() {
        assertTrue(ExhaustiveWhen(TestConfig()).lint("""
            sealed interface PreviewEvent {
                data class Loaded(val value: String) : PreviewEvent
                data object Closed : PreviewEvent
            }
            fun handle(event: PreviewEvent) {
                when (event) {
                    is PreviewEvent.Loaded -> println(event.value)
                    PreviewEvent.Closed -> println("closed")
                }
            }
        """, resolve = true).isEmpty())
    }

    @Test fun `sealed property when with empty bodies and grouped objects is exhaustive`() {
        assertTrue(ExhaustiveWhen(TestConfig()).lint("""
            sealed class Destination {
                data class Folder(val path: String) : Destination()
                data class Document(val id: String) : Destination()
                data object Home : Destination()
                data object Recent : Destination()
                data object Settings : Destination()
                companion object
            }
            class Request(val destination: Destination)
            fun handle(request: Request) {
                when (request.destination) {
                    is Destination.Folder -> { }
                    is Destination.Document -> { }
                    Destination.Home, Destination.Recent -> { }
                    Destination.Settings -> { }
                }
            }
        """, resolve = true).isEmpty())
    }

    @Test fun `sealed when still requires omitted subtypes and null`() {
        assertEquals(2, ExhaustiveWhen(TestConfig()).lint("""
            sealed class Destination {
                data class Folder(val path: String) : Destination()
                data object Home : Destination()
                data object Recent : Destination()
            }
            fun incomplete(destination: Destination) {
                when (destination) {
                    Destination.Home, Destination.Recent -> { }
                }
            }
            fun nullable(destination: Destination?) {
                when (destination) {
                    is Destination.Folder -> { }
                    Destination.Home, Destination.Recent -> { }
                }
            }
        """, resolve = true).size)
    }

    @Test fun `subjectless when is ignored while nested class subjects are checked`() {
        assertEquals(1, ExhaustiveWhen(TestConfig()).lint("""
            interface Message
            class Ready : Message
            fun handle(message: Message) {
                when {
                    message is Ready -> {
                        when (message as Message) { is Ready -> println(message) }
                    }
                }
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

    @Test fun `data class copy arguments can reset properties to constructor defaults`() {
        assertTrue(RedundantDefaultArgument(TestConfig()).lint("""
            data class Model(val count: Int = 0, val name: String? = null, val enabled: Boolean = false)
            fun caller() {
                val model = Model(5, "changed", true)
                model.copy(count = 0, name = null, enabled = false)
                model.copy(0, null, false)
            }
        """, resolve = true).isEmpty())
    }

    @Test fun `custom copy methods still use their declared defaults`() {
        assertEquals(1, RedundantDefaultArgument(TestConfig()).lint("""
            class Model { fun copy(count: Int = 0) = count }
            fun caller() { Model().copy(count = 0) }
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
