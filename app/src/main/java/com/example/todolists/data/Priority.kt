package com.example.todolists.data

enum class Priority(val storageValue: Int, val label: String) {
    SOMEDAY(0, "そのうちやる"),
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    companion object {
        val DEFAULT = MEDIUM
        fun fromStorage(value: Int): Priority =
            values().firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
