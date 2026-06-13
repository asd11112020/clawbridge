package com.clawbridge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView

class BlinkingLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val logoView: ImageView
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blinkScale = 1f
    private var eyeOffsetY = 0f

    private var blinkAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)

        logoView = ImageView(context).apply {
            setImageResource(R.drawable.ic_openclaw)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(logoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        eyePaint.apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        pupilPaint.apply {
            color = 0xFF1A1A2E.toInt()
            style = Paint.Style.FILL
        }
    }

    fun blink() {
        blinkAnimator?.cancel()
        blinkAnimator = ValueAnimator.ofFloat(1f, 0.1f, 1f).apply {
            duration = 250
            repeatCount = 1
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                blinkScale = animation.animatedValue as Float
                invalidate()
            }
            start()
        }

        val clickAnim = AnimationUtils.loadAnimation(context, R.anim.logo_click)
        val resetAnim = AnimationUtils.loadAnimation(context, R.anim.logo_reset)
        logoView.startAnimation(clickAnim)
        logoView.postDelayed({ logoView.startAnimation(resetAnim) }, 300)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawEyes(canvas)
    }

    private fun drawEyes(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val eyeCenterY = h * 0.42f + eyeOffsetY
        val eyeSpacing = w * 0.12f
        val eyeWidth = w * 0.13f
        val eyeHeight = h * 0.09f * blinkScale
        val pupilRadius = w * 0.035f

        val leftEyeX = w / 2f - eyeSpacing
        val rightEyeX = w / 2f + eyeSpacing

        canvas.drawOval(
            leftEyeX - eyeWidth, eyeCenterY - eyeHeight,
            leftEyeX + eyeWidth, eyeCenterY + eyeHeight,
            eyePaint
        )
        canvas.drawOval(
            rightEyeX - eyeWidth, eyeCenterY - eyeHeight,
            rightEyeX + eyeWidth, eyeCenterY + eyeHeight,
            eyePaint
        )

        if (blinkScale > 0.3f) {
            val pupilOffsetY = -eyeHeight * 0.15f
            canvas.drawCircle(leftEyeX, eyeCenterY + pupilOffsetY, pupilRadius, pupilPaint)
            canvas.drawCircle(rightEyeX, eyeCenterY + pupilOffsetY, pupilRadius, pupilPaint)
        }
    }
}
