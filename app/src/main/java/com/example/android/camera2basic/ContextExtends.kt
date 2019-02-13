package com.example.android.camera2basic

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * 获取网络缓存路径
 */
fun Context.provideNetCache(): File? {
    val cacheDir = cacheDir
    cacheDir?.apply {
        if (!this.exists()) {
            this.mkdirs()
        }
        val result = File(this, "netCache")
        return if (!result.mkdirs() && (!result.exists() || !result.isDirectory)) null else result
    }
    return null
}

/**
 * 外部存储文件缓存路径
 */
@Throws(RuntimeException::class)
fun Context.provideExternalFileCache(): File? {
    if (!isSDCardEnable) {
        throw RuntimeException("存储卡异常")
    }
    externalCacheDir?.apply {
        if (!this.exists()) {
            this.mkdirs()
        }
        val result = File(this, "fileCache")
        return if (!result.mkdirs() && (!result.exists() || !result.isDirectory)) null else result
    }
    return null
}

/**
 * 文件缓存路径
 */
fun Context.provideFileCache(): File? {
    cacheDir?.apply {
        if (!this.exists()) {
            this.mkdirs()
        }
        val result = File(this, "fileCache")
        return if (!result.mkdirs() && (!result.exists() || !result.isDirectory)) null else result
    }
    return null
}

/**
 * 是否6.0以上
 */
fun Context.isVersion6Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

/**
 * 是否7.0以上
 */
fun Context.isVersion7Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

/**
 * 是否8.0以上
 */
fun Context.isVersion8Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

/**
 * 是否5.0以上
 */
fun Context.isVersion5Above() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

///*********************************扩展属性*****************************///

inline val Context.isSDCardEnable: Boolean
    get() = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

/**
 * 获取状态栏的高度
 */
inline val Context.statusBarHeight: Int
    get() {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

/**
 * 获取屏幕宽度
 */
inline val Context.screenWidth: Int
    get() {
        val wm: WindowManager? = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.apply {
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)
            return metrics.widthPixels
        }
        return 0
    }


/**
 * 获取屏幕高度
 */
inline val Context.screenHeightIncludeStatusBar: Int
    get() {
        val wm: WindowManager? = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.apply {
            val point = Point()
            wm.defaultDisplay.getRealSize(point)
            return point.y
        }
        return 0
    }

/**
 * 获取屏幕宽度
 */
inline val Context.screenWidthIncludeStatusBar: Int
    get() {
        val wm: WindowManager? = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.apply {
            val point = Point()
            wm.defaultDisplay.getRealSize(point)
            return point.x
        }
        return 0
    }

/**
 * 获取屏幕高度(不包括底部导航栏)
 */
inline val Context.screenHeight: Int
    get() {
        val wm: WindowManager? = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.apply {
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)
            return metrics.heightPixels
        }
        return 0
    }


/**
 * appName
 */
inline val Context.appName: String
    get() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val labelRes = packageInfo.applicationInfo.labelRes
            return resources.getString(labelRes)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

/**
 * versionName
 */
inline val Context.versionName: String
    get() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            return packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

/**
 * processName
 */
inline val Context.processName: String
    get() {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader("/proc/" + Process.myPid() + "/cmdline"))
            var processName = reader.readLine()
            if (!TextUtils.isEmpty(processName)) {
                processName = processName.trim { it <= ' ' }
            }
            return processName
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        } finally {
            try {
                reader?.close()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
        return ""
    }

/**
 * app是否存活
 */
inline val Context.isAppAlive: Boolean
    get() {
        val activityManager: ActivityManager? = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.apply {
            for (i in runningAppProcesses.indices) {
                if (runningAppProcesses[i].processName == packageName) {
                    return true
                }
            }
        }
        return false
    }

/**
 * 当前Activity是否在栈顶
 */
inline val Context.isTopActivity: Boolean
    get() {
        val activityManager: ActivityManager? = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.apply {
            val tasksInfo = getRunningTasks(1)
            return tasksInfo.size > 0 && packageName == tasksInfo[0].topActivity.packageName
        }
        return false
    }

/**
 * GPS是否打开
 */
inline val Context.isGpsOpen: Boolean
    get() {
        var isOpen: Boolean = false
        val locationManager: LocationManager? = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locationManager?.apply {
            isOpen = isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
        return isOpen
    }