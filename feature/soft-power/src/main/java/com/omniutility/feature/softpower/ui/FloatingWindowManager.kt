package com.omniutility.feature.softpower.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.omniutility.feature.softpower.data.SoftPowerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

class FloatingWindowManager(
    private val context: Context,
    private val preferenceFlow: StateFlow<SoftPowerPreferences>,
    private val onButtonClick: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    
    private val isOnRightEdgeFlow = MutableStateFlow(false)
    private val isDraggingFlow = MutableStateFlow(false)
    private var snapAnimator: ValueAnimator? = null
    private var currentParams: WindowManager.LayoutParams? = null

    fun show() {
        if (composeView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 500
        }
        currentParams = params

        composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }

            setContent {
                val prefs by preferenceFlow.collectAsState()
                val isOnRightEdge by isOnRightEdgeFlow.collectAsState()
                val isDragging by isDraggingFlow.collectAsState()

                LaunchedEffect(prefs.buttonSize) {
                    // Slight delay to allow Compose to measure new size before calculating screen bounds
                    kotlinx.coroutines.delay(50)
                    snapToEdge(params)
                }

                FloatingButton(
                    opacity = prefs.buttonOpacity,
                    sizeDp = prefs.buttonSize,
                    isOnRightEdge = isOnRightEdge,
                    isDragging = isDragging
                )
            }

            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            snapAnimator?.cancel()
                            isDraggingFlow.value = true
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                            params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                            windowManager.updateViewLayout(composeView, params)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            isDraggingFlow.value = false
                            val deltaX = Math.abs(event.rawX - initialTouchX)
                            val deltaY = Math.abs(event.rawY - initialTouchY)
                            if (deltaX < 10 && deltaY < 10) {
                                onButtonClick()
                                snapToEdge(params) // Snap back if it was just a tiny tap movement
                            } else {
                                snapToEdge(params)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    v.systemGestureExclusionRects = listOf(Rect(0, 0, right - left, bottom - top))
                }
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = composeView?.width ?: 0
        
        val isRight = params.x + viewWidth / 2 >= screenWidth / 2
        isOnRightEdgeFlow.value = isRight
        
        val targetX = if (isRight) screenWidth - viewWidth else 0
        
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (_: Exception) {}
            }
            start()
        }
    }

    fun dismiss() {
        snapAnimator?.cancel()
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            composeView = null
        }
        currentParams = null
    }
}

@Composable
fun FloatingButton(opacity: Float, sizeDp: Int, isOnRightEdge: Boolean, isDragging: Boolean) {
    val size = sizeDp.dp
    
    val bgColor = Color(0xFFF0C38E) // Orix Peach
    val iconColor = Color(0xFF312C51) // Orix Dark Indigo

    // Animate the corner percentage: full circle (50%) when dragging, flat (0%) on attached side when snapped
    val flatCornerPercent by animateIntAsState(
        targetValue = if (isDragging) 50 else 0,
        animationSpec = tween(durationMillis = 200),
        label = "CornerAnimation"
    )
    
    val shape = if (isOnRightEdge) {
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStartPercent = 50, bottomStartPercent = 50, topEndPercent = flatCornerPercent, bottomEndPercent = flatCornerPercent
        )
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStartPercent = flatCornerPercent, bottomStartPercent = flatCornerPercent, topEndPercent = 50, bottomEndPercent = 50
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .alpha(opacity)
            .clip(shape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Screen",
            tint = iconColor,
            modifier = Modifier.size(size * 0.65f) // Increased icon size from 0.45f
        )
    }
}
