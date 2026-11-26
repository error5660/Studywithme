package com.studywithme.app.lock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.studywithme.app.R

/** An active device admin cannot be uninstalled until the admin is switched off in Settings, which the lock blocks. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence = context.getString(R.string.admin_disable_warning)
}
