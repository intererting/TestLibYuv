package com.yuliyang.testlibyuv

import android.content.Context
import android.content.pm.ActivityInfo
import android.support.v4.app.Fragment

fun Context.isScreenPortrait(): Boolean {
    val ori = resources.configuration.orientation //获取屏幕方向
    return ori == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

fun Fragment.isScreenPortrait(): Boolean {
    val ori = resources.configuration.orientation //获取屏幕方向
    return ori == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}