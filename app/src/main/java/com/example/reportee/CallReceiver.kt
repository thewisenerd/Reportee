package com.example.reportee

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.TextView
import com.example.reportee.Constants.LOGTAG
import com.example.reportee.dao.AppDatabase
import ezvcard.Ezvcard
import ezvcard.VCard
import kotlinx.coroutines.*


class CallReceiver : BroadcastReceiver() {
    companion object {
        private var dialogInstance: View? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(LOGTAG, "got intent for context boolala")
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state.isNullOrBlank()) {
            Log.d(LOGTAG, "state is null, $state; returning")
            return
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                handleRingingState(context, intent)
            }
            TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                handleIdleState(context, intent)
            }
        }
    }

    private fun handleRingingState(context: Context, intent: Intent) {
        if (dialogInstance != null) {
            Log.d(LOGTAG, "dialog is already open, returning")
            return
        }

        val phoneNumber = if (intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                .takeIf { !it.isNullOrBlank() }
        } else {
            null
        }
        if (phoneNumber.isNullOrBlank()) {
            Log.d(LOGTAG, "intent does not have phoneNumber, returning")
        } else {
            createCallDialog(context, intent, phoneNumber)
        }
    }

    private fun handleIdleState(context: Context, intent: Intent) {
        if (dialogInstance == null) {
            return
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(dialogInstance)
        dialogInstance = null
    }

    private fun createCallDialog(context: Context, intent: Intent, phoneNumber: String) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        val dialog: View?
        synchronized(this) {
            dialog = inflater.inflate(R.layout.call_dialog, null)
            dialogInstance = dialog
        }
        if (dialog == null) {
            Log.d(LOGTAG, "unable to inflate view, returning")
            return
        }

        Utils.populateDialogInfo(context, dialog, Utils.sanitizePhoneNumber(phoneNumber))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val dm = DisplayMetrics()
        wm.defaultDisplay?.let { dd ->
            dd.getMetrics(dm)

            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.width = (dm.widthPixels * 0.8).toInt()
            layoutParams.x = (dm.widthPixels - layoutParams.width) / 2
            layoutParams.y = (dm.heightPixels * 0.5).toInt()
        } ?: kotlin.run {
            layoutParams.gravity = Gravity.NO_GRAVITY
            layoutParams.x = 0
            layoutParams.y = 100
        }

        dialog.setOnTouchListener(DragViewAnimator(wm, dialog, layoutParams))
        wm.addView(dialog, layoutParams)
    }

}

object Utils {
    private fun searchableNumber(phoneNumber: String): String {
        return if (phoneNumber.length > 8) {
            phoneNumber.substring(phoneNumber.length - 8)
        } else {
            phoneNumber
        }
    }

    fun getPhoneContacts(context: Context, phoneNumber: String): Collection<String> {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(searchableNumber(phoneNumber)))
        val cards = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(uri, arrayOf(PhoneLookup.LOOKUP_KEY), null, null, null)?.use { query ->
            var count = query.count
            while (query.moveToNext() && count-- > 0) {
                val lookupKey = query.getString(query.getColumnIndex(PhoneLookup.LOOKUP_KEY))
                val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
                context.contentResolver.openAssetFileDescriptor(lookupUri, "r")?.use { fd ->
                    fd.createInputStream()?.use { fis ->
                        cards.add(lookupKey to String(fis.readBytes()))
                    }
                }
            }
        }
        return cards.toMap().values
    }

    suspend fun getDatabaseContacts(context: Context, phoneNumber: String, db: AppDatabase) = coroutineScope {
        val phoneNumbers = withContext(Dispatchers.IO) {
            db.phoneNumberDao().endsWith(searchableNumber(phoneNumber))
        }
        val contactIds = phoneNumbers.map { it.contactId }.distinct()
        val vCardDataList = withContext(Dispatchers.IO) {
            db.vCardDataDao().get(contactIds)
        }

        vCardDataList.map { it.vcard }
    }

    fun sanitizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.filter {
            it.isDigit()
        }
    }

    fun populateDialogInfo(context: Context, dialog: View, phoneNumber: String) = runBlocking {
        val contacts = mutableListOf<VCard>()

        getPhoneContacts(context, phoneNumber).flatMap { vcf ->
            Ezvcard.parse(vcf).all()
        }.filterNotNull().let {
            contacts.addAll(it)
        }

        val db = AppDatabase.getDatabase(context)
        getDatabaseContacts(context, phoneNumber, db).let {
            contacts.addAll(it)
        }

        createCallDialog(context, dialog, contacts)
    }

    // This is hacky af. we ideally should not have multiple cards for a user.
    fun createCallDialog(context: Context, view: View, cards: List<VCard>) {
        val names = mutableSetOf<String>()
        val addresses = mutableSetOf<String>()
        val notes = mutableSetOf<String>()
        val orgs = mutableSetOf<String>()
        val titles = mutableSetOf<String>()
        val emails = mutableSetOf<String>()

        val dName = view.findViewById<TextView>(R.id.name)
        val dNotes = view.findViewById<TextView>(R.id.notes)
        val dAddress = view.findViewById<TextView>(R.id.address)
        val dOrganization = view.findViewById<TextView>(R.id.org_company)
        val dTitle = view.findViewById<TextView>(R.id.org_title)
        val dEmail = view.findViewById<TextView>(R.id.email)

        val dAddressDivider = view.findViewById<View>(R.id.address_divider)
        val dOrgDivider = view.findViewById<View>(R.id.org_divider)
        val dEmailDivider = view.findViewById<View>(R.id.email_divider)

        cards.forEach { card ->
            val name: String? = card.formattedName.value?.takeIf {
                it.isNotBlank()
            } ?: (card.structuredName.given + " " + card.structuredName.family)
            if (!name.isNullOrBlank()) {
                names.add(name)
            }
            notes.addAll(card.notes?.map { it.value } ?: emptyList())
            addresses.addAll(card.addresses?.map { it.streetAddress } ?: emptyList())
            orgs.addAll(card.organization?.values ?: emptyList())
            titles.addAll(card.titles?.map { it.value } ?: emptyList())
            emails.addAll(card.emails?.map { it.value } ?: emptyList())
        }
        if (names.isEmpty()) {
            names.add("?")
        }

        dName.text = names.joinToString("\n")
        textViewOrNone(dNotes, notes)
        textViewOrNone(dAddress, addresses)
        textViewOrNone(dOrganization, orgs)
        textViewOrNone(dTitle, titles)
        textViewOrNone(dEmail, emails)
        if (orgs.isEmpty() && titles.isEmpty()) {
            dOrgDivider.visibility = View.GONE
        }
        if (addresses.isEmpty()) {
            dAddressDivider.visibility = View.GONE
        }
        if (emails.isEmpty()) {
            dEmailDivider.visibility = View.GONE
        }
    }

    private fun textViewOrNone(view: TextView, data: Collection<String>) {
        if (data.isNotEmpty()) {
            view.text = data.joinToString("\n")
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
    }
}

class DragViewAnimator(
    private val windowManager: WindowManager,
    private val view: View,
    private val layoutParams: WindowManager.LayoutParams
) : View.OnTouchListener {
    private var initialTouchX: Float = -1F
    private var initialTouchY: Float = -1F

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val handled: Boolean
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val initialX = layoutParams.x
                val finalX = (layoutParams.x + (event.x - initialTouchX)).toInt()
                val pvhX = PropertyValuesHolder.ofInt("x", initialX, finalX)

                val initialY = layoutParams.y
                val finalY = (layoutParams.y + (event.y - initialTouchY)).toInt()
                val pvhY = PropertyValuesHolder.ofInt("y", initialY, finalY)

                val translator = ValueAnimator.ofPropertyValuesHolder(pvhX, pvhY)

                translator.addUpdateListener { valueAnimator: ValueAnimator ->
                    layoutParams.x = (valueAnimator.getAnimatedValue("x") as Int)
                    layoutParams.y = (valueAnimator.getAnimatedValue("y") as Int)
                    windowManager.updateViewLayout(view, layoutParams)
                }

                translator.duration = 300
                translator.start()

                handled = true
            }
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y

                handled = true
            }
            else -> {
                handled = false
            }
        }
        return handled
    }
}