package com.vivid.app.di

import com.vivid.app.data.local.VividDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Guards the Room wiring: forward upgrades must use the explicit migration
 * chain. A destructive fallback would wipe cached chats.
 */
class DatabaseModuleContractTest {

    @Test
    fun `module source does not call destructive forward fallback`() {
        val source = locateDatabaseModuleSource()
        val withoutDowngrade = source.lineSequence()
            .filterNot { it.contains("fallbackToDestructiveMigrationOnDowngrade") }
            .joinToString("\n")
        assertFalse(
            "fallbackToDestructiveMigration() wipes chat history on a missed migration",
            withoutDowngrade.contains("fallbackToDestructiveMigration(")
        )
        assertTrueHasMigrations(source)
    }

    @Test
    fun `module uses the shared ALL_MIGRATIONS array`() {
        val source = locateDatabaseModuleSource()
        assert(source.contains("VividDatabase.ALL_MIGRATIONS")) {
            "DatabaseModule should apply VividDatabase.ALL_MIGRATIONS"
        }
        assertEquals(VividDatabase.NAME, "vivid_database")
    }

    private fun assertTrueHasMigrations(source: String) {
        assert(source.contains("addMigrations")) { "DatabaseModule must register migrations" }
    }

    private fun locateDatabaseModuleSource(): String {
        val candidates = listOf(
            File("src/main/java/com/vivid/app/di/DatabaseModule.kt"),
            File("app/src/main/java/com/vivid/app/di/DatabaseModule.kt"),
            File("../app/src/main/java/com/vivid/app/di/DatabaseModule.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not find DatabaseModule.kt from ${File(".").absolutePath}")
        return file.readText()
    }
}
