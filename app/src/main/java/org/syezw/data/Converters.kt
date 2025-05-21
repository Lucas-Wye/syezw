package org.syezw.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Gson().toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            val listType = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(it, listType)
        }
    }

    // You might already have a converter for Date to Long if you use Date objects
    // @TypeConverter
    // fun fromTimestamp(value: Long?): Date? {
    //     return value?.let { Date(it) }
    // }

    // @TypeConverter
    // fun dateToTimestamp(date: Date?): Long? {
    //     return date?.time
    // }
}