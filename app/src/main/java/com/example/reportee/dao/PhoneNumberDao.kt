package com.example.reportee.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.reportee.models.PhoneNumber

@Dao
interface PhoneNumberDao {
    @Query("delete from phone_number where 1")
    fun nuke()

    @Insert
    fun insert(vararg numbers: PhoneNumber)

    @Query("select * from phone_number where numeric_number like :number")
    fun endsWithInternal(number: String): List<PhoneNumber>

    fun endsWith(number: String) = endsWithInternal(if (number.startsWith("%")) {
        number
    } else {
        "%${number}"
    })
}