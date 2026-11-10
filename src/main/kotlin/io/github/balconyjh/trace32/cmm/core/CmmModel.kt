package io.github.balconyjh.trace32.cmm.core

import java.util.ArrayDeque
import java.util.Locale
import java.util.regex.Pattern

/** Static file-local semantics. Never evaluates scripts or the runtime PRACTICE stack. */
class CmmModel private constructor(
    private val symbols: List<Symbol>, private val references: List<Reference>,
    private val folds: List<Fold>, private val scriptPaths: List<ScriptPath>,
    private val declarations: List<Declaration>
) {
    data class Symbol(val name: String, val start: Int, val end: Int, val macro: Boolean) {
        fun name() = name; fun start() = start; fun end() = end; fun macro() = macro
    }
    data class Reference(val start: Int, val end: Int, val name: String, val macro: Boolean) {
        fun start() = start; fun end() = end; fun name() = name; fun macro() = macro
    }
    data class Fold(val start: Int, val end: Int) { fun start() = start; fun end() = end }
    data class ScriptPath(val start: Int, val end: Int, val path: String) {
        fun start() = start; fun end() = end; fun path() = path
    }
    private data class Lexeme(val token: CmmScanner.Token, val text: String) {
        val start get() = token.start
        val end get() = token.end
        val kind get() = token.kind
        fun upper() = CmmCatalog.canonicalName(text).uppercase(Locale.ROOT)
    }
    private data class Line(val start: Int, val end: Int, val words: List<Lexeme>)
    private data class Declaration(val symbol: Symbol, val scope: Fold, val global: Boolean, val explicit: Boolean)
    private data class Assignment(val name: String, val start: Int, val scope: Fold, val declarationStart: Int, val expression: String)

    fun symbols() = symbols
    fun references() = references
    fun folds() = folds
    fun scriptPaths() = scriptPaths

    /** Returns a same-file definition, or null when static resolution is unsafe. */
    fun resolve(reference: Reference): Symbol? = if (!reference.macro) {
        symbols.firstOrNull { !it.macro && it.name == reference.name }
    } else visibleDeclaration(declarations, macroName(reference.name), reference.start)?.symbol

    companion object {
        private val MACRO = Pattern.compile("&(?:\\([A-Za-z_][A-Za-z0-9_]*\\)|[A-Za-z_][A-Za-z0-9_]*)")
        private val NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*")
        private val SCRIPT_DIRECTORY = Pattern.compile("(?i)OS\\.(?:PPD|PresentPracticeDirectory)\\s*\\(\\s*\\)")
        private val DECLARATIONS = setOf("PRIVATE", "LOCAL", "GLOBAL", "PARAMETERS", "ENTRY")
        private val BRANCHES = setOf("GOTO", "GOSUB", "JUMPTO")
        private val SCRIPT_COMMANDS = setOf("DO", "RUN", "CHDIR.DO", "DODECRYPT")
        private val INPUT_COMMANDS = setOf("ENTRY", "PARAMETERS", "RETURNVALUES", "READ", "ENTER", "INKEY", "SPRINTF")

        /** Symbol identity is independent of the optional parentheses in &(name). */
        @JvmStatic fun macroName(text: String) = if (text.startsWith("&(") && text.endsWith(')')) "&" + text.substring(2, text.length - 1) else text

        @JvmStatic fun parse(text: String): CmmModel {
            val tokens = CmmScanner.scan(text)
            val lines = lines(text, tokens)
            val blockFolds = findBlockFolds(lines)
            val root = Fold(0, text.length + 1)
            val symbols = mutableListOf<Symbol>()
            val declarations = mutableListOf<Declaration>()
            val assignments = mutableListOf<Assignment>()
            val definitionOffsets = hashSetOf<Int>()
            for (line in lines) {
                val words = line.words
                if (words.isEmpty()) continue
                var commandIndex = 0
                val first = words[0]
                if (first.kind == CmmScanner.Kind.LABEL) {
                    val name = first.text.removeSuffix(":")
                    symbols.add(Symbol(name, first.start, first.start + name.length, false))
                    definitionOffsets.add(first.start)
                    commandIndex++
                }
                if (commandIndex >= words.size) continue
                // &&macro=... is the recursive form of assignment, not a different symbol.
                if (words[commandIndex].text == "&" && words.getOrNull(commandIndex + 1)?.kind == CmmScanner.Kind.MACRO &&
                    words.getOrNull(commandIndex + 2)?.text == "=") commandIndex++
                val command = words[commandIndex]
                if (command.upper() == "SUBROUTINE" && commandIndex + 1 < words.size) {
                    val target = words[commandIndex + 1]
                    if (NAME.matcher(target.text).matches()) {
                        symbols.add(Symbol(target.text, target.start, target.end, false))
                        definitionOffsets.add(target.start)
                    }
                }
                if (command.upper() in DECLARATIONS) {
                    for (word in words.drop(commandIndex + 1)) {
                        if (word.kind != CmmScanner.Kind.MACRO) continue
                        val symbol = Symbol(macroName(word.text), word.start, word.end, true)
                        val scope = scopeAt(blockFolds, root, word.start)
                        val global = command.upper() == "GLOBAL"
                        if (hasDeclaration(declarations, symbol.name, scope, global)) continue
                        symbols.add(symbol)
                        declarations.add(Declaration(symbol, scope, global, true))
                        definitionOffsets.add(word.start)
                    }
                }
                if (command.kind == CmmScanner.Kind.MACRO && words.getOrNull(commandIndex + 1)?.text == "=") {
                    val scope = scopeAt(blockFolds, root, command.start)
                    val name = macroName(command.text)
                    if (visibleDeclaration(declarations, name, command.start) == null) {
                        val symbol = Symbol(name, command.start, command.end, true)
                        symbols.add(symbol)
                        declarations.add(Declaration(symbol, scope, false, false))
                        definitionOffsets.add(command.start)
                    }
                    val declaration = visibleDeclaration(declarations, name, command.start)!!
                    val expression = text.substring(words[commandIndex + 1].end, words.last().end).trim()
                    assignments.add(Assignment(name, command.start, scope, declaration.symbol.start, expression))
                } else if (command.upper() in INPUT_COMMANDS) {
                    for (word in words.drop(commandIndex + 1)) {
                        if (word.kind != CmmScanner.Kind.MACRO) continue
                        val name = macroName(word.text)
                        val declaration = visibleDeclaration(declarations, name, word.start) ?: continue
                        assignments.add(Assignment(name, word.start, scopeAt(blockFolds, root, word.start), declaration.symbol.start, ""))
                    }
                }
            }
            val references = mutableListOf<Reference>()
            for (token in tokens) {
                if (token.kind == CmmScanner.Kind.MACRO && token.start !in definitionOffsets) {
                    references.add(Reference(token.start, token.end, macroName(text.substring(token.start, token.end)), true))
                } else if (token.kind == CmmScanner.Kind.STRING) {
                    val matcher = MACRO.matcher(text.substring(token.start, token.end))
                    while (matcher.find()) references.add(Reference(token.start + matcher.start(), token.start + matcher.end(), macroName(matcher.group()), true))
                }
            }
            val paths = mutableListOf<ScriptPath>()
            for (line in lines) {
                val words = line.words
                if (words.isEmpty()) continue
                var commandIndex = if (words[0].kind == CmmScanner.Kind.LABEL) 1 else 0
                if (commandIndex >= words.size) continue
                if (words[commandIndex].upper() in setOf("ON", "GLOBALON")) {
                    // ON/GLOBALON have one action slot; CMD and TIME consume one extra argument.
                    // Do not interpret words inside EXECute/PRINT arguments as PRACTICE actions.
                    val event = words.getOrNull(commandIndex + 1)?.upper() ?: continue
                    commandIndex += if (event in setOf("CMD", "TIME")) 3 else 2
                }
                val command = words.getOrNull(commandIndex) ?: continue
                if (command.upper() in BRANCHES) {
                    val target = words.getOrNull(commandIndex + 1) ?: continue
                    if (NAME.matcher(target.text).matches()) references.add(Reference(target.start, target.end, target.text, false))
                } else if (command.upper() in SCRIPT_COMMANDS) {
                    val path = words.getOrNull(commandIndex + 1)?.let { readPath(text, line, it.start) }
                    if (path != null) paths.add(path.copy(path = expandDirectoryAlias(path.path, path.start, assignments, declarations)))
                }
            }
            val folds = (blockFolds + findCommentFolds(text, tokens)).sortedBy { it.start }
            return CmmModel(symbols.sortedBy { it.start }, references.sortedBy { it.start }, folds, paths.toList(), declarations.toList())
        }

        private fun hasDeclaration(declarations: List<Declaration>, name: String, scope: Fold, global: Boolean) =
            declarations.any { it.explicit && it.symbol.name == name && it.global == global && it.scope == scope }

        private fun visibleDeclaration(declarations: List<Declaration>, name: String, offset: Int): Declaration? {
            var best: Declaration? = null
            for (candidate in declarations) {
                if (candidate.symbol.name == name && candidate.symbol.start <= offset &&
                    (candidate.global || contains(candidate.scope, offset)) && (best == null || moreSpecific(candidate, best))) best = candidate
            }
            return best
        }
        private fun moreSpecific(candidate: Declaration, current: Declaration): Boolean {
            val candidateSize = if (candidate.global) Int.MAX_VALUE else candidate.scope.end - candidate.scope.start
            val currentSize = if (current.global) Int.MAX_VALUE else current.scope.end - current.scope.start
            if (candidateSize != currentSize) return candidateSize < currentSize
            if (candidate.explicit != current.explicit) return candidate.explicit
            return candidate.symbol.start < current.symbol.start
        }
        private fun scopeAt(folds: List<Fold>, root: Fold, offset: Int) =
            folds.filter { contains(it, offset) }.minByOrNull { it.end - it.start } ?: root
        private fun contains(scope: Fold, offset: Int) = offset >= scope.start && offset < scope.end

        private fun lines(text: String, tokens: List<CmmScanner.Token>): List<Line> {
            val result = mutableListOf<Line>()
            var logicalWords = mutableListOf<Lexeme>()
            var logicalStart = 0
            var tokenIndex = 0
            var start = 0
            while (start <= text.length) {
                val newline = text.indexOf('\n', start)
                val end = if (newline < 0) text.length else newline
                val words = mutableListOf<Lexeme>()
                while (tokenIndex < tokens.size && tokens[tokenIndex].start < end) {
                    val token = tokens[tokenIndex++]
                    if (token.kind != CmmScanner.Kind.WHITESPACE && token.kind != CmmScanner.Kind.COMMENT) {
                        words.add(Lexeme(token, text.substring(token.start, token.end)))
                    }
                }
                val last = words.lastOrNull()
                val continued = last?.kind == CmmScanner.Kind.OPERATOR && last.text == "\\" && newline >= 0 && text.substring(last.end, end).isBlank()
                if (continued) words.removeAt(words.lastIndex)
                logicalWords.addAll(words)
                if (!continued) {
                    result.add(Line(logicalStart, end, logicalWords))
                    logicalWords = mutableListOf()
                    logicalStart = newline + 1
                }
                if (newline < 0) break
                start = newline + 1
            }
            return result
        }

        private fun findBlockFolds(lines: List<Line>): List<Fold> {
            val stack = ArrayDeque<Lexeme>()
            val folds = mutableListOf<Fold>()
            for (line in lines) {
                val first = line.words.firstOrNull() ?: continue
                if (first.kind == CmmScanner.Kind.LPAREN && line.words.size == 1) stack.push(first)
                else if (first.kind == CmmScanner.Kind.RPAREN && stack.isNotEmpty()) {
                    val open = stack.pop()
                    if (open.start < line.start) folds.add(Fold(open.start, first.end))
                }
            }
            return folds.sortedBy { it.start }
        }

        private fun findCommentFolds(text: String, tokens: List<CmmScanner.Token>): List<Fold> {
            val folds = mutableListOf<Fold>()
            var first: CmmScanner.Token? = null
            var last: CmmScanner.Token? = null
            var count = 0
            fun flush() {
                if (count >= 2) folds.add(Fold(first!!.start, last!!.end))
                first = null; last = null; count = 0
            }
            for (token in tokens) {
                if (token.kind != CmmScanner.Kind.COMMENT) continue
                val lineStart = text.lastIndexOf('\n', token.start - 1) + 1
                val ownLine = text.substring(lineStart, token.start).isBlank()
                val adjacent = last?.let { previous ->
                    val gap = text.substring(previous.end, token.start)
                    gap.isBlank() && gap.count { it == '\n' } == 1
                } ?: true
                if (!ownLine || !adjacent) flush()
                if (ownLine) { if (first == null) first = token; last = token; count++ }
            }
            flush()
            return folds
        }

        private fun readPath(text: String, line: Line, firstArgument: Int): ScriptPath? {
            var start = firstArgument
            while (start < line.end && text[start].isWhitespace()) start++
            if (start >= line.end) return null
            val quote = text[start]
            var end: Int
            if (quote == '"' || quote == '\'') {
                start++; end = start
                while (end < line.end && text[end] != quote) end++
            } else {
                end = start
                while (end < line.end && !text[end].isWhitespace() && text[end] != ';' &&
                    !(text[end] == '/' && end + 1 < line.end && text[end + 1] == '/')) end++
            }
            return if (start == end) null else ScriptPath(start, end, text.substring(start, end))
        }
        private fun expandDirectoryAlias(path: String, offset: Int, assignments: List<Assignment>, declarations: List<Declaration>): String {
            val macro = MACRO.matcher(path)
            if (!macro.lookingAt()) return path
            val name = macroName(macro.group())
            if (macro.end() < path.length && path[macro.end()] != '/' && path[macro.end()] != '\\') return path
            val visible = visibleDeclaration(declarations, name, offset) ?: return path
            val latest = assignments.filter { it.name == name && it.start < offset && it.declarationStart == visible.symbol.start }.maxByOrNull { it.start } ?: return path
            if (!contains(latest.scope, offset) || !SCRIPT_DIRECTORY.matcher(latest.expression).matches()) return path
            return "." + path.substring(macro.end())
        }
    }
}
