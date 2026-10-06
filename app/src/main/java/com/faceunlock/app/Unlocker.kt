package com.faceunlock.app

/**
 * Unlocks a *secure* keyguard (PIN) using root by waking the screen, revealing
 * the PIN bouncer with a swipe-up, then injecting the digits as key events.
 *
 * KEYCODE_0 == 7, so digit d maps to keyevent (7 + d). KEYCODE_ENTER == 66.
 *
 * Pattern/password: see customScript() escape hatch in Prefs for advanced setups.
 */
object Unlocker {

    fun unlockWithPin(pin: String, width: Int, height: Int): Boolean {
        val cmds = buildList {
            add("input keyevent 224")                         // KEYCODE_WAKEUP
            add("sleep 0.4")
            // reveal the bouncer / PIN pad
            add("input touchscreen swipe ${width / 2} ${(height * 0.80).toInt()} ${width / 2} ${(height * 0.25).toInt()} 200")
            add("sleep 0.5")
            for (ch in pin) {
                if (ch in '0'..'9') add("input keyevent ${7 + (ch - '0')}")
            }
            add("input keyevent 66")                          // KEYCODE_ENTER (submit)
        }
        return RootShell.run(cmds)
    }

    /** Advanced: run a user-provided shell sequence (for pattern/password setups). */
    fun unlockWithScript(script: String): Boolean {
        val lines = script.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return RootShell.run(lines)
    }

    /** Wake the screen without unlocking (used before starting the camera). */
    fun wake(): Boolean = RootShell.run("input keyevent 224")
}
