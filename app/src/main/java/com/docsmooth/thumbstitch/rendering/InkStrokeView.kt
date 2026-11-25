package com.docsmooth.thumbstitch.rendering

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import com.docsmooth.thumbstitch.input.StrokeListener
import com.docsmooth.thumbstitch.input.StrokeSample

/**
 * Renders the in-progress finger stroke with AndroidX Ink. All Ink types are confined to this
 * class; callers observe input only through [StrokeListener].
 */
@SuppressLint("ViewConstructor")
class InkStrokeView(
    context: Context,
    var listener: StrokeListener? = null,
) : FrameLayout(context) {

    private val strokesView = InProgressStrokesView(context)
    private val brush = Brush.createWithColorIntArgb(
        family = BrushFamily(),
        colorIntArgb = Color.BLACK,
        size = 6f,
        epsilon = 0.1f,
    )
    private var activePointerId = -1
    private var activeStroke: InProgressStrokeId? = null

    init {
        addView(strokesView)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                activePointerId = event.getPointerId(event.actionIndex)
                activeStroke = strokesView.startStroke(event, activePointerId, brush)
                listener?.onStrokeStarted(sample(event))
            }
            MotionEvent.ACTION_MOVE -> {
                val id = activeStroke ?: return true
                strokesView.addToStroke(event, activePointerId, id)
                listener?.onStrokeMoved(sample(event))
            }
            MotionEvent.ACTION_UP -> {
                val id = activeStroke ?: return true
                strokesView.finishStroke(event, activePointerId, id)
                activeStroke = null
                listener?.onStrokeFinished(sample(event))
            }
            MotionEvent.ACTION_CANCEL -> {
                activeStroke?.let { strokesView.cancelStroke(it, event) }
                activeStroke = null
                listener?.onStrokeCancelled()
            }
        }
        return true
    }

    private fun sample(event: MotionEvent) =
        StrokeSample(event.x, event.y, event.eventTime)
}
