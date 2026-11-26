package com.studywithme.app.lock

/** Decides which app may be in the foreground while the lock is on. Default is to block. */
object LockPolicy {
    /** Never allowed, even if the user ticks them: they could switch the lock off or uninstall the app. */
    val NEVER_ALLOWED = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
    )

    /** Phone, in-call screen, emergency calls and the system surfaces that must keep working. */
    val ALWAYS_ALLOWED = setOf(
        "android", "com.android.systemui", "com.samsung.android.app.aodservice", // always-on display is not an app the user opened
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.incallui", "com.samsung.android.incallui",
        "com.android.phone", "com.android.server.telecom", "com.samsung.android.app.telephonyui",
        "com.android.emergency", "com.samsung.android.emergency", "com.sec.android.emergencymode.service",
    )

    /** [dynamicAllowed] is the current keyboard apps and default dialer, looked up at runtime. */
    fun isBlocked(pkg: String?, ownPackage: String, userAllowed: Set<String>, dynamicAllowed: Set<String>): Boolean = when {
        pkg == null -> false
        pkg in NEVER_ALLOWED -> true
        pkg == ownPackage || pkg in ALWAYS_ALLOWED || pkg in dynamicAllowed || pkg in userAllowed -> false
        else -> true
    }
}
