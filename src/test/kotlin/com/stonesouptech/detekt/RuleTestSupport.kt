package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.Rule
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File
import java.nio.file.Files

/** Runs the actual PSI visitor; semantic tests use the same K1 compiler analysis as Detekt 1.23.8. */
internal fun Rule.lint(code: String, resolve: Boolean = false): List<Finding> {
    check(KotlinVersion.CURRENT == KotlinVersion(2, 0, 21)) {
        "Detekt 1.23.8 semantic tests require kotlin-stdlib 2.0.21; found ${KotlinVersion.CURRENT}"
    }
    check(org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION == "2.0.21") {
        "Detekt 1.23.8 semantic tests require kotlin-compiler-embeddable 2.0.21"
    }
    val disposable = Disposer.newDisposable()
    val directory = Files.createTempDirectory("detekt-rule-test").toFile()
    try {
        val source = File(directory, "Test.kt").apply { writeText(code.trimIndent()) }
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "test")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")))
            configureJdkClasspathRoots()
            addJvmClasspathRoot(File(Unit::class.java.protectionDomain.codeSource.location.toURI()))

            if (resolve) addKotlinSourceRoot(source.absolutePath)
        }
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        val file = if (resolve) environment.getSourceFiles().single()
            else KtPsiFactory(environment.project).createFile("Test.kt", source.readText())
        check(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) == null) { "Invalid test syntax" }
        if (resolve) {
            val analysis = checkNotNull(KotlinToJVMBytecodeCompiler.analyze(environment))
            analysis.throwIfError()
            val errors = analysis.bindingContext.diagnostics.all().filter {
                it.severity == org.jetbrains.kotlin.diagnostics.Severity.ERROR &&
                    it.factory.name != "NO_ELSE_IN_WHEN"
            }
            check(errors.isEmpty()) { "Unexpected compiler errors: ${errors.map { it.factory.name }}" }
            visitFile(file, analysis.bindingContext)
        } else visitFile(file)
        return findings.toList()
    } finally {
        Disposer.dispose(disposable)
        directory.deleteRecursively()
    }
}

internal class TestConfig(private val values: Map<String, Any> = emptyMap()) : Config {
    override val parentPath: String? = null
    override fun subConfig(key: String): Config = this
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> valueOrDefault(key: String, default: T): T =
        if (key == "active") true as T else values[key] as? T ?: default
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> valueOrNull(key: String): T? = values[key] as? T
}
