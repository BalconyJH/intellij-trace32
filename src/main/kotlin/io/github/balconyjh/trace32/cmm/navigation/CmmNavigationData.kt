package io.github.balconyjh.trace32.cmm.navigation

import com.intellij.util.io.DataExternalizer
import io.github.balconyjh.trace32.cmm.core.CmmModel
import java.io.DataInput
import java.io.DataOutput

/** Content-only index data: no PSI, project settings, or filesystem access while indexing. */
object CmmNavigationData {
    const val SYMBOL_PREFIX = "symbol:"
    const val CALLS_KEY = "calls:"
    data class Entry(val value: String, val offset: Int, val macro: Boolean)

    @JvmStatic fun indexText(text: String): Map<String, List<Entry>> {
        val model = CmmModel.parse(text)
        val result = LinkedHashMap<String, MutableList<Entry>>()
        for (symbol in model.symbols()) {
            result.getOrPut(SYMBOL_PREFIX + symbol.name()) { ArrayList() }
                .add(Entry(symbol.name(), symbol.start(), symbol.macro()))
        }
        val calls = model.scriptPaths().filter { it.path().isNotBlank() && '&' !in it.path() }
            .map { Entry(it.path(), it.start(), false) }
        if (calls.isNotEmpty()) result[CALLS_KEY] = calls.toMutableList()
        return result
    }

    @JvmField val EXTERNALIZER = object : DataExternalizer<List<Entry>> {
        override fun save(output: DataOutput, value: List<Entry>) {
            output.writeInt(value.size)
            for (entry in value) {
                val bytes = entry.value.toByteArray(Charsets.UTF_8)
                output.writeInt(bytes.size)
                output.write(bytes)
                output.writeInt(entry.offset)
                output.writeBoolean(entry.macro)
            }
        }
        override fun read(input: DataInput): List<Entry> {
            val count = input.readInt()
            require(count in 0..1_000_000) { "Invalid CMM index entry count" }
            return List(count) {
                val length = input.readInt()
                require(length in 0..16_777_216) { "Invalid CMM index value length" }
                val bytes = ByteArray(length)
                input.readFully(bytes)
                Entry(String(bytes, Charsets.UTF_8), input.readInt(), input.readBoolean())
            }
        }
    }

    /** Expand static roots at query time, so changing settings never leaves the content index stale. */
    @JvmStatic fun expandedPath(directory: String, value: String, system: String, temporary: String): String? {
        if (value.isBlank() || '&' in value) return null
        val path = value.replace('\\', '/')
        val parent = directory.replace('\\', '/').trimEnd('/')
        fun root(prefix: String, location: String): String? = location.takeIf(String::isNotBlank)
            ?.replace('\\', '/')?.trimEnd('/')?.plus(path.removePrefix(prefix))
        return when {
            path == "~~~~" || path.startsWith("~~~~/") -> parent + path.removePrefix("~~~~")
            path == "~~~" || path.startsWith("~~~/") -> root("~~~", temporary)
            path == "~~" || path.startsWith("~~/") -> root("~~", system)
            path.startsWith('~') -> null
            path.startsWith('/') || Regex("^[A-Za-z]:/.*").matches(path) -> path
            else -> "$parent/$path"
        }
    }
}
