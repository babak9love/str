package dev.cluvex.strata.core.shell

import android.content.Context

/**
 * Quick Settings tiles are declared in the manifest, so there is a fixed number of them ([SLOTS]); the person
 * picks which saved command each one runs. The choice is stored on the command itself ([SavedCommand.tile]),
 * so it travels with the command in the shell export and in the settings backup.
 */
object ShellTiles {
    const val SLOTS = 4

    /** The saved command behind [slot] (1..[SLOTS]), or null when the slot is empty. */
    fun command(context: Context, slot: Int): SavedCommand? =
        runCatching { ShellCommandStore(context.applicationContext).load().firstOrNull { it.tile == slot } }.getOrNull()

    /**
     * Makes sure every slot belongs to at most one command and that no command holds a slot that does not exist.
     * When several commands claim the same slot, the one whose id is in [prefer] keeps it (else the first one);
     * the others lose their tile.
     */
    fun resolve(commands: List<SavedCommand>, prefer: Set<String> = emptySet()): List<SavedCommand> {
        val owner = HashMap<Int, String>()
        for (slot in 1..SLOTS) {
            val holders = commands.filter { it.tile == slot }
            val winner = holders.firstOrNull { it.id in prefer } ?: holders.firstOrNull()
            if (winner != null) owner[slot] = winner.id
        }
        return commands.map { c ->
            if (c.tile == 0 || owner[c.tile] == c.id) c else c.copy(tile = 0)
        }
    }
}
