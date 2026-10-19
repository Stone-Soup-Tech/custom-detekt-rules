package com.stonesouptech.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtFile

class KDocLiteralBackticks(config: Config = Config.empty) : ConventionRule(
    config, "KDocLiteralBackticks", "Wrap true, false, and null in backticks in KDoc prose."
) {
    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        PsiTreeUtil.findChildrenOfType(file, KDoc::class.java).forEach { doc ->
            val prose = doc.text.toCharArray()
            fun mask(start: Int, end: Int) {
                for (index in start until end) prose[index] = ' '
            }

            // Strip KDoc decoration before recognizing Markdown fence lines, retaining offsets.
            var fence: String? = null
            var offset = 0
            doc.text.splitToSequence('\n').forEach { line ->
                val content = line.trimStart().removePrefix("/**").trimStart().removePrefix("*").trimStart()
                val marker = Regex("^(`{3,}|~{3,})").find(content)?.value
                if (fence != null) {
                    mask(offset, offset + line.length)
                    val opening = checkNotNull(fence)
                    if (marker != null && marker.first() == opening.first() && marker.length >= opening.length &&
                        content.drop(marker.length).isBlank()) fence = null
                } else if (marker != null) {
                    fence = marker
                    mask(offset, offset + line.length)
                }
                offset += line.length + 1
            }

            // A code span can contain more backticks by using a longer delimiter.
            val spans = Regex("(?<!`)(`+)(?!`)([\\s\\S]*?)(?<!`)\\1(?!`)")
            spans.findAll(String(prose)).forEach { mask(it.range.first, it.range.last + 1) }
            // KDoc links, Markdown destinations, and bare URLs refer to names/addresses, not prose.
            val links = Regex("\\[[^]\\r\\n]*]\\([^\\r\\n)]*\\)|\\[[^]\\r\\n]*]|https?://[^\\s<>]+")
            links.findAll(String(prose)).forEach { match ->
                // Keep inline link labels visible, but exclude their destinations.
                val destination = match.value.indexOf("](")
                if (destination >= 0) mask(match.range.first + destination + 1, match.range.last + 1)
                else mask(match.range.first, match.range.last + 1)
            }
            Regex("(?<![\\p{L}\\p{N}_])(true|false|null)(?![\\p{L}\\p{N}_])")
                .findAll(String(prose)).forEach { match ->
                    flag(doc, "Wrap '${match.value}' in backticks in KDoc prose.")
                }
        }
    }
}
