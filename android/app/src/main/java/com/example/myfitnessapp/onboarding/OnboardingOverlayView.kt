package com.example.myfitnessapp.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.LayoutOnboardingOverlayBinding

class OnboardingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: LayoutOnboardingOverlayBinding
    private var steps = listOf<OnboardingStep>()
    private var currentStepIndex = 0
    private var onFinishListener: (() -> Unit)? = null

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#E6000000") // 90% Black for better isolation
    }

    private val eraserPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val highlightPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f // Thicker border
        color = Color.WHITE
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f // More glow
        color = Color.parseColor("#995E5CE6") // 60% accent_blue
        isAntiAlias = true
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.OUTER)
    }

    private var targetRect: RectF? = null

    init {
        binding = LayoutOnboardingOverlayBinding.inflate(LayoutInflater.from(context), this, true)
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true

        binding.btnNext.setOnClickListener { nextStep() }
        binding.btnBack.setOnClickListener { previousStep() }
        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnFinish.setOnClickListener { finishOnboarding() }
    }

    fun setSteps(steps: List<OnboardingStep>) {
        this.steps = steps
        post {
            updateStep()
        }
    }

    fun setOnFinishListener(listener: () -> Unit) {
        this.onFinishListener = listener
    }

    private fun updateStep() {
        if (currentStepIndex !in steps.indices) return

        val step = steps[currentStepIndex]
        binding.tvTitle.text = step.title
        binding.tvDescription.text = step.description
        binding.tvProgress.text = "${currentStepIndex + 1}/${steps.size}"

        binding.btnBack.visibility = if (currentStepIndex > 0) View.VISIBLE else View.GONE
        
        if (step.isFinalStep || currentStepIndex == steps.size - 1) {
            binding.btnNext.visibility = View.GONE
            binding.btnFinish.visibility = View.VISIBLE
        } else {
            binding.btnNext.visibility = View.VISIBLE
            binding.btnFinish.visibility = View.GONE
        }

        step.iconRes?.let {
            binding.ivIcon.setImageResource(it)
            binding.ivIcon.visibility = View.VISIBLE
        } ?: run {
            binding.ivIcon.visibility = View.GONE
        }

        repositionCard()
    }

    private fun repositionCard() {
        binding.cardContainer.alpha = 0f
        post {
            val screenHeight = height.toFloat()
            val cardHeight = binding.cardContainer.height.toFloat()
            
            if (screenHeight <= 0 || cardHeight <= 0) return@post

            val density = context.resources.displayMetrics.density
            
            val step = steps[currentStepIndex]
            targetRect = step.targetView?.let { view ->
                if (view.width <= 0 && view.visibility == View.VISIBLE) {
                    postDelayed({ repositionCard() }, 50)
                    return@let null
                }

                val location = IntArray(2)
                view.getLocationInWindow(location)
                
                val parentLocation = IntArray(2)
                getLocationInWindow(parentLocation)
                
                val x = (location[0] - parentLocation[0]).toFloat()
                val y = (location[1] - parentLocation[1]).toFloat()
                
                val padding = step.paddingDp * density
                
                RectF(
                    x - padding,
                    y - padding,
                    x + view.width + padding,
                    y + view.height + padding
                )
            }
            
            invalidate()

            val target = targetRect
            val finalY = if (target == null) {
                (screenHeight - cardHeight) / 2f
            } else {
                val margin = 40 * density // Margin from spotlight
                val spaceAbove = target.top
                val spaceBelow = screenHeight - target.bottom

                val cardY = if (spaceBelow > spaceAbove) {
                    val idealY = target.bottom + (spaceBelow - cardHeight) / 2f
                    idealY.coerceAtLeast(target.bottom + margin)
                } else {
                    val idealY = (spaceAbove - cardHeight) / 2f
                    idealY.coerceAtMost(target.top - cardHeight - margin)
                }

                val topPadding = 60 * density
                val bottomPadding = 100 * density
                cardY.coerceIn(topPadding, screenHeight - cardHeight - bottomPadding)
            }

            binding.cardContainer.animate()
                .y(finalY)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun nextStep() {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
            updateStep()
        }
    }

    private fun previousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--
            updateStep()
        }
    }

    private fun finishOnboarding() {
        onFinishListener?.invoke()
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)

        targetRect?.let { rect ->
            // Draw glow
            canvas.drawRoundRect(rect, 32f, 32f, glowPaint)
            // Draw spotlight hole
            canvas.drawRoundRect(rect, 32f, 32f, eraserPaint)
            // Draw highlight border
            canvas.drawRoundRect(rect, 32f, 32f, highlightPaint)
        }
    }
}
