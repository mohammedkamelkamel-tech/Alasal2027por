package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.data.local.database.AlasalDatabase
import com.example.data.repository.AlasalRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.StationSyncRepository
import com.example.service.BackupAndRestoreManager
import com.example.service.DailyAutoBackupWorker
import com.example.service.LocalWifiSyncEngine
import com.example.service.SafBackupManager
import com.example.service.StationMaintenanceWorker
import com.example.service.ThemePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlasalApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _databaseEpoch = MutableStateFlow(1)
    val databaseEpoch: StateFlow<Int> = _databaseEpoch.asStateFlow()

    @Volatile
    lateinit var database: AlasalDatabase
        private set

    @Volatile
    lateinit var repository: AlasalRepository
        private set

    @Volatile
    lateinit var billingRepository: BillingRepository
        private set

    @Volatile
    lateinit var syncRepository: StationSyncRepository
        private set

    val themePreferences: ThemePreferencesRepository by lazy { ThemePreferencesRepository(this) }

    @Volatile
    lateinit var safBackupManager: SafBackupManager
        private set

    val localWifiSyncEngine: LocalWifiSyncEngine by lazy {
        LocalWifiSyncEngine(
            context = this,
            coreRepository = repository,
            syncRepository = syncRepository
        )
    }

    val backupAndRestoreManager: BackupAndRestoreManager by lazy {
        BackupAndRestoreManager(
            context = this,
            database = database,
            repository = repository,
            onReloadApplicationDependencies = { reloadDatabaseDependencies() }
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        val initialDb = AlasalDatabase.getInstance(this)
        val initialRepo = AlasalRepository(initialDb)
        database = initialDb
        repository = initialRepo
        billingRepository = BillingRepository(initialDb, initialRepo)
        syncRepository = StationSyncRepository(initialDb, initialRepo)
        safBackupManager = SafBackupManager(this, initialRepo)

        applicationScope.launch {
            runCatching {
                repository.ensureBaselineSeeded()
                val backupPrefs = themePreferences.getBackupAndSessionPrefsOnce()
                if (backupPrefs.dailyAutoBackupEnabled) {
                    DailyAutoBackupWorker.scheduleDailyBackup(this@AlasalApplication)
                }
                backupAndRestoreManager.refreshBackupsList()
            }
        }
        runCatching {
            StationMaintenanceWorker.schedulePeriodicMaintenance(this)
        }
    }

    /**
     * Ensures that if the database singleton was reset externally, all app-level repositories rebind cleanly.
     */
    @Synchronized
    fun ensureActiveDatabaseDependencies() {
        val currentLive = AlasalDatabase.getInstance(this)
        if (!::database.isInitialized || database !== currentLive || !database.isOpen) {
            reloadDatabaseDependencies()
        }
    }

    /**
     * Rebinds all database-backed repositories after an 18-step safe restore swaps the SQLite file.
     */
    @Synchronized
    fun reloadDatabaseDependencies() {
        val nextDb = AlasalDatabase.getInstance(this)
        val nextRepo = AlasalRepository(nextDb)
        val nextBilling = BillingRepository(nextDb, nextRepo)
        val nextSync = StationSyncRepository(nextDb, nextRepo)
        val nextSaf = SafBackupManager(this, nextRepo)

        database = nextDb
        repository = nextRepo
        billingRepository = nextBilling
        syncRepository = nextSync
        safBackupManager = nextSaf

        localWifiSyncEngine.rebindRepositories(nextRepo, nextSync)
        backupAndRestoreManager.rebindDatabase(nextDb, nextRepo)
        _databaseEpoch.value = _databaseEpoch.value + 1
    }
}
