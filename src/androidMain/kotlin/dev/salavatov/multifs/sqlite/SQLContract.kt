package dev.salavatov.multifs.sqlite

internal object SQLContract {
    object Folders {
        const val TABLE_NAME = "Folders"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_PARENT = "parent"

        val CREATE_STATEMENT = """
            CREATE TABLE $TABLE_NAME (
              $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
              $COLUMN_NAME TEXT NOT NULL,
              $COLUMN_PARENT REFERENCES $TABLE_NAME($COLUMN_ID) ON DELETE CASCADE,
              UNIQUE ($COLUMN_NAME, $COLUMN_PARENT),
              CHECK($COLUMN_NAME <> '' or $COLUMN_PARENT is NULL)
            );
            """.trimIndent()

        const val ROOT_ID = 0
        val INSERT_ROOT_STATEMENT = """
            INSERT INTO $TABLE_NAME($COLUMN_ID, $COLUMN_NAME, $COLUMN_PARENT) VALUES ($ROOT_ID, '', null);
            """.trimIndent()
    }

    object Files {
        const val TABLE_NAME = "Files"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_PARENT = "parent"
        const val COLUMN_DATA = "data"

        val CREATE_STATEMENT = """
            CREATE TABLE $TABLE_NAME (
              $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
              $COLUMN_NAME TEXT NOT NULL,
              $COLUMN_DATA BLOB NOT NULL,
              $COLUMN_PARENT REFERENCES ${Folders.TABLE_NAME}(${Folders.COLUMN_ID}) ON DELETE CASCADE,
              UNIQUE($COLUMN_NAME, $COLUMN_PARENT),
              CHECK($COLUMN_NAME <> '')
            );
            """.trimIndent()
    }
}
