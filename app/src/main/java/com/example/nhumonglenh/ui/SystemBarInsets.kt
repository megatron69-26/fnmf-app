package com.example.nhumonglenh.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

/** Keeps application content outside status, navigation and display-cutout areas. */
object SystemBarInsets {
    fun apply(activity: Activity, root: View, useLightStatusIcons: Boolean = false) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, root).apply {
            isAppearanceLightStatusBars = useLightStatusIcons
            isAppearanceLightNavigationBars = useLightStatusIcons
        }

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val safeInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = initialLeft + safeInsets.left,
                top = initialTop + safeInsets.top,
                right = initialRight + safeInsets.right,
                bottom = initialBottom + safeInsets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
