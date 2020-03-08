package com.example.reportee.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.reportee.models.Contact

@Dao
interface ContactDao {
    @Query("delete from contact where 1")
    fun nuke()

    @Insert
    fun insert(vararg contacts: Contact)
}