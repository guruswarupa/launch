package com.guruswarupa.launch.utils

import android.content.Context
import android.util.TypedValue
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R

object DialogStyler {
    fun styleInput(context: Context, editText: EditText) {
        val horizontal = dp(context, 16)
        val vertical = dp(context, 14)
        editText.setTextColor(ContextCompat.getColor(context, R.color.dialog_text))
        editText.setHintTextColor(ContextCompat.getColor(context, R.color.dialog_text_secondary))
        editText.background = ContextCompat.getDrawable(context, R.drawable.dialog_input_background)
        editText.setPadding(horizontal, vertical, horizontal, vertical)
        editText.minimumHeight = dp(context, 52)
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
