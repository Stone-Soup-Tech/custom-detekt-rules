package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class AndroidConventionRuleSetProvider : RuleSetProvider {
    override val ruleSetId = "android-conventions"

    override fun instance(config: Config) = RuleSet(ruleSetId, listOf(
        BlankLineBeforeDocumentedProperty(config),
        KDocLiteralBackticks(config),
        NoForceUnwrap(config),
        ExhaustiveWhen(config),
        NoBlankLinesBetweenWhenBranches(config),
        BracesRequired(config),
        WhenBranchBracesRequired(config),
        SingleInitBlock(config),
        MemberVisibilityOrder(config),
        RedundantDefaultArgument(config),
        NamedArgumentsForLargeCalls(config),
        AppOwnedExtension(config),
    ))
}
