package com.zyz4.gamepademu.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.zyz4.gamepademu.model.ButtonPosition

class PresetPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var buttons: List<ButtonPosition> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = -0x555556
        style = Paint.Style.FILL
    }

    fun setButtons(buttonPositions: List<ButtonPosition>) {
        buttons = buttonPositions
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (buttons.isEmpty()) return

        val w = width.toFloat() - paddingLeft - paddingRight
        val h = height.toFloat() - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return

        val gridW = 120f
        val gridH = 53f
        val scaleX = w / gridW
        val scaleY = h / gridH
        val scale = minOf(scaleX, scaleY)
        val offsetX = paddingLeft + (w - gridW * scale) / 2f
        val offsetY = paddingTop + (h - gridH * scale) / 2f

        for (btn in buttons) {
            if (!btn.visible) continue
            val cx = offsetX + (btn.x + btn.width / 2f) * scale
            val cy = offsetY + (btn.y + btn.height / 2f) * scale
            val r = (minOf(btn.width, btn.height) * scale * 0.35f).coerceAtLeast(2f)
            canvas.drawCircle(cx, cy, r, paint)
        }
    }
}
