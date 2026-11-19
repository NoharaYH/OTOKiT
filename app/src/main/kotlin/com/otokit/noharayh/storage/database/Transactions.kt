package com.otokit.noharayh.storage.database

import android.database.sqlite.SQLiteDatabase

internal inline fun <T> SQLiteDatabase.writeTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
