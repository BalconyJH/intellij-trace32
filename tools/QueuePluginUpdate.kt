import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.ide.startup.StartupActionScriptManager.CopyCommand
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Uses the installed IDE's own serialization format; never executes the queued actions. */
fun main(args: Array<String>) {
    require(args.size == 3) { "Expected staged JAR, installed JAR, startup action script" }
    val source = Path.of(args[0]).toAbsolutePath().normalize()
    val target = Path.of(args[1]).toAbsolutePath().normalize()
    val script = Path.of(args[2]).toAbsolutePath().normalize()
    require(Files.isRegularFile(source) && source != target)
    val previous = if (Files.exists(script)) Files.readAllBytes(script) else null
    val commands = if (previous == null) mutableListOf() else StartupActionScriptManager.loadActionScript(script).toMutableList()
    val command = CopyCommand(source, target)
    // Repeating installation of the same staged build is idempotent.
    if (commands.none { it is CopyCommand && it.toString() == command.toString() }) commands.add(command)
    Files.createDirectories(script.parent)
    val temporary = Files.createTempFile(script.parent, "trace32-toolkit-actions-", ".tmp")
    try {
        StartupActionScriptManager.saveActionScript(commands, temporary)
        val restored = StartupActionScriptManager.loadActionScript(temporary)
        check(restored.size == commands.size && restored.any { it.toString() == command.toString() })
        if (previous == null) {
            // Refuse to overwrite actions concurrently created by the IDE.
            Files.move(temporary, script)
        } else {
            check(Files.readAllBytes(script).contentEquals(previous)) { "IDE changed its pending actions; retry installation." }
            val digest = MessageDigest.getInstance("SHA-256").digest(previous).joinToString("") { "%02x".format(it) }
            val backup = script.resolveSibling("action.script.trace32-toolkit-backup-$digest")
            if (!Files.exists(backup)) Files.write(backup, previous)
            Files.move(temporary, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        println("Queued for CLion restart: $source -> $target")
        println("Startup actions preserved: ${commands.size}")
    } finally {
        Files.deleteIfExists(temporary)
    }
}
