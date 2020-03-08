package com.example.reportee

import android.Manifest

object Constants {
    const val LOGTAG = "Reportee"

    private const val PERM_REQUEST_NONE = 16384
    const val PERM_REQUEST_ASKABLE = PERM_REQUEST_NONE + 1
    const val PERM_CB_REQUEST_ASKABLE = PERM_REQUEST_NONE + 2
    const val PERM_CB_REQUEST_OVERLAY_WINDOW = PERM_REQUEST_NONE + 3

    private const val REQUEST_FILE_PICKER = 32768
    const val REQUEST_FILE_PICKER_RESULT = REQUEST_FILE_PICKER + 1

    val PERMISSION_LIST = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    const val INTENT_EXTRA_PHONE_NUMBER = "incoming_number"

    private const val dbName = "reportee.db"

    const val NOTIFICATION_CHANNEL_ID_DEFAULT = "reporteeNotificationChannelDefault"
}