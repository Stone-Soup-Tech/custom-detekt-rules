package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.*
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

abstract class ConventionRule(config: Config, id: String, description: String) : Rule(config) {
    final override val issue = Issue(id, Severity.Style, description, Debt.FIVE_MINS)

    protected fun flag(element: PsiElement, message: String = issue.description) {
        report(CodeSmell(issue, Entity.from(element), message))
    }
}

// Look only at whitespace PSI leaves; comments and declaration contents cannot create a blank line.
internal fun whitespaceBefore(element: PsiElement): String = buildString {
    var leaf = PsiTreeUtil.prevLeaf(element)
    while (leaf is PsiWhiteSpace) {
        insert(0, leaf.text)
        leaf = PsiTreeUtil.prevLeaf(leaf)
    }
}

internal fun String.hasBlankLine(): Boolean = replace("\r\n", "\n").count { it == '\n' } >= 2

class BlankLineBeforeDocumentedProperty(config: Config = Config.empty) : ConventionRule(
    config, "BlankLineBeforeDocumentedProperty",
    "Separate constructor property KDoc from the preceding property with a blank line."
) {
    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (!klass.isData()) return
        klass.primaryConstructorParameters.filter { it.hasValOrVar() }.drop(1).forEach { parameter ->
            val doc = parameter.docComment ?: parameter.children.filterIsInstance<KDoc>().firstOrNull()
            if (doc != null && !whitespaceBefore(doc).hasBlankLine()) flag(doc)
        }
    }
}

class NoForceUnwrap(config: Config = Config.empty) : ConventionRule(
    config, "NoForceUnwrap", "Use a safe let call or a local value with an Elvis early return instead of !!."
) {
    override fun visitPostfixExpression(expression: KtPostfixExpression) {
        super.visitPostfixExpression(expression)
        if (expression.operationToken == KtTokens.EXCLEXCL) flag(expression.operationReference)
    }
}

class NoBlankLinesBetweenWhenBranches(config: Config = Config.empty) : ConventionRule(
    config, "NoBlankLinesBetweenWhenBranches", "Do not leave blank lines between when branches."
) {
    override fun visitWhenExpression(expression: KtWhenExpression) {
        super.visitWhenExpression(expression)
        expression.entries.zipWithNext().forEach { (previous, next) ->
            // Inspect only the PSI between entries, including whitespace on either side of comments.
            var sibling = previous.nextSibling
            while (sibling != null && sibling != next) {
                if (sibling is PsiWhiteSpace && sibling.text.hasBlankLine()) {
                    flag(next)
                    break
                }
                sibling = sibling.nextSibling
            }
        }
    }
}

class BracesRequired(config: Config = Config.empty) : ConventionRule(
    config, "BracesRequired", "Use braces for if and else unless the entire conditional fits on one physical line."
) {
    override fun visitIfExpression(expression: KtIfExpression) {
        super.visitIfExpression(expression)
        if (expression.text.none { it == '\n' || it == '\r' }) return
        listOfNotNull(expression.then, expression.`else`).forEach { branch ->
            // An else-if continues the conditional chain; its own branches are visited separately.
            if (branch !is KtBlockExpression && !(branch == expression.`else` && branch is KtIfExpression)) flag(branch)
        }
    }
}

class SingleInitBlock(config: Config = Config.empty) : ConventionRule(
    config, "SingleInitBlock", "Use one init block per class; preserve execution order when consolidating initialization."
) {
    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        classOrObject.declarations.filterIsInstance<KtClassInitializer>().drop(1).forEach { flag(it) }
    }
}

class MemberVisibilityOrder(config: Config = Config.empty) : ConventionRule(
    config, "MemberVisibilityOrder", "Order properties and functions: public, internal, protected, private."
) {
    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        // Constructor properties precede body members in source and participate in the same order.
        val parameters = (classOrObject as? KtClass)?.primaryConstructorParameters.orEmpty().filter { it.hasValOrVar() }
        val members = parameters + classOrObject.declarations.filter { it is KtProperty || it is KtNamedFunction }
        var highest = -1
        members.forEach { member ->
            val rank = when {
                member.hasModifier(KtTokens.PRIVATE_KEYWORD) -> 3
                member.hasModifier(KtTokens.PROTECTED_KEYWORD) -> 2
                member.hasModifier(KtTokens.INTERNAL_KEYWORD) -> 1
                else -> 0
            }
            if (rank < highest) flag(member) else highest = rank
        }
    }
}
