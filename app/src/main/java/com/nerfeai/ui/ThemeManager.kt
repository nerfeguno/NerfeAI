package com.nerfeai.ui

import android.app.Activity
import android.graphics.Color
import android.view.View

class ThemeManager(
    private val activity: Activity
) {

    var isDarkTheme: Boolean = true

    val darkBackground =
        Color.rgb(33, 33, 33)

    val darkPanel =
        Color.rgb(42, 42, 42)

    val darkCard =
        Color.rgb(48, 48, 48)

    val lightBackground =
        Color.rgb(248, 248, 248)

    val lightPanel =
        Color.WHITE

    val lightCard =
        Color.rgb(239, 239, 239)

    fun backgroundColor(): Int =
        if (isDarkTheme) {
            darkBackground
        } else {
            lightBackground
        }

    fun panelColor(): Int =
        if (isDarkTheme) {
            darkPanel
        } else {
            lightPanel
        }

    fun cardColor(): Int =
        if (isDarkTheme) {
            darkCard
        } else {
            lightCard
        }

    fun textColor(): Int =
        if (isDarkTheme) {
            Color.rgb(
                236,
                236,
                236
            )
        } else {
            Color.rgb(
                35,
                35,
                35
            )
        }

    fun secondaryTextColor(): Int =
        if (isDarkTheme) {
            Color.rgb(
                165,
                165,
                165
            )
        } else {
            Color.rgb(
                105,
                105,
                105
            )
        }

    fun applySystemBars() {

        activity.window.statusBarColor =
            backgroundColor()

        activity.window.navigationBarColor =
            backgroundColor()

        activity.window.decorView.systemUiVisibility =
            if (isDarkTheme) {
                0
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
    }

    fun dp(
        value: Int
    ): Int {

        return (
            value *
                    activity.resources
                        .displayMetrics
                        .density
        ).toInt()
    }
}