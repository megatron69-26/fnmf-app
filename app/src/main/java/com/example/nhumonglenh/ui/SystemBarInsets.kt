package com.example.nhumonglenh.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

import com.example.nhumonglenh.R

/** Keeps application content outside status, navigation and display-cutout areas. */
object SystemBarInsets {

    data class InitialPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun apply(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.parseColor("#131722")
        activity.window.navigationBarColor = Color.parseColor("#131722")

        // Trên nền tối #131722, các icon phải có màu trắng sáng (isAppearanceLightStatusBars = false)
        WindowInsetsControllerCompat(activity.window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Lưu trữ initial padding ban đầu bằng keyed tag R.id.tag_initial_system_bar_padding
        // để không đè tag mặc định của View và chống cộng dồn padding khi recreate hoặc apply nhiều lần
        val initial = (root.getTag(R.id.tag_initial_system_bar_padding) as? InitialPadding) ?: InitialPadding(
            root.paddingLeft,
            root.paddingTop,
            root.paddingRight,
            root.paddingBottom
        ).also { root.setTag(R.id.tag_initial_system_bar_padding, it) }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val safeInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = initial.left + safeInsets.left,
                top = initial.top + safeInsets.top,
                right = initial.right + safeInsets.right,
                bottom = initial.bottom + safeInsets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
