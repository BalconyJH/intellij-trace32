package io.github.balconyjh.trace32.cmm

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFileManager
import io.github.balconyjh.trace32.cmm.core.CmmCatalog
import io.github.balconyjh.trace32.cmm.core.CmmModel
import io.github.balconyjh.trace32.cmm.core.CmmScanner
import java.util.Locale

class CmmCompletionContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile as? CmmFile ?: return
        val text = parameters.editor.document.text
        val offset = parameters.offset.coerceAtMost(text.length)
        val tokens = CmmScanner.scan(text)
        val context = tokens.firstOrNull { it.start() < offset && offset <= it.end() }?.kind()
        if (context == CmmScanner.Kind.COMMENT) return
        if (completePaths(file, text, offset, result)) return
        var start = offset
        while (start > 0 && isWord(text[start - 1])) start--
        var prefix = text.substring(start, offset)
        // Parenthesized macro spelling is semantically the same name.
        val parenthesizedMacro = start >= 2 && text.substring(start - 2, start) == "&("
        if (parenthesizedMacro) prefix = "&$prefix"
        val macro = prefix.startsWith('&')
        if (context == CmmScanner.Kind.STRING && !macro) return
        val matched = result.withPrefixMatcher(if (parenthesizedMacro) prefix.drop(1) else prefix)
        val seen = HashSet<String>()
        val before = text.substring(text.lastIndexOf('\n', start - 1) + 1, start)
        val label = Regex("(?is).*\\b(GOTO|GOSUB|JUMPTO)\\s+").matches(before)
        if (macro || label) {
            val symbols = matched.withPrefixMatcher(SymbolPrefixMatcher(matched.prefixMatcher.prefix))
            for (symbol in file.model().symbols()) {
                if (macro && file.model().resolve(CmmModel.Reference(offset, offset, symbol.name(), true)) == null) continue
                if (symbol.macro() == macro && seen.add(symbol.name())) {
                    val name = if (parenthesizedMacro) symbol.name().removePrefix("&") else symbol.name()
                    var lookup = LookupElementBuilder.create(name).withCaseSensitivity(true)
                        .withTypeText(if (macro) "PRACTICE macro" else "PRACTICE label")
                    if (parenthesizedMacro) lookup = lookup.withInsertHandler { insertion, _ ->
                        val tail = insertion.tailOffset
                        if (tail >= insertion.document.textLength || insertion.document.charsSequence[tail] != ')')
                            insertion.document.insertString(tail, ")")
                        insertion.editor.caretModel.moveToOffset(tail + 1)
                    }
                    symbols.addElement(lookup)
                }
            }
            return
        }
        if (prefix.startsWith('/')) {
            val command = Regex("[A-Za-z][A-Za-z0-9_.]*").find(before)?.value ?: return
            for (option in CmmDocumentation.options(command))
                matched.caseInsensitive().addElement(LookupElementBuilder.create(option).withCaseSensitivity(false).withTypeText("Command option"))
            return
        }
        val commands = matched.caseInsensitive()
        for (entry in CmmCatalog.entries()) {
            if (!seen.add(entry.name().uppercase(Locale.ROOT) + if (entry.function()) "()" else "")) continue
            var lookup = LookupElementBuilder.create(entry.name()).withCaseSensitivity(false)
                .withLookupStrings(entry.aliases()).withTypeText(entry.detail(), true)
                .withTailText(CmmDocumentation.signature(entry.name())?.removePrefix(entry.name()) ?: if (entry.function()) "()" else "", true)
            if (entry.function()) lookup = lookup.withInsertHandler { insertion, _ ->
                val tail = insertion.tailOffset
                val document = insertion.document
                if (tail >= document.textLength || document.charsSequence[tail] != '(') {
                    document.insertString(tail, "()")
                    insertion.editor.caretModel.moveToOffset(tail + 1)
                }
            }
            commands.addElement(lookup)
        }
        for (token in tokens) {
            if (token.kind() != CmmScanner.Kind.COMMAND) continue
            val name = text.substring(token.start(), token.end())
            if (seen.add(name.uppercase(Locale.ROOT))) commands.addElement(LookupElementBuilder.create(name)
                .withCaseSensitivity(false).withTypeText("Command in this script"))
        }
    }

    private fun completePaths(file: CmmFile, text: String, offset: Int, result: CompletionResultSet): Boolean {
        val context = pathContext(text, offset) ?: return false
        val quoted = context.quoted
        val typed = context.typed
        if (typed.contains('&')) return false
        val slash = typed.lastIndexOf('/')
        val parent = if (slash < 0) "" else typed.substring(0, slash + 1)
        val leaf = typed.substring(slash + 1)
        val expanded = CmmReferences.expandedPath(file, parent) ?: return true
        val directory = VirtualFileManager.getInstance().getFileSystem("file").findFileByPath(expanded) ?: return true
        if (!directory.isDirectory) return true
        val matched = result.withPrefixMatcher(leaf).caseInsensitive()
        for (child in directory.children.take(2000)) {
            if (!child.isDirectory && !child.name.endsWith(".cmm", true) && !child.name.endsWith(".cmmt", true)) continue
            val value = child.name + if (child.isDirectory) "/" else ""
            var item = LookupElementBuilder.create(value).withCaseSensitivity(false)
                .withTypeText(if (child.isDirectory) "Directory" else "PRACTICE script")
            if (!quoted && value.contains(' ')) item = item.withInsertHandler { insertion, _ ->
                val pathStart = context.start
                val tail = insertion.tailOffset
                insertion.document.insertString(tail, "\"")
                insertion.document.insertString(pathStart, "\"")
                insertion.editor.caretModel.moveToOffset(tail + if (child.isDirectory) 1 else 2)
            }
            matched.addElement(item)
        }
        return true
    }
    private fun isWord(c: Char) = c.isLetterOrDigit() || c in "_.&/"

    // PRACTICE symbols remain case-sensitive regardless of the IDE's completion preferences.
    private class SymbolPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
        override fun prefixMatches(name: String) = name.startsWith(prefix)
        override fun cloneWithPrefix(prefix: String) = SymbolPrefixMatcher(prefix)
    }

    data class PathContext(val start: Int, val quoted: Boolean, val typed: String)
    companion object {
        private val scriptCommands = setOf("DO", "RUN", "CHDIR.DO", "DODECRYPT")

        /** Only a real command/action argument is a path; quoted text never becomes a command. */
        @JvmStatic fun pathContext(text: String, offset: Int): PathContext? {
            val end = offset.coerceIn(0, text.length)
            var start = text.lastIndexOf('\n', end - 1) + 1
            while (start > 0) {
                val previousEnd = start - 1
                val previousStart = text.lastIndexOf('\n', previousEnd - 1) + 1
                val previous = text.substring(previousStart, previousEnd)
                val last = CmmScanner.scan(previous).lastOrNull { it.kind() != CmmScanner.Kind.WHITESPACE }
                if (last?.kind() != CmmScanner.Kind.OPERATOR || previous.substring(last.start(), last.end()) != "\\") break
                start = previousStart
            }
            val prefix = text.substring(start, end)
            val scanned = CmmScanner.scan(prefix)
            if (scanned.lastOrNull()?.kind() == CmmScanner.Kind.COMMENT) return null
            fun continuation(token: CmmScanner.Token): Boolean {
                if (token.kind() != CmmScanner.Kind.OPERATOR || prefix.substring(token.start(), token.end()) != "\\") return false
                val newline = prefix.indexOf('\n', token.end())
                return newline >= 0 && prefix.substring(token.end(), newline).isBlank()
            }
            val tokens = scanned.filter { it.kind() !in setOf(CmmScanner.Kind.WHITESPACE, CmmScanner.Kind.COMMENT) && !continuation(it) }
            var commandIndex = if (tokens.firstOrNull()?.kind() == CmmScanner.Kind.LABEL) 1 else 0
            fun name(index: Int): String = tokens.getOrNull(index)?.let {
                if (it.kind() == CmmScanner.Kind.STRING) "" else CmmCatalog.canonicalName(prefix.substring(it.start(), it.end())).uppercase(Locale.ROOT)
            } ?: ""
            if (name(commandIndex) in setOf("ON", "GLOBALON")) {
                // Documented PRACTICE events CMD and TIME take one argument before the action.
                val event = name(commandIndex + 1)
                if (event.isEmpty()) return null
                commandIndex += if (event in setOf("CMD", "TIME")) 3 else 2
            }
            if (name(commandIndex) !in scriptCommands) return null
            val command = tokens[commandIndex]
            if (command.end() == prefix.length || !prefix[command.end()].isWhitespace()) return null
            var argumentStart = command.end()
            while (argumentStart < prefix.length) {
                if (prefix[argumentStart].isWhitespace()) { argumentStart++; continue }
                val marker = scanned.firstOrNull { it.start() == argumentStart && continuation(it) }
                if (marker != null) { argumentStart = marker.end(); continue }
                break
            }
            val raw = prefix.substring(argumentStart)
            val quote = raw.firstOrNull()?.takeIf { it == '"' || it == '\'' }
            val quoted = quote != null
            if (quote != null && raw.drop(1).contains(quote) || !quoted && raw.any(Char::isWhitespace)) return null
            return PathContext(start + argumentStart, quoted, (if (quoted) raw.drop(1) else raw).replace('\\', '/'))
        }
    }
}
