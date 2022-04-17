package dev.salavatov.multifs.sqlite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SqliteFSDatabaseHelper(context: Context, databaseName: String) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQLContract.Folders.CREATE_STATEMENT)
        db.execSQL(SQLContract.Folders.INSERT_ROOT_STATEMENT)
        db.execSQL(SQLContract.Files.CREATE_STATEMENT)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SqliteFSException("unexpected call of SqliteFsDbHelper.onUpgrade")
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SqliteFSException("unexpected call of SqliteFsDbHelper.onDowngrade")
    }
    companion object {
        const val DATABASE_VERSION = 1
    }
}