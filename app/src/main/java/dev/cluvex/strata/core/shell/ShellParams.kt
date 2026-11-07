package dev.cluvex.strata.core.shell

import android.content.Context

/**
 * The {package} placeholder of a saved command: where the package name of an app goes. The app is chosen when the
 * command runs, and the command is run with the name written in. Only a well-formed package name is ever written
 * into a command, so a picked or typed value can never add anything else to the shell line.
 */
object ShellParams {
    const val PACKAGE = "{package}"
    private val VALID = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun needsPackage(command: String): Boolean = command.contains(PACKAGE)

    fun isValidPackage(name: String): Boolean = VALID.matches(name)

    /**
     * The same saved command (same id, so its run and log stay together) with the package name written in. For
     * several apps the command is written once for each, one after the other, and runs as a single run.
     */
    fun resolveAll(command: SavedCommand, packages: List<String>): SavedCommand {
        require(packages.isNotEmpty() && packages.all { isValidPackage(it) }) { "Not a package name" }
        return command.copy(command = packages.joinToString("\n") { command.command.replace(PACKAGE, it) })
    }

    fun resolve(command: SavedCommand, packageName: String): SavedCommand = resolveAll(command, listOf(packageName))

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("shell_ui", Context.MODE_PRIVATE)

    /** The app picked the last time this command ran, so the picker can offer it first. */
    fun lastPackage(context: Context, commandId: String): String? =
        prefs(context).getString("last_pkg_$commandId", null)?.takeIf { isValidPackage(it) }

    fun rememberPackage(context: Context, commandId: String, packageName: String) {
        prefs(context).edit().putString("last_pkg_$commandId", packageName).apply()
    }
}
