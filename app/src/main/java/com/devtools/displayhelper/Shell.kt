package com.devtools.displayhelper

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands with ADB (shell) privileges through Shizuku. */
object Shell {
    fun running(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun granted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun requestPermission() { try { Shizuku.requestPermission(100) } catch (_: Throwable) {} }

    fun exec(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Throwable) { "ERR: ${e.message}" }
}
