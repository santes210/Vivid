package com.vivid.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Tests the additive SQL migration; full Room schema validation still requires a build. */
class PostAltTextMigrationTest {
    @Test fun existingRowsSurviveAndReceiveEmptyAlternativeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE posts (id TEXT NOT NULL PRIMARY KEY, caption TEXT NOT NULL)")
                    db.execSQL("INSERT INTO posts (id, caption) VALUES ('p1', 'existing')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val db = helper.writableDatabase
            VividDatabase.MIGRATION_9_10.migrate(db)
            db.query("SELECT caption, altText FROM posts WHERE id = 'p1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("existing", it.getString(0))
                assertEquals("", it.getString(1))
            }
        } finally { helper.close() }
    }
}
