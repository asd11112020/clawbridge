package com.clawbridge

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView

class BlinkingLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val logoView: ImageView
    private var isBlinking = false

    init {
        setWillNotDraw(false)

        logoView = ImageView(context).apply {
            setImageResource(R.drawable.ic_openclaw)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(logoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun blink() {
        if (isBlinking) return
        isBlinking = true

        logoView.setImageResource(R.drawable.blink_frames)
        val anim = logoView.drawable as? AnimationDrawable
        anim?.start()

        logoView.postDelayed({
            logoView.setImageResource(R.drawable.ic_openclaw)
            isBlinking = false
        }, 450)
    }
}
