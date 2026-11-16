package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.*
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cfg.WhenChecker
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.CompileTimeConstant
import org.jetbrains.kotlin.types.TypeUtils

@RequiresTypeResolution
class ExhaustiveWhen(config: Config = Config.empty) : ConventionRule(
    config, "ExhaustiveWhen", "When subjects other than primitives and String must cover all cases or include else."
) {
    override fun visitWhenExpression(expression: KtWhenExpression) {
        super.visitWhenExpression(expression)
        if (expression.subjectExpression == null || expression.elseExpression != null) return
        // Without a resolved subject type, missing compiler flags cannot prove missing cases.
        val subjectType = WhenChecker.whenSubjectType(expression, bindingContext) ?: return
        if (KotlinBuiltIns.isPrimitiveTypeOrNullablePrimitiveType(subjectType) ||
            KotlinBuiltIns.isStringOrNullableString(subjectType) ||
            KotlinBuiltIns.isUnsignedNumber(TypeUtils.makeNotNullable(subjectType))
        ) return
        if (bindingContext[BindingContext.EXHAUSTIVE_WHEN, expression] != true &&
            bindingContext[BindingContext.IMPLICIT_EXHAUSTIVE_WHEN, expression] != true &&
            WhenChecker.getMissingCases(expression, bindingContext).isNotEmpty()
        ) flag(expression)
    }
}

@RequiresTypeResolution
class RedundantDefaultArgument(config: Config = Config.empty) : ConventionRule(
    config, "RedundantDefaultArgument", "Omit explicit arguments that equal the parameter's default constant value."
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        checkCall(expression)
    }

    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
        super.visitSuperTypeCallEntry(call)
        checkCall(call)
    }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        checkCall(annotationEntry)
    }

    private fun checkCall(element: KtElement) {
        val callee = (element as? KtCallElement)?.calleeExpression ?: return
        val call = bindingContext[BindingContext.CALL, callee] ?: return
        val resolved = bindingContext[BindingContext.RESOLVED_CALL, call] ?: return
        val descriptor = resolved.resultingDescriptor
        // Generated copy defaults read the receiver's properties, not constructor defaults.
        if (descriptor.name.asString() == "copy" &&
            (descriptor as? CallableMemberDescriptor)?.kind == CallableMemberDescriptor.Kind.SYNTHESIZED &&
            (descriptor.containingDeclaration as? ClassDescriptor)?.isData == true
        ) return
        resolved.valueArguments.forEach { (parameter, argument) ->
            if (parameter.varargElementType != null) return@forEach
            val value = argument.arguments.singleOrNull() ?: return@forEach
            val actual = value.getArgumentExpression() ?: return@forEach
            if (isNamedValue(actual)) return@forEach
            val default = defaultExpression(parameter) ?: return@forEach
            val actualConstant = constant(actual) ?: return@forEach
            val defaultConstant = constant(default) ?: return@forEach
            if (actualConstant == defaultConstant) flag(value.asElement())
        }
    }

    private fun isNamedValue(expression: KtExpression): Boolean {
        val reference = when (val unwrapped = KtPsiUtil.deparenthesize(expression)) {
            is KtDotQualifiedExpression -> unwrapped.selectorExpression as? KtNameReferenceExpression
            is KtNameReferenceExpression -> unwrapped
            else -> null
        } ?: return false
        return bindingContext[BindingContext.REFERENCE_TARGET, reference] is VariableDescriptor
    }

    private fun defaultExpression(parameter: ValueParameterDescriptor): KtExpression? {
        val source = DescriptorToSourceUtils.descriptorToDeclaration(parameter.original) as? KtParameter
        return source?.defaultValue ?: parameter.overriddenDescriptors.firstNotNullOfOrNull { defaultExpression(it) }
    }

    private fun constant(expression: KtExpression): Any? {
        val value: CompileTimeConstant<*>? = bindingContext[BindingContext.COMPILE_TIME_VALUE, expression]
        if (value != null && !value.isError && !value.usesNonConstValAsConstant) {
            val type = bindingContext.getType(expression) ?: return null
            // ConstantValue preserves types (including null) instead of comparing source spelling.
            return value.toConstantValue(type)
        }
        val unwrapped = KtPsiUtil.deparenthesize(expression)
        val reference = when (unwrapped) {
            is KtDotQualifiedExpression -> unwrapped.selectorExpression as? KtNameReferenceExpression
            is KtNameReferenceExpression -> unwrapped
            else -> null
        } ?: return null
        val target = bindingContext[BindingContext.REFERENCE_TARGET, reference] as? ClassDescriptor ?: return null
        return target.original.takeIf { it.kind == ClassKind.ENUM_ENTRY }
    }
}

@RequiresTypeResolution
class AppOwnedExtension(config: Config = Config.empty) : ConventionRule(
    config, "AppOwnedExtension", "Put derived behavior on app-owned types or document why an extension is necessary."
) {
    private val appOwnedPackages: List<String> by config(emptyList<String>())

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        checkExtension(function, function.receiverTypeReference)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        checkExtension(property, property.receiverTypeReference)
    }

    private fun checkExtension(declaration: KtDeclaration, receiver: KtTypeReference?) {
        if (receiver == null) return
        val type = bindingContext[BindingContext.TYPE, receiver] ?: return
        val owner = type.constructor.declarationDescriptor ?: return
        val fqName = appOwnedType(owner, mutableSetOf()) ?: return
        if (!hasRationale(declaration)) flag(declaration,
            "Move this behavior to $fqName or add a nearby 'Extension rationale: <reason>' comment.")
    }

    private fun appOwnedType(owner: ClassifierDescriptor, visited: MutableSet<ClassifierDescriptor>): String? {
        if (!visited.add(owner)) return null
        if (owner is TypeParameterDescriptor) {
            return owner.upperBounds.firstNotNullOfOrNull { bound ->
                bound.constructor.declarationDescriptor?.let { appOwnedType(it, visited) }
            }
        }
        if (owner !is ClassDescriptor) return null
        val fqName = DescriptorUtils.getFqNameSafe(owner).asString()
        return fqName.takeIf {
            appOwnedPackages.any { it.isNotBlank() && (fqName == it || fqName.startsWith("$it.")) }
        }
    }

    private fun hasRationale(declaration: KtDeclaration): Boolean {
        val comments = mutableListOf<String>()
        declaration.docComment?.let { comments += it.text }
        // Kotlin PSI may attach a leading line comment to the declaration itself.
        generateSequence(declaration.firstChild) { it.nextSibling }
            .takeWhile { it is PsiComment || it is PsiWhiteSpace }
            .filterIsInstance<PsiComment>().forEach { comments += it.text }
        var leaf = PsiTreeUtil.prevLeaf(declaration)
        while (leaf != null) {
            if (leaf is PsiWhiteSpace) {
                if (leaf.text.hasBlankLine()) break
                leaf = PsiTreeUtil.prevLeaf(leaf)
                continue
            }
            val comment = leaf as? PsiComment ?: PsiTreeUtil.getParentOfType(leaf, KDoc::class.java)
            if (comment == null) break
            comments += comment.text
            leaf = PsiTreeUtil.prevLeaf(comment)
        }
        return comments.any { comment ->
            val marker = "Extension rationale:"
            val index = comment.indexOf(marker, ignoreCase = true)
            index >= 0 && comment.substring(index + marker.length).trim().trim('*', '/', ' ', '\n', '\r').isNotBlank()
        }
    }
}
