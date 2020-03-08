package com.example.reportee.models

import androidx.room.*
import ezvcard.Ezvcard
import ezvcard.VCard

@Entity(tableName = "contact")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "contact_id") val contactId: String
)

@Entity(tableName = "phone_number")
data class PhoneNumber(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "numeric_number") val numericNumber: String
)

@Entity(tableName = "vcard_data", indices = [Index("uid", unique = true)])
@TypeConverters(VCardConverter::class)
data class VCardData(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "vcard") val vcard: VCard,
    @ColumnInfo(name = "uid") val uid: String = vcard.uid.value
) {
    init {
        require(!vcard.uid.value.isNullOrBlank()) { "no uid found for vcard" }
    }
}

class VCardConverter {
    @TypeConverter
    fun vCardDeserializer(value: String?): VCard? {
        return value?.let {
            Ezvcard.parse(it).all().firstOrNull()
        }
    }

    @TypeConverter
    fun vCardSerializer(value: VCard?): String? {
        return value?.let {
            Ezvcard.write(it).go()
        }
    }
}