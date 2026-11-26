package com.studywithme.app.lock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

/** What the lock needs from the system, read fresh whenever the user comes back from a settings page. */
class LockPermissions(val accessibility: Boolean, val admin: Boolean, val notifications: Boolean) {
    /** The lock cannot work without these two. */
    val ready get() = accessibility && admin

    companion object {
        fun read(context: Context): LockPermissions {
            val am = context.getSystemService(AccessibilityManager::class.java)
            val a11y = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
            val admin = context.getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(context, AdminReceiver::class.java))
            return LockPermissions(a11y, admin, NotificationManagerCompat.from(context).areNotificationsEnabled())
        }
    }
}
