package com.devtools.displayhelper

import android.content.Context
import android.hardware.display.DisplayManager
import kotlin.math.roundToInt

object RefreshCtl {
    fun supported(ctx: Context): List<Int> {
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0)
        return d.supportedModes.map { it.refreshRate.roundToInt() }.distinct().sorted()
    }

    fun current(ctx: Context): Float =
        (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0).refreshRate

    /** Raise the maximum refresh rate the system may use. */
    fun boost(hz: Int) {
        Shell.exec("settings put system peak_refresh_rate $hz; settings put system user_refresh_rate $hz")
    }

    /** Lock frames: min = max = hz. */
    fun lock(hz: Int) {
        boost(hz)
        Shell.exec("settings put system min_refresh_rate $hz")
    }

    fun unlock() { Shell.exec("settings put system min_refresh_rate 0") }

    // ---- Resolution / screen dimensions (wm size) ----
    fun physical(): Pair<Int, Int>? {
        val m = Regex("Physical size: (\\d+)x(\\d+)").find(Shell.exec("wm size")) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    /** Keeps the physical aspect ratio, so there are no black bars. */
    fun setWidth(w: Int) {
        val (pw, ph) = physical() ?: return
        val h = ((ph.toDouble() * w / pw).roundToInt() / 2) * 2
        Shell.exec("wm size ${w}x$h")
    }

    fun resetSize() { Shell.exec("wm size reset") }
}
