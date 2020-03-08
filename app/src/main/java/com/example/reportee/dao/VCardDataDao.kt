package com.example.reportee.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.reportee.models.VCardData

@Dao
interface VCardDataDao {
    @Query("delete from vcard_data where 1")
    fun nuke()

    @Insert
    fun insert(vararg contacts: VCardData)

    @Query("select count(*) from vcard_data")
    fun getCount(): Long

    @Query("select * from vcard_data where contact_id in (:contactIds)")
    fun get(contactIds: List<String>): List<VCardData>

    fun get(contactId: String) {
        val data = get(listOf(contactId))
        if (data.size > 1) {
            error("multiple vcards against uuid, unique constraint violation")
        }
        data.firstOrNull()
    }
}