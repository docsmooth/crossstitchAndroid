package com.docsmooth.thumbstitch.input

/** Ink-independent sample of a finger stroke, in view pixel coordinates. */
data class StrokeSample(val x: Float, val y: Float, val timeMillis: Long)

/** Boundary through which stroke input leaves the Ink layer. Game rules depend only on this. */
interface StrokeListener {
    fun onStrokeStarted(sample: StrokeSample)
    fun onStrokeMoved(sample: StrokeSample)
    fun onStrokeFinished(sample: StrokeSample)
    fun onStrokeCancelled()
}
