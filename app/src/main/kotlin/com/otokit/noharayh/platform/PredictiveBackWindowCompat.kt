package com.otokit.noharayh.platform

import android.app.Activity
import android.os.Build
import android.view.View

/**
 * ColorOS 窗口兼容：在根 View 挂载后重新同步 Window 的公开属性。
 *
 * 已在 OPPO API 37 真机复现：初次挂载允许应用自行生成返回进度，但窗口没有收到
 * 对应的 MOVE，系统也不再发送连续进度。重新同步属性后，系统恢复发送进度。
 * 必须在挂载后执行；不访问私有标记，也不自行识别边缘手势或计算返回进度。
 * 页面、标题和抽屉仍统一使用 Activity 转发的系统返回回调。
 */
internal fun restoreColorOsPredictiveBackProgress(activity: Activity, root: View) {
    if (Build.VERSION.SDK_INT < 36 || !Build.MANUFACTURER.equals("OPPO", ignoreCase = true)) return

    root.post {
        if (!activity.isDestroyed && !activity.isFinishing && root.isAttachedToWindow) {
            activity.window.attributes = activity.window.attributes
        }
    }
}
