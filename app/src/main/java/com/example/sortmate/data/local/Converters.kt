package com.example.sortmate.data.local

import androidx.room.TypeConverter
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.domain.model.SplitType

class Converters {

    @TypeConverter
    fun fromPresence(value: PresenceStatus): String = value.name

    @TypeConverter
    fun toPresence(value: String): PresenceStatus = PresenceStatus.from(value)

    @TypeConverter
    fun fromSplitType(value: SplitType): String = value.name

    @TypeConverter
    fun toSplitType(value: String): SplitType =
        SplitType.entries.firstOrNull { it.name == value } ?: SplitType.EQUAL

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").filter { it.isNotBlank() }
}
