package ar.edu.uade.ronda.core

/** Reloj inyectable: los tests y el simulador usan tiempo virtual. */
fun interface Clock {
    fun nowMs(): Long
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
