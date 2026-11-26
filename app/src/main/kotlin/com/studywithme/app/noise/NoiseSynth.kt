package com.studywithme.app.noise

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Builds seamless noise loops in code, so no audio files need to be bundled. */
object NoiseSynth {
    const val RATE = 22_050
    const val RAIN = 1
    const val CAFE = 2
    const val FAN = 3

    /** One loop of mono 16-bit samples; the end flows into the start without a click. */
    fun generate(kind: Int, seconds: Int = 8): ShortArray {
        val n = RATE * seconds
        val fade = RATE / 2
        val raw = DoubleArray(n + fade)
        val rnd = Random(kind * 7919)
        var lp = 0.0
        var brown = 0.0
        var drop = 0.0
        var clink = 0.0
        var clinkPhase = 0.0
        val phases = DoubleArray(3) { rnd.nextDouble() * 2 * PI }
        for (i in raw.indices) {
            val white = rnd.nextDouble() * 2 - 1
            val t = i.toDouble() / RATE
            raw[i] = when (kind) {
                RAIN -> {
                    lp += 0.45 * (white - lp)                           // soft hiss
                    if (rnd.nextDouble() < 0.0009) drop = 0.5 + rnd.nextDouble() * 0.5
                    drop *= 0.992
                    lp * 0.6 + drop * white * 0.8                        // droplets
                }
                CAFE -> {
                    lp += 0.12 * (white - lp)                           // low murmur
                    val chatter = 0.55 + 0.25 * sin(2 * PI * 0.4 * t + phases[0]) + 0.15 * sin(2 * PI * 0.9 * t + phases[1]) + 0.1 * sin(2 * PI * 1.7 * t + phases[2])
                    if (rnd.nextDouble() < 0.00003) { clink = 0.25; clinkPhase = 0.0 }
                    clink *= 0.9993; clinkPhase += 2 * PI * 2600 / RATE
                    lp * 1.6 * chatter + clink * sin(clinkPhase)
                }
                FAN -> {
                    brown = (brown + 0.02 * white) / 1.02              // deep rumble
                    brown * 5.0 * (1 + 0.08 * sin(2 * PI * 0.3 * t)) + 0.04 * sin(2 * PI * 110 * t)
                }
                else -> white * 0.3
            }
        }
        // crossfade the extra tail into the start so the loop point is continuous
        val out = DoubleArray(n) { raw[it] }
        for (i in 0 until fade) { val a = i.toDouble() / fade; out[i] = a * raw[i] + (1 - a) * raw[n + i] }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-6)
        return ShortArray(n) { (out[it] / peak * 0.6 * Short.MAX_VALUE).toInt().toShort() }
    }

}
