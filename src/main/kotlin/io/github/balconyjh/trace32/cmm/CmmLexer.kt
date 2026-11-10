package io.github.balconyjh.trace32.cmm

import com.intellij.lexer.LexerBase
import io.github.balconyjh.trace32.cmm.core.CmmScanner

/** Parser lexer. String tokens stay intact so references retain their original offsets. */
open class CmmLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var end = 0
    private var scanner: CmmScanner? = null
    private var token: CmmScanner.Token? = null
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        end = endOffset
        scanner = CmmScanner(buffer, startOffset, endOffset, initialState)
        advance()
    }
    override fun getState() = token?.state() ?: 0
    override fun getTokenType() = token?.let { CmmTypes.of(it.kind()) }
    override fun getTokenStart() = token?.start() ?: end
    override fun getTokenEnd() = token?.end() ?: end
    override fun advance() { token = scanner?.next() }
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = end
}
