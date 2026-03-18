package com.guruswarupa.launch.utils

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog

fun Context.dpToPx(value: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

fun AlertDialog.Builder.setDialogInputView(
    context: Context,
    view: View,
    topDp: Int = 12,
    bottomDp: Int = 0,
    horizontalDp: Int = 20
): AlertDialog.Builder {
    val container = FrameLayout(context)
    val params = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    params.setMargins(
        context.dpToPx(horizontalDp),
        context.dpToPx(topDp),
        context.dpToPx(horizontalDp),
        context.dpToPx(bottomDp)
    )
    view.layoutParams = params
    container.addView(view)
    return this.setView(container)
}
