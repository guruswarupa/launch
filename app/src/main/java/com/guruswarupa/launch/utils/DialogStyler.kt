package com.guruswarupa.launch.utils

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.TypographyManager

object DialogStyler {
    fun styleInput(context: Context, editText: EditText) {
        val horizontal = context.dpToPx(16)
        val vertical = context.dpToPx(14)
        editText.setTextColor(Color.WHITE)
        editText.setHintTextColor(Color.parseColor("#B0B0B0"))
        editText.background = ContextCompat.getDrawable(context, R.drawable.dialog_input_background)
        editText.setPadding(horizontal, vertical, horizontal, vertical)
        editText.minimumHeight = context.dpToPx(52)
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    fun styleDialog(dialog: AlertDialog) {
        val context = dialog.context
        val themeColor = TypographyManager.getConfiguredFontColor(context) ?: Color.WHITE

        dialog.setOnShowListener {
            // Style Title - Try multiple possible IDs for different Android versions/themes
            val titleView = dialog.findViewById<TextView>(context.resources.getIdentifier("alertTitle", "id", "android"))
                ?: dialog.findViewById<TextView>(android.R.id.title)
            
            titleView?.setTextColor(themeColor)

            // Style Buttons
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(themeColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#B0B0B0"))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#B0B0B0"))
        }
    }

    /**
     * Creates an adapter for AlertDialog list items that ensures white text
     */
    fun createWhiteTextAdapter(context: Context, items: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(context, android.R.layout.select_dialog_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                if (view is TextView) {
                    view.setTextColor(Color.WHITE)
                }
                return view
            }
        }
    }
}
