package com.guruswarupa.launch.utils

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.util.TypedValue
import android.view.View

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
): AlertDialog.Builder = this.setView(
    view,
    context.dpToPx(horizontalDp),
    context.dpToPx(topDp),
    context.dpToPx(horizontalDp),
    context.dpToPx(bottomDp)
)
