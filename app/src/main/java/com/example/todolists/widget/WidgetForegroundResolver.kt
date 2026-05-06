package com.example.todolists.widget

import android.content.Context
import android.graphics.Color
import com.example.todolists.R
import com.example.todolists.data.WidgetBackgroundSettings
import com.example.todolists.data.WidgetForegroundMode

/**
 * Resolves the primary / secondary foreground colours used across the
 * widget chrome and the list rows. Both [BaseTaskWidgetReceiver] (for the
 * title, header icons, empty message) and [BaseTaskListFactory] (for the
 * per-row title / meta text) call into this so the user's chosen mode
 * applies consistently to every text element.
 */
internal object WidgetForegroundResolver {

    fun primary(context: Context, settings: WidgetBackgroundSettings): Int =
        primary(
            context,
            settings.foregroundMode,
            settings.customForegroundColor,
            hasBg = settings.uri != null,
        )

    fun primary(
        context: Context,
        mode: WidgetForegroundMode,
        customColor: Int,
        hasBg: Boolean,
    ): Int = when (mode) {
        WidgetForegroundMode.AUTO ->
            if (hasBg) context.getColor(R.color.widget_text_on_image)
            else context.getColor(R.color.widget_text_primary)
        WidgetForegroundMode.LIGHT -> context.getColor(R.color.widget_text_on_image)
        WidgetForegroundMode.DARK -> Color.BLACK
        WidgetForegroundMode.CUSTOM -> customColor
    }

    fun secondary(context: Context, settings: WidgetBackgroundSettings): Int =
        secondary(
            context,
            settings.foregroundMode,
            settings.customForegroundColor,
            hasBg = settings.uri != null,
        )

    fun secondary(
        context: Context,
        mode: WidgetForegroundMode,
        customColor: Int,
        hasBg: Boolean,
    ): Int = when (mode) {
        WidgetForegroundMode.AUTO ->
            if (hasBg) context.getColor(R.color.widget_text_on_image_dim)
            else context.getColor(R.color.widget_text_secondary)
        WidgetForegroundMode.LIGHT -> context.getColor(R.color.widget_text_on_image_dim)
        WidgetForegroundMode.DARK -> Color.parseColor("#FF555555")
        // Dim the user's custom colour to ~70% alpha for the secondary line.
        WidgetForegroundMode.CUSTOM ->
            (customColor and 0x00FFFFFF) or (0xB0 shl 24)
    }
}
