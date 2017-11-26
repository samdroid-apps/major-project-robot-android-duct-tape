package robot.eng.sam.today.robot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * Created by sam on 11/5/17.
 */

private val CM_TO_DP = 3f

class MapView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : View(context, attrs, defStyle) {
    var mRotation = 0.0
    var mLength = 10f * CM_TO_DP
    var mPositionLine = FloatArray(3)

    val mArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val mLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val mRobotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        mArrowPaint.color = Color.RED
        mArrowPaint.strokeWidth = 4f

        mLinePaint.color = Color.GREEN
        mLinePaint.strokeWidth = 2f

        mRobotPaint.color = Color.BLACK
    }

    fun updateRot(rot: Double) {
        mRotation = rot
        invalidate()
    }

    fun updateLength(cm: Int) {
        mLength = cm.toFloat() * CM_TO_DP
        invalidate()
    }

    fun updatePosition(position: FloatArray) {
        mPositionLine = position
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val x = 100f
        val y = 100f

        if (true) {
            var width = mPositionLine[0] * CM_TO_DP * 100
            var height = mPositionLine[2] * CM_TO_DP * 100
            canvas.drawLine(x, y, x+width, y+height, mLinePaint)
            Log.d("Position", mPositionLine.joinToString())
        }

        val x2 = x + mLength*Math.cos(mRotation).toFloat()
        val y2 = y + mLength*Math.sin(mRotation).toFloat()
        canvas.drawLine(x, y, x2, y2, mArrowPaint)
        canvas.drawRect(Rect(90, 90, 110, 110), mRobotPaint)
    }
}