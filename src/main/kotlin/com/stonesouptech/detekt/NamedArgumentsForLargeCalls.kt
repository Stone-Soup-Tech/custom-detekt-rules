package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext

@RequiresTypeResolution
class NamedArgumentsForLargeCalls(config: Config = Config.empty) : ConventionRule(
    config, "NamedArgumentsForLargeCalls",
    "Use named arguments when explicitly supplying at least three parameters inside parentheses."
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        checkCall(expression)
    }

    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
        super.visitSuperTypeCallEntry(call)
        checkCall(call)
    }

    override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) {
        super.visitConstructorDelegationCall(call)
        checkCall(call)
    }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        checkCall(annotationEntry)
    }

    private fun checkCall(element: KtCallElement) {
        val callee = element.calleeExpression ?: return
        val call = bindingContext[BindingContext.CALL, callee] ?: return
        val resolved = bindingContext[BindingContext.RESOLVED_CALL, call] ?: return
        if (!resolved.status.isSuccess) return
        val descriptor = resolved.resultingDescriptor
        val underlying = (descriptor as? TypeAliasConstructorDescriptor)?.underlyingConstructorDescriptor ?: descriptor
        // Java bytecode can expose parameter names, but Kotlin still forbids named arguments.
        if (underlying.original is JavaCallableMemberDescriptor) return
        if (descriptor.valueParameters.size < 3 || !descriptor.hasStableParameterNames()) return

        // Defaults and trailing lambdas do not count; vararg elements supply one parameter.
        val supplied = resolved.valueArguments.filter { (_, value) ->
            value.arguments.any { it.asElement() !is KtLambdaArgument }
        }
        if (supplied.size < 3) return
        val unnamed = supplied.flatMap { (parameter, value) ->
            value.arguments.filter { argument ->
                !argument.isNamed() && argument.asElement() !is KtLambdaArgument &&
                    // Expanded varargs cannot be named individually. Spread arrays can be named.
                    (parameter.varargElementType == null || argument.getSpreadElement() != null)
            }.map { parameter.name.asString() }
        }
        if (unnamed.isNotEmpty()) {
            flag(element.valueArgumentList ?: element,
                "Use named arguments for: ${unnamed.joinToString()}. This call explicitly supplies ${supplied.size} parameters.")
        }
    }
}
