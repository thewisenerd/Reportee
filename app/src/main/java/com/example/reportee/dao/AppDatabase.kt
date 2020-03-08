package com.example.reportee.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.reportee.models.Contact
import com.example.reportee.models.PhoneNumber
import com.example.reportee.models.VCardData

@Database(entities = [Contact::class, PhoneNumber::class, VCardData::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun phoneNumberDao(): PhoneNumberDao
    abstract fun vCardDataDao(): VCardDataDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reportee.db"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }

    @Transaction
    fun insertContact(contact: Contact, phoneNumbers: List<PhoneNumber>, vCardData: VCardData) {
        contactDao().insert(contact)
        phoneNumbers.forEach {
            phoneNumberDao().insert(it)
        }
        vCardDataDao().insert(vCardData)
    }

    @Transaction
    fun nuke() {
        contactDao().nuke()
        phoneNumberDao().nuke()
        vCardDataDao().nuke()
    }
}