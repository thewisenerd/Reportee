package com.example.reportee

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.reportee.Constants.LOGTAG
import com.example.reportee.dao.AppDatabase
import com.example.reportee.models.Contact
import com.example.reportee.models.PhoneNumber
import com.example.reportee.models.VCardData
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.property.Uid
import kotlinx.android.synthetic.main.contacts_importer.*
import kotlinx.coroutines.*
import java.util.*

class ContactsImporterActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts_importer)
        title = "Import Contacts"
        contact_progress.isIndeterminate = true
        contact_errors_detail.text = ""

        fun setProcessedText(processed: Int) {
            contact_processed_text.text = "Processed: ${processed}"
            contact_progress.progress = processed
        }

        var errored = 0
        fun setErrorText(index: Int, e: Exception) {
            errored += 1
            contact_error_text.text = "Errors: ${errored}"

            val newErrorText = StringBuilder(contact_errors_detail.text.toString())
            if (errored > 1) {
                newErrorText.append("\n")
            }
            newErrorText.append("$index: ${e.message}")
            contact_errors_detail.text = "$newErrorText"
        }

        val uri = intent?.data
            ?: return dialogAndExit(
                this,
                "Error init",
                "Error initializing, could not find uri to open"
            )

        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return dialogAndExit(
                this,
                "Need Permission",
                "Do not have read external storage permissions, provide."
            )
        }

        val db = AppDatabase.getDatabase(this)

        contentResolver.openInputStream(uri).use { inputStream ->
            val cards = try {
                Ezvcard.parse(inputStream).all()
            } catch (e: Exception) {
                Log.d(LOGTAG, "parsing vcard gave exception", e)
                return dialogAndExit(
                    this,
                    "Error parsing",
                    "parsing vcard gave exception: ${e.message}"
                )
            }

            contact_total_text.text = "Total: ${cards.size}"
            contact_processed_text.text = "Processed: 0"
            contact_error_text.text = "Errors: 0"
            contact_progress.max = cards.size
            contact_progress.isIndeterminate = false
            setProcessedText(0)

            close_importer.visibility = View.GONE
            close_importer.setOnClickListener(this)

            GlobalScope.launch(Dispatchers.Main) {
                cards.forEachIndexed { index, vCard ->
                    try {
                        processCard(vCard, db)
                    } catch (e: Exception) {
                        setErrorText(index, e)
                    } finally {
                        setProcessedText(index + 1)
                    }
                }

                close_importer.visibility = View.VISIBLE
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.close_importer -> {
                finish()
            }
            else -> {

            }
        }
    }

    private fun dialogAndExit(thisActivity: Activity, dialogTitle: String, dialogMessage: String) {
        val dialog = AlertDialog.Builder(thisActivity.applicationContext).apply {
            title = "$title; $dialogTitle"
            setMessage(dialogMessage)
            setPositiveButton(android.R.string.ok) { _, _ ->
                thisActivity.finish()
            }
        }
        dialog.show()
    }

    private suspend fun processCard(card: VCard, db: AppDatabase) {
        val uuid = if (card.uid == null || card.uid.value.isNullOrBlank()) {
            UUID.randomUUID().toString()
        } else {
            if (card.uid.value.startsWith("urn:uuid:")) {
                card.uid.value.substring("urn:uuid:".length)
            } else {
                UUID.randomUUID().toString()
            }
        }
        card.uid = Uid("urn:uuid:$uuid")
        require(uuid.length == 36) { "uuid length is NOT 36, $uuid [${uuid.length}]" }
        val contact = Contact(0, uuid)
        val phoneNumbers = mutableListOf<PhoneNumber>()
        card.telephoneNumbers.mapNotNull { tel ->
            tel.text.takeIf { !it.isNullOrBlank() }
                ?: tel.uri.number.takeIf { !it.isNullOrBlank() }
        }.filter {
            Utils.sanitizePhoneNumber(it).isNotBlank()
        }.forEach {
            phoneNumbers.add(PhoneNumber(0, uuid, it, Utils.sanitizePhoneNumber(it)))
        }
        val vCardData = VCardData(0, uuid, card)
        withContext(Dispatchers.IO) {
            db.insertContact(contact, phoneNumbers, vCardData)
        }
    }
}