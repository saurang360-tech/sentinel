package dev.titus.sentinel.monitor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppAccess {
    const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    const val NOTICE_VERSION = 7
    fun acknowledged(context: Context) = context.getSharedPreferences("sentinel-privacy", Context.MODE_PRIVATE)
        .getInt("noticeVersion", 0) == NOTICE_VERSION
    fun acknowledge(context: Context) {
        check(context.getSharedPreferences("sentinel-privacy", Context.MODE_PRIVATE).edit()
            .putInt("noticeVersion", NOTICE_VERSION).commit())
    }
    fun networkGranted(context: Context) = Build.VERSION.SDK_INT < 37 ||
        context.checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    fun canConnect(context: Context) = acknowledged(context) && networkGranted(context)
}
