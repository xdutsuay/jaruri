package com.example.moneymanager.tunnel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.tunnel.data.TunnelAppRuleEntity
import com.example.moneymanager.tunnel.data.TunnelDomainRuleEntity
import com.example.moneymanager.tunnel.data.TunnelListEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v13 = v14 minus the tunnel tables; Room validates the schema after MIGRATION_13_14. Runs in both flavors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TunnelMigrationTest {
    private val dbName = "tunnel-migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate13To14_addsTunnelTables_keepsListenerData() = runBlocking {
        Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build().apply {
            val sql = openHelper.writableDatabase
            for (t in listOf("tunnel_app_rules", "tunnel_domain_rules", "tunnel_lists", "listener_markers")) sql.execSQL("DROP TABLE $t")
            sql.execSQL(
                "INSERT INTO listener_sessions (title, startedAt, endedAt, durationMs, segmentCount, bytes, state, dirName, notes, " +
                    "transcriptState, transcribedChunks) VALUES ('Kept', 1, 2, 1, 1, 1, 'DONE', 'session-1', 'USER', 'DONE', 1)"
            )
            sql.version = 13
            close()
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        assertEquals(15, db.openHelper.writableDatabase.version)
        assertEquals("Kept", db.listenerSessionDao().getAll().first().single().title)

        val dao = db.tunnelRuleDao()
        dao.setAppRule(TunnelAppRuleEntity("com.bank", TunnelAppRuleEntity.MODE_BYPASS, 1))
        dao.setAppRule(TunnelAppRuleEntity("com.bank", TunnelAppRuleEntity.MODE_BLOCK, 2))
        assertEquals(TunnelAppRuleEntity.MODE_BLOCK, dao.appRules().single().mode)

        dao.putDomainRule(TunnelDomainRuleEntity(pattern = "*.ads.net", action = TunnelDomainRuleEntity.ACTION_BLOCK, createdAt = 1))
        dao.putDomainRule(TunnelDomainRuleEntity(pattern = "*.ads.net", action = TunnelDomainRuleEntity.ACTION_ALLOW, createdAt = 2))
        val rules = dao.domainRules()
        assertEquals("unique pattern: replaced, not duplicated", 1, rules.size)
        assertEquals(TunnelDomainRuleEntity.ACTION_ALLOW, rules[0].action)

        val id = dao.insertList(TunnelListEntity(name = "mine", fileName = "1.txt", entryCount = 3, enabled = true, importedAt = 1))
        dao.setListEnabled(id, false)
        assertTrue(!dao.lists().single().enabled)
        db.close()
    }
}
