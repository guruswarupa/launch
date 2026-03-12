package com.guruswarupa.launch.utils

import android.content.Context
import android.util.TypedValue
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.dpToPx

object DialogStyler {
    fun styleInput(context: Context, editText: EditText) {
        val horizontal = context.dpToPx(16)
        val vertical = context.dpToPx(14)
        editText.setTextColor(ContextCompat.getColor(context, R.color.dialog_text))
        editText.setHintTextColor(ContextCompat.getColor(context, R.color.dialog_text_secondary))
        editText.background = ContextCompat.getDrawable(context, R.drawable.dialog_input_background)
        editText.setPadding(horizontal, vertical, horizontal, vertical)
        editText.minimumHeight = context.dpToPx(52)
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }
}
