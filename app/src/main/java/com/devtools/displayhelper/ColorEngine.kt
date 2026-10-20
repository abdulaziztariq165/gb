package com.devtools.displayhelper

import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Global screen colour matrix, applied through SurfaceFlinger (service call 1015) using Shizuku.
 * Matrix layout: 3x4 row-major (3x3 linear part + offset in column 4).
 */
object ColorEngine {
    class Adj(val key: String, val label: String, val min: Float, val max: Float, val def: Float)

    val adjustments = listOf(
        Adj("brightness", "السطوع", -0.3f, 0.3f, 0f),
        Adj("contrast", "التباين", 0.6f, 1.6f, 1f),
        Adj("saturation", "التشبع", 0f, 2f, 1f),
        Adj("vibrance", "الحيوية", 0f, 1f, 0f),
        Adj("exposure", "التعريض", -1f, 1f, 0f),
        Adj("hue", "درجة اللون (Hue)", -180f, 180f, 0f),
        Adj("warmth", "الدفء (حار/بارد)", -1f, 1f, 0f),
        Adj("tint", "الصبغة (أخضر/أرجواني)", -1f, 1f, 0f),
        Adj("red", "قناة الأحمر", 0.5f, 1.5f, 1f),
        Adj("green", "قناة الأخضر", 0.5f, 1.5f, 1f),
        Adj("blue", "قناة الأزرق", 0.5f, 1.5f, 1f),
        Adj("black", "مستوى الأسود", -0.1f, 0.2f, 0f),
        Adj("white", "مستوى الأبيض", 0.7f, 1.3f, 1f),
        Adj("gray", "تدرج رمادي", 0f, 1f, 0f),
        Adj("sepia", "سيبيا", 0f, 1f, 0f),
    )

    val presets: Map<String, Map<String, Float>> = linkedMapOf(
        "الأصلي" to mapOf(),
        "حيوي" to mapOf("saturation" to 1.35f, "vibrance" to 0.3f, "contrast" to 1.1f),
        "واقعي" to mapOf("saturation" to 1.0f, "warmth" to 0.05f, "contrast" to 1.03f),
        "سينما" to mapOf("contrast" to 1.15f, "saturation" to 0.9f, "warmth" to 0.15f, "black" to 0.02f),
        "HDR" to mapOf("contrast" to 1.25f, "saturation" to 1.2f, "exposure" to 0.1f, "white" to 1.05f, "vibrance" to 0.4f),
    )

    fun applyPreset(name: String) {
        resetValues()
        presets[name]?.forEach { (k, v) -> Prefs.setF(k, v) }
    }

    fun resetValues() { adjustments.forEach { Prefs.remove(it.key) } }

    private fun g(k: String): Float = Prefs.f(k, adjustments.first { it.key == k }.def)

    private typealias Mat = DoubleArray

    private fun ident(): Mat = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

    private fun lin(a: DoubleArray, t: DoubleArray = DoubleArray(3)): Mat = doubleArrayOf(
        a[0], a[1], a[2], t[0], a[3], a[4], a[5], t[1], a[6], a[7], a[8], t[2]
    )

    /** result(x) = second(first(x)) */
    private fun then(first: Mat, second: Mat): Mat {
        val r = DoubleArray(12)
        for (i in 0..2) {
            for (j in 0..2) {
                var s = 0.0
                for (k in 0..2) s += second[i * 4 + k] * first[k * 4 + j]
                r[i * 4 + j] = s
            }
            var t = second[i * 4 + 3]
            for (k in 0..2) t += second[i * 4 + k] * first[k * 4 + 3]
            r[i * 4 + 3] = t
        }
        return r
    }

    private fun gain(r: Double, gg: Double, b: Double, off: Double = 0.0) =
        lin(doubleArrayOf(r, 0.0, 0.0, 0.0, gg, 0.0, 0.0, 0.0, b), doubleArrayOf(off, off, off))

    private fun satMat(s: Double): Mat {
        val lr = 0.2126; val lg = 0.7152; val lb = 0.0722
        return lin(doubleArrayOf(
            lr * (1 - s) + s, lg * (1 - s), lb * (1 - s),
            lr * (1 - s), lg * (1 - s) + s, lb * (1 - s),
            lr * (1 - s), lg * (1 - s), lb * (1 - s) + s
        ))
    }

    private fun hueMat(deg: Double): Mat {
        val a = Math.toRadians(deg); val c = cos(a); val s = sin(a)
        val lr = 0.213; val lg = 0.715; val lb = 0.072
        return lin(doubleArrayOf(
            lr + c * (1 - lr) - s * lr, lg - c * lg - s * lg, lb - c * lb + s * (1 - lb),
            lr - c * lr + s * 0.143, lg + c * (1 - lg) + s * 0.140, lb - c * lb - s * 0.283,
            lr - c * lr - s * (1 - lr), lg - c * lg + s * lg, lb + c * (1 - lb) + s * lb
        ))
    }

    fun build(): Mat {
        var m = ident()
        val e = 2.0.pow(g("exposure").toDouble())
        m = then(m, gain(e, e, e))
        val w = g("white").toDouble()
        m = then(m, gain(w, w, w))
        m = then(m, gain(g("red").toDouble(), g("green").toDouble(), g("blue").toDouble()))
        val wr = g("warmth").toDouble()
        m = then(m, gain(1 + 0.2 * wr, 1.0, 1 - 0.2 * wr))
        val ti = g("tint").toDouble()
        m = then(m, gain(1 + 0.1 * ti, 1 - 0.2 * ti, 1 + 0.1 * ti))
        val sat = g("saturation") * (1 + g("vibrance") * 0.5) * (1 - g("gray"))
        m = then(m, satMat(sat.toDouble()))
        val sp = g("sepia").toDouble()
        if (sp > 0) {
            val se = doubleArrayOf(.393, .769, .189, .349, .686, .168, .272, .534, .131)
            val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
            m = then(m, lin(DoubleArray(9) { id[it] * (1 - sp) + se[it] * sp }))
        }
        if (g("hue") != 0f) m = then(m, hueMat(g("hue").toDouble()))
        val c = g("contrast").toDouble()
        m = then(m, gain(c, c, c, 0.5 * (1 - c)))
        val bl = g("black").toDouble()
        m = then(m, gain(1 - bl, 1 - bl, 1 - bl, bl))
        val br = g("brightness").toDouble()
        m = then(m, gain(1.0, 1.0, 1.0, br))
        return m
    }

    private fun command(m: Mat): String {
        val sb = StringBuilder("service call SurfaceFlinger 1015 i32 1")
        for (col in 0..3) for (row in 0..3) {
            val v = if (row == 3) (if (col == 3) 1.0 else 0.0) else m[row * 4 + col]
            sb.append(" f ").append(String.format(Locale.US, "%.4f", v))
        }
        return sb.toString()
    }

    private val ex = Executors.newSingleThreadExecutor()
    @Volatile private var ver = 0

    /** Debounced apply (so dragging a slider doesn't spawn a process per tick). */
    fun applyAsync() {
        val v = ++ver
        ex.execute {
            Thread.sleep(150)
            if (v == ver) Shell.exec(command(build()))
        }
    }

    fun resetScreen() { ex.execute { Shell.exec("service call SurfaceFlinger 1015 i32 0") } }
}
