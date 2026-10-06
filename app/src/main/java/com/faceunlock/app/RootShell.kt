package com.faceunlock.app

import android.util.Log
import java.io.DataOutputStream

/** Minimal helper that pipes commands to a root (`su`) shell. */
object RootShell {

    fun run(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                for (c in commands) {
                    os.writeBytes(c + "\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            val code = process.waitFor()
            Log.d("RootShell", "exit=$code for ${commands.size} cmd(s)")
            code == 0
        } catch (e: Exception) {
            Log.e("RootShell", "su failed", e)
            false
        }
    }

    fun run(vararg commands: String): Boolean = run(commands.toList())

    fun hasRoot(): Boolean = run("id")
}
