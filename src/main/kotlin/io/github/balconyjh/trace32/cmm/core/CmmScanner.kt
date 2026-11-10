package io.github.balconyjh.trace32.cmm.core

/** Lossless, restartable PRACTICE lexer, independent of the IntelliJ runtime. */
class CmmScanner(private val buffer: CharSequence, start: Int, private val end: Int, initialState: Int) {
    enum class Kind {
        WHITESPACE, COMMENT, STRING, MACRO, KEYWORD, COMMAND, FUNCTION, NUMBER,
        IDENTIFIER, LABEL, OPERATOR, LPAREN, RPAREN, BAD_CHARACTER
    }

    /** [state] is the state immediately before this token, for incremental restart. */
    data class Token(val kind: Kind, val start: Int, val end: Int, val state: Int) {
        fun kind() = kind
        fun start() = start
        fun end() = end
        fun state() = state
    }

    private var offset = start
    private var state = initialState

    init { require(start >= 0 && end >= start && end <= buffer.length) { "Invalid scanner range" } }

    fun next(): Token? {
        if (offset >= end) return null
        val start = offset
        val before = state
        val c = buffer[offset]
        if (c.isWhitespace()) {
            while (offset < end && buffer[offset].isWhitespace()) {
                val whitespace = buffer[offset++]
                if (isNewline(whitespace)) {
                    if (whitespace == '\r' && at(offset, '\n')) offset++
                    state = if (state and CONTINUED != 0) state and CONTINUED.inv() else 0
                }
            }
            return Token(Kind.WHITESPACE, start, offset, before)
        }
        if ((c == ';' && state and VAR_COMMAND == 0) || (c == '/' && at(offset + 1, '/'))) {
            while (offset < end && !isNewline(buffer[offset])) offset++
            return Token(Kind.COMMENT, start, offset, before)
        }
        // Backticks quote source-language symbols (e.g. `<clinit>()`), not JS template strings.
        if (c == '"' || c == '\'' || c == '`') {
            offset++
            while (offset < end && !isNewline(buffer[offset])) {
                if (buffer[offset++] == c) {
                    // PRACTICE strings escape quotes by doubling, not by backslash.
                    if (c != '`' && at(offset, c)) offset++ else break
                }
            }
            state = state or HAS_COMMAND
            return Token(Kind.STRING, start, offset, before)
        }
        val macroEnd = if (c == '&') macroEnd(offset) else offset
        if (macroEnd > offset) {
            offset = macroEnd
            state = state or HAS_COMMAND
            return Token(Kind.MACRO, start, offset, before)
        }
        // At a statement start, &&name=... is recursive macro assignment. Keep its
        // operator separate so the following &name retains normal symbol identity.
        if (c == '&' && state and HAS_COMMAND == 0 && at(offset + 1, '&')) {
            var assignment = macroEnd(offset + 1)
            if (assignment > offset + 1) {
                while (assignment < end && buffer[assignment] in " \t") assignment++
                if (at(assignment, '=') && !at(assignment + 1, '=')) {
                    offset++
                    return Token(Kind.OPERATOR, start, offset, before)
                }
            }
        }
        if (isNameStart(c)) {
            offset++
            while (offset < end && (isNamePart(buffer[offset]) ||
                        (buffer[offset] == '.' && !at(offset + 1, '.') && !at(offset - 1, '.')))) offset++
            val word = buffer.subSequence(start, offset).toString()
            if (at(offset, ':') && isPhysicalLineStart(start) && labelEndsAt(offset + 1)) {
                offset++
                return Token(Kind.LABEL, start, offset, before)
            }
            var following = offset
            while (following < end && buffer[following] in " \t") following++
            val kind = when {
                CmmCatalog.isKeyword(word) -> Kind.KEYWORD
                at(following, '(') -> Kind.FUNCTION
                state and HAS_COMMAND == 0 && !at(offset, ':') -> Kind.COMMAND
                else -> Kind.IDENTIFIER
            }
            if (state and HAS_COMMAND == 0 && (word.equals("Var", true) || word.startsWith("Var.", true))) {
                state = state or VAR_COMMAND
            }
            state = state or HAS_COMMAND
            return Token(kind, start, offset, before)
        }
        if (c.isDigit()) {
            offset++
            if (c == '0' && offset < end && buffer[offset] in "xXyYbB") {
                val radix = buffer[offset++].lowercaseChar()
                while (offset < end && (buffer[offset] == '_' || buffer[offset] in "xX" ||
                            if (radix == 'x') isHexDigit(buffer[offset]) else buffer[offset] in "01")) offset++
            } else {
                while (offset < end && (buffer[offset].isDigit() || buffer[offset] == '_')) offset++
                if (at(offset, '.') && !at(offset + 1, '.')) {
                    offset++
                    while (offset < end && buffer[offset].isDigit()) offset++
                }
                if (at(offset, 'e') || at(offset, 'E')) {
                    var exponent = offset + 1
                    if (at(exponent, '+') || at(exponent, '-')) exponent++
                    if (exponent < end && buffer[exponent].isDigit()) {
                        offset = exponent + 1
                        while (offset < end && buffer[offset].isDigit()) offset++
                    }
                }
            }
            while (offset < end && buffer[offset].isLetter()) offset++
            state = state or HAS_COMMAND
            return Token(Kind.NUMBER, start, offset, before)
        }
        offset++
        if (c == '(' || c == ')') {
            state = state or HAS_COMMAND
            return Token(if (c == '(') Kind.LPAREN else Kind.RPAREN, start, offset, before)
        }
        if (c in "+-*/%=!<>|&^~?:,.;[]{}#\\@") {
            if (c == '\\' && isContinuation(start)) state = state or CONTINUED
            if (offset < end && isDoubleOperator(c, buffer[offset])) offset++
            state = state or HAS_COMMAND
            return Token(Kind.OPERATOR, start, offset, before)
        }
        state = state or HAS_COMMAND
        return Token(Kind.BAD_CHARACTER, start, offset, before)
    }

    private fun macroEnd(position: Int): Int {
        var cursor = position + 1
        val parenthesized = at(cursor, '(')
        if (parenthesized) cursor++
        if (cursor >= end || !isNameStart(buffer[cursor])) return position
        cursor++
        while (cursor < end && isNamePart(buffer[cursor])) cursor++
        if (parenthesized) {
            if (!at(cursor, ')')) return position
            cursor++
        }
        return cursor
    }

    private fun at(position: Int, expected: Char) = position in 0 until end && buffer[position] == expected
    private fun isPhysicalLineStart(position: Int) = position == 0 || isNewline(buffer[position - 1])
    private fun labelEndsAt(position: Int) = position == end || buffer[position].isWhitespace() ||
        at(position, ';') || (at(position, '/') && at(position + 1, '/'))

    private fun isContinuation(position: Int): Boolean {
        var next = position + 1
        while (next < end && buffer[next] in " \t") next++
        return next < end && isNewline(buffer[next])
    }

    companion object {
        private const val HAS_COMMAND = 1
        private const val VAR_COMMAND = 2
        private const val CONTINUED = 4

        @JvmStatic fun scan(buffer: CharSequence): List<Token> {
            val scanner = CmmScanner(buffer, 0, buffer.length, 0)
            return buildList { while (true) add(scanner.next() ?: break) }
        }

        private fun isNameStart(c: Char) = c == '_' || c in 'A'..'Z' || c in 'a'..'z'
        private fun isNamePart(c: Char) = isNameStart(c) || c in '0'..'9'
        private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
        private fun isNewline(c: Char) = c == '\r' || c == '\n'
        private fun isDoubleOperator(first: Char, second: Char) = when (first) {
            '&', '|', '+', '-', '<', '>' -> second == first || second == '='
            '=', '!', '*', '/' -> second == '='
            '.' -> second == '.'
            else -> false
        }
    }
}
