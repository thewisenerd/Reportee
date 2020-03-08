package com.example.reportee

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckedTextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.reportee.Constants.LOGTAG
import com.example.reportee.dao.AppDatabase
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_scrollable)

        findViewById<Button>(R.id.request_perm_button)?.setOnClickListener(this)
        findViewById<Button>(R.id.read_perm_button)?.setOnClickListener(this)
        findViewById<Button>(R.id.load_contacts_data)?.setOnClickListener(this)
        findViewById<Button>(R.id.nuke_contacts_data)?.setOnClickListener(this)
        findViewById<Button>(R.id.start_fg_service)?.setOnClickListener(this)
        findViewById<Button>(R.id.stop_fg_service)?.setOnClickListener(this)
        checkAndGetPermissions(this, false)
        refreshPermissionList(this)

        val si = Intent(this, ForegroundCallService::class.java)
        startForegroundService(si)
    }

    override fun onClick(v: View?) {
        if (v == null) {
            Log.d(LOGTAG, "view is null, returning")
            return
        }
        when (v.id) {
            R.id.request_perm_button -> {
                checkAndGetPermissions(this, true)
            }
            R.id.read_perm_button -> {
                refreshPermissionList(this)
            }
            R.id.load_contacts_data -> {
                loadContactsData(this)
            }
            R.id.nuke_contacts_data -> {
                nukeContactsData(this)
            }
            R.id.start_fg_service -> {
                val si = Intent(this, ForegroundCallService::class.java)
                startForegroundService(si)
            }
            R.id.stop_fg_service -> {
                val si = Intent(this, ForegroundCallService::class.java)
                stopService(si)
            }
            else -> {
                Log.d(LOGTAG, "idk what to do with this view: ${v.id}")
            }
        }
    }

    private fun refreshPermissionStatus(context: Context, permission: String, grant: Int?) {
        val view: CheckedTextView? = when (permission) {
            Manifest.permission.READ_PHONE_STATE -> {
                findViewById(R.id.perm_call_state_indicator)
            }
            Manifest.permission.READ_CALL_LOG -> {
                findViewById(R.id.perm_call_log_indicator)
            }
            Manifest.permission.READ_CONTACTS -> {
                findViewById(R.id.perm_contacts_indicator)
            }
            Manifest.permission.READ_EXTERNAL_STORAGE -> {
                findViewById(R.id.perm_external_storage_indicator)
            }
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                findViewById(R.id.perm_system_overlay_indicator)
            }
            else -> null
        }
        val drawable = when(grant) {
            PackageManager.PERMISSION_GRANTED -> ContextCompat.getDrawable(context, android.R.drawable.presence_online)
            PackageManager.PERMISSION_DENIED -> ContextCompat.getDrawable(context, android.R.drawable.presence_busy)
            else -> ContextCompat.getDrawable(context, android.R.drawable.presence_invisible)
        }
        view?.apply {
            checkMarkDrawable = drawable
        }
    }

    private fun refreshPermissionList(context: Context) {
        Constants.PERMISSION_LIST.forEach {
            refreshPermissionStatus(context, it, ContextCompat.checkSelfPermission(context, it))
        }
        val canDrawOverlays = if (Settings.canDrawOverlays(context)) {
            PackageManager.PERMISSION_GRANTED
        } else {
            PackageManager.PERMISSION_DENIED
        }
        refreshPermissionStatus(context, Manifest.permission.SYSTEM_ALERT_WINDOW, canDrawOverlays)
    }

    private fun checkAndGetPermissions(thisActivity: Activity, forceAsk: Boolean = false) {
        Log.d(LOGTAG, "checkAndGetPermissions yo")
        val requestPermissionList = Constants.PERMISSION_LIST.filter {
            ContextCompat.checkSelfPermission(thisActivity.applicationContext, it) != PackageManager.PERMISSION_GRANTED
        }
        val (nonPermissibleList, askList) = if (forceAsk) {
            emptyList<String>() to requestPermissionList
        } else {
            requestPermissionList.partition {
                ActivityCompat.shouldShowRequestPermissionRationale(thisActivity, it)
            }
        }
        if (nonPermissibleList.isNotEmpty()) {
            val dialog = AlertDialog.Builder(thisActivity).apply {
                title = "Require Permissions"
                setMessage("Permissions \"Call Logs\" and \"Phone\" are required for this application to work \"properly\", please provide the same from Settings.")
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, Constants.PERM_CB_REQUEST_ASKABLE)
                }
                setNegativeButton(android.R.string.no, null)
            }
            dialog.show()
        }
        if (askList.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                thisActivity,
                askList.toTypedArray(),
                Constants.PERM_CB_REQUEST_ASKABLE
            )
        }
        if (!Settings.canDrawOverlays(thisActivity)) {
            val dialog = AlertDialog.Builder(thisActivity).apply {
                title = "Require Overlay Permissions"
                setMessage("Permissions \"Draw System Overlay\" are required for contact overlay to work, please provide the same from Settings.")
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, Constants.PERM_CB_REQUEST_OVERLAY_WINDOW)
                }
                setNegativeButton(android.R.string.no, null)
            }
            dialog.show()

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            Constants.PERM_REQUEST_ASKABLE -> {
                permissions.forEachIndexed { index, permission ->
                    grantResults.getOrNull(index)?.let { grant ->
                        refreshPermissionStatus(this, permission, grant)
                    }
                }
            }
            else -> {
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(LOGTAG, "got back $requestCode yo")
        when (requestCode) {
            Constants.PERM_CB_REQUEST_ASKABLE, Constants.PERM_CB_REQUEST_OVERLAY_WINDOW -> {
                refreshPermissionList(this)
            }
            Constants.REQUEST_FILE_PICKER_RESULT -> {
                Log.d(LOGTAG, "got file awoog: ${data?.data?.path}")
            }
            else -> {
            }
        }
    }

    private fun loadContactsData(context: Context) = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val count = withContext(Dispatchers.IO) {
            db.vCardDataDao().getCount()
        }
        contact_list_count.text = "Contacts: $count"
    }

    private fun nukeContactsData(context: Context) = GlobalScope.launch(Dispatchers.Main) {
        val db = AppDatabase.getDatabase(context)
        nuke_contacts_data.isEnabled = false
        load_contacts_data.isEnabled = false
        contacts_nuke_progress.visibility = View.VISIBLE
        contacts_nuke_progress.isIndeterminate = true
        withContext(Dispatchers.IO) {
            db.nuke()
            delay(1000) // hmm?
        }
        nuke_contacts_data.isEnabled = true
        load_contacts_data.isEnabled = true
        contacts_nuke_progress.isIndeterminate = false
        contacts_nuke_progress.visibility = View.GONE
    }
}
