package dev.cluvex.strata.core.apk

import androidx.annotation.StringRes
import dev.cluvex.strata.R

/** How the installed-apps list is ordered. The enum name is what is stored in the settings (and their backup). */
enum class AppsSort(@StringRes val label: Int) {
    NAME(R.string.apps_sort_name),
    SIZE(R.string.apps_sort_size),
    LAST_UPDATED(R.string.apps_sort_last_updated),
    ;

    /** A-Z for names; biggest and most recently updated first for the rest. [reversed] flips that. */
    fun comparator(reversed: Boolean = false): Comparator<InstalledApp> {
        val byName = compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        val base = when (this) {
            NAME -> byName
            SIZE -> compareByDescending<InstalledApp> { it.apkSize }.then(byName)
            LAST_UPDATED -> compareByDescending<InstalledApp> { it.lastUpdate }.then(byName)
        }
        return if (reversed) base.reversed() else base
    }
}
