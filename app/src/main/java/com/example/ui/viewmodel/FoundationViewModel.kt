package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.FinancialAuditLogEntity
import com.example.data.local.entity.KwhTariffEntity
import com.example.data.local.entity.PermissionEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.SubscriberFullHistory
import com.example.data.local.entity.TariffChangeLogEntity
import com.example.data.local.entity.UserEntity
import com.example.data.repository.AlasalRepository
import com.example.data.repository.FoundationDashboardSnapshot
import com.example.data.repository.SubscriberInput
import com.example.data.repository.TableSchemaStatus
import com.example.data.repository.TariffPricePreviewItem
import com.example.domain.auth.PermissionCatalog
import com.example.domain.auth.StationPermission
import com.example.domain.auth.StationRoles
import com.example.domain.sync.SyncConnectionState
import com.example.domain.sync.SyncLogEntry
import com.example.domain.sync.SyncMergeSummary
import com.example.domain.sync.SyncPeerInfo
import com.example.service.AlasalNotificationService
import com.example.service.AppThemeMode
import com.example.service.BackupAndRestoreManager
import com.example.service.BackupAndSessionPrefs
import com.example.service.BackupRecordInfo
import com.example.service.DailyAutoBackupWorker
import com.example.service.LocalWifiSyncEngine
import com.example.service.NotificationAndPrintPrefs
import com.example.service.RestoreExecutionResult
import com.example.service.SafBackupManager
import com.example.service.StationMaintenanceWorker
import com.example.service.ThemePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface FoundationUiState {
    data object Loading : FoundationUiState
    data class Ready(
        val dashboard: FoundationDashboardSnapshot,
        val subscribers: List<SubscriberEntity>,
        val tariffs: List<KwhTariffEntity>,
        val tariffChangeLogs: List<TariffChangeLogEntity>,
        val tableStatuses: List<TableSchemaStatus>,
        val users: List<UserEntity>,
        val permissions: List<PermissionEntity>,
        val settings: List<AppSettingEntity>,
        val auditLogs: List<FinancialAuditLogEntity>
    ) : FoundationUiState
    data class Error(val message: String) : FoundationUiState
}

sealed interface FeedbackMessage {
    data class Success(val text: String) : FeedbackMessage
    data class Failure(val text: String) : FeedbackMessage
}

class FoundationViewModel(
    private val repository: AlasalRepository,
    private val themePreferences: ThemePreferencesRepository,
    private val safBackupManager: SafBackupManager,
    private val backupAndRestoreManager: BackupAndRestoreManager? = null,
    private val localWifiSyncEngine: LocalWifiSyncEngine? = null,
    private val preserveUnlockedSession: Boolean = false
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = themePreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeMode.SYSTEM
    )

    val notificationPrefs: StateFlow<NotificationAndPrintPrefs> =
        themePreferences.notificationAndPrintPrefs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationAndPrintPrefs()
        )

    val backupSessionPrefs: StateFlow<BackupAndSessionPrefs> =
        themePreferences.backupAndSessionPrefs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BackupAndSessionPrefs()
        )

    val reminders: StateFlow<List<ReminderEntity>> =
        repository.observeAllReminders().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _feedback = MutableStateFlow<FeedbackMessage?>(null)
    val feedback: StateFlow<FeedbackMessage?> = _feedback.asStateFlow()

    private val _selectedSubscriberHistory = MutableStateFlow<SubscriberFullHistory?>(null)
    val selectedSubscriberHistory: StateFlow<SubscriberFullHistory?> =
        _selectedSubscriberHistory.asStateFlow()

    private val _tariffPreviewItems = MutableStateFlow<List<TariffPricePreviewItem>>(emptyList())
    val tariffPreviewItems: StateFlow<List<TariffPricePreviewItem>> =
        _tariffPreviewItems.asStateFlow()

    private val _isAppLocked = MutableStateFlow(!preserveUnlockedSession)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _lastInteractionAtMs = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionAtMs: StateFlow<Long> = _lastInteractionAtMs.asStateFlow()

    private var lastBackgroundedAtMs: Long = 0L

    val backupsList: StateFlow<List<BackupRecordInfo>> =
        backupAndRestoreManager?.backups ?: MutableStateFlow(emptyList())

    val lastRestoreResult: StateFlow<RestoreExecutionResult?> =
        backupAndRestoreManager?.lastRestoreResult ?: MutableStateFlow(null)

    val syncConnectionState: StateFlow<SyncConnectionState> =
        localWifiSyncEngine?.connectionState ?: MutableStateFlow(SyncConnectionState.IDLE)

    val syncStatusMessage: StateFlow<String> =
        localWifiSyncEngine?.statusMessage ?: MutableStateFlow("غير مهيأ")

    val syncHostingPort: StateFlow<Int?> =
        localWifiSyncEngine?.hostingPort ?: MutableStateFlow(null)

    val syncLocalIp: StateFlow<String> =
        localWifiSyncEngine?.localIpAddress ?: MutableStateFlow("127.0.0.1")

    val discoveredSyncPeers: StateFlow<List<SyncPeerInfo>> =
        localWifiSyncEngine?.discoveredPeers ?: MutableStateFlow(emptyList())

    val syncLogs: StateFlow<List<SyncLogEntry>> =
        localWifiSyncEngine?.syncLogs ?: MutableStateFlow(emptyList())

    val lastSyncSummary: StateFlow<SyncMergeSummary?> =
        localWifiSyncEngine?.lastSummary ?: MutableStateFlow(null)

    val uiState: StateFlow<FoundationUiState> = run {
        val coreFlow = combine(
            repository.observeDashboardSnapshot(),
            repository.observeSubscribers(),
            repository.observeTariffs(),
            repository.observeTariffChangeLogs(),
            repository.observeSchemaTableStatuses()
        ) { dashboard, subscribers, tariffs, tariffLogs, tableStatuses ->
            CoreBundle(dashboard, subscribers, tariffs, tariffLogs, tableStatuses)
        }

        val systemFlow = combine(
            repository.observeUsers(),
            repository.observePermissions(),
            repository.observeSettings(),
            repository.observeRecentAuditLogs()
        ) { users, permissions, settings, auditLogs ->
            SystemBundle(users, permissions, settings, auditLogs)
        }

        combine(coreFlow, systemFlow) { core, sys ->
            FoundationUiState.Ready(
                dashboard = core.dashboard,
                subscribers = core.subscribers,
                tariffs = core.tariffs,
                tariffChangeLogs = core.tariffLogs,
                tableStatuses = core.tableStatuses,
                users = sys.users,
                permissions = sys.permissions,
                settings = sys.settings,
                auditLogs = sys.auditLogs
            ) as FoundationUiState
        }.catch { throwable ->
            emit(FoundationUiState.Error(throwable.message ?: "حدث خطأ غير متوقع في تحميل البيانات"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FoundationUiState.Loading
        )
    }

    val activeUser: StateFlow<UserEntity?> = combine(uiState, backupSessionPrefs) { state, prefs ->
        val ready = state as? FoundationUiState.Ready ?: return@combine null
        val selected = prefs.activeUserId?.let { id -> ready.users.firstOrNull { it.id == id } }
        selected ?: ready.users.firstOrNull { it.roleCode == StationRoles.ADMIN && it.isActive }
            ?: ready.users.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val effectivePermissions: StateFlow<Set<String>> = combine(uiState, activeUser) { state, user ->
        val allPermissionKeys = StationPermission.entries.map { it.key }.toSet()
        val ready = state as? FoundationUiState.Ready ?: return@combine allPermissionKeys
        if (user == null) return@combine allPermissionKeys
        if (!user.isActive) return@combine emptySet()
        val rolePerms = ready.permissions.filter { it.roleCode == user.roleCode }
        StationPermission.entries
            .filter { PermissionCatalog.isEffectivePermissionAllowed(user, rolePerms, it.key) }
            .map { it.key }
            .toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StationPermission.entries.map { it.key }.toSet()
    )

    private fun currentActorUserId(): Long? = backupSessionPrefs.value.activeUserId ?: activeUser.value?.id

    init {
        viewModelScope.launch {
            repository.ensureBaselineSeeded()
            backupAndRestoreManager?.refreshBackupsList()
            val prefs = themePreferences.getBackupAndSessionPrefsOnce()
            if (!prefs.appLockEnabled || preserveUnlockedSession) {
                _isAppLocked.value = false
            }
            if (preserveUnlockedSession) {
                backupAndRestoreManager?.lastRestoreResult?.value?.let { lastRes ->
                    _feedback.value = if (lastRes.succeeded) {
                        FeedbackMessage.Success(lastRes.message)
                    } else {
                        val detail = lastRes.technicalError?.let { "\n$it" }.orEmpty()
                        FeedbackMessage.Failure("${lastRes.message}$detail")
                    }
                }
            }
        }
    }

    fun recordUserInteraction(nowMillis: Long = System.currentTimeMillis()) {
        _lastInteractionAtMs.value = nowMillis
    }

    fun lockAppImmediately() {
        _isAppLocked.value = true
    }

    fun onAppWentToBackground(nowMillis: Long = System.currentTimeMillis()) {
        lastBackgroundedAtMs = nowMillis
        if (backupSessionPrefs.value.appLockEnabled) {
            _isAppLocked.value = true
        }
    }

    fun onAppReturnedToForeground(nowMillis: Long = System.currentTimeMillis()) {
        val prefs = backupSessionPrefs.value
        if (!prefs.appLockEnabled) {
            _isAppLocked.value = false
            lastBackgroundedAtMs = 0L
            return
        }
        val timeoutMs = prefs.inactivityLockTimeoutSeconds * 1000L
        val backgroundElapsed = if (lastBackgroundedAtMs > 0L) {
            (nowMillis - lastBackgroundedAtMs).coerceAtLeast(0L)
        } else 0L
        val inactivityElapsed = (nowMillis - _lastInteractionAtMs.value).coerceAtLeast(0L)
        if (timeoutMs == 0L ||
            (lastBackgroundedAtMs > 0L && backgroundElapsed >= timeoutMs) ||
            (timeoutMs > 0L && inactivityElapsed >= timeoutMs)
        ) _isAppLocked.value = true
        lastBackgroundedAtMs = 0L
    }

    fun checkInactivityTimeout(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = backupSessionPrefs.value
        if (!prefs.appLockEnabled || _isAppLocked.value) return false
        val timeoutSeconds = prefs.inactivityLockTimeoutSeconds
        if (timeoutSeconds <= 0) return false
        val elapsed = nowMillis - _lastInteractionAtMs.value
        if (elapsed >= timeoutSeconds * 1000L) {
            _isAppLocked.value = true
            return true
        }
        return false
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAppLockEnabled(enabled)
            if (!enabled) {
                _isAppLocked.value = false
            }
            _feedback.value = FeedbackMessage.Success(
                if (enabled) "تم تفعيل قفل الأمان التلقائي للتطبيق"
                else "تم تعطيل شاشة القفل التلقائي عند الدخول"
            )
        }
    }

    fun setInactivityLockTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            themePreferences.setInactivityLockTimeoutSeconds(seconds)
            val label = if (seconds == 0) "فوري عند الخروج من التطبيق" else "$seconds ثانية"
            _feedback.value = FeedbackMessage.Success("تم ضبط مهلة القفل التلقائي على: $label")
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun cycleThemeMode(isCurrentlyDark: Boolean) {
        setThemeMode(if (isCurrentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
    }

    fun loginWithCredentials(
        username: String,
        password: String,
        onSuccess: (UserEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.authenticateUser(username, password)
                .onSuccess { user ->
                    themePreferences.setActiveUserId(user.id)
                    _lastInteractionAtMs.value = System.currentTimeMillis()
                    _isAppLocked.value = false
                    _feedback.value = FeedbackMessage.Success(
                        "تم تسجيل الدخول بحساب: ${user.fullName} (${StationRoles.arabicTitle(user.roleCode)})"
                    )
                    onSuccess(user)
                }
                .onFailure { err ->
                    val msg = err.message ?: "تعذر تسجيل الدخول"
                    _feedback.value = FeedbackMessage.Failure(msg)
                    onError(msg)
                }
        }
    }

    fun unlockWithBiometric(
        userId: Long,
        onSuccess: (UserEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.authenticateUserWithBiometric(userId)
                .onSuccess { user ->
                    themePreferences.setActiveUserId(user.id)
                    _lastInteractionAtMs.value = System.currentTimeMillis()
                    _isAppLocked.value = false
                    _feedback.value = FeedbackMessage.Success(
                        "تم فتح التطبيق بالبصمة بحساب: ${user.fullName}"
                    )
                    onSuccess(user)
                }
                .onFailure { err ->
                    val msg = err.message ?: "تعذر التحقق بالبصمة"
                    _feedback.value = FeedbackMessage.Failure(msg)
                    onError(msg)
                }
        }
    }

    fun createUserAccount(
        username: String,
        fullName: String,
        roleCode: String,
        password: String,
        isActive: Boolean,
        customPermissions: Map<String, Boolean>,
        biometricEnabled: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createUserAccount(
                username = username,
                fullName = fullName,
                roleCode = roleCode,
                password = password,
                isActive = isActive,
                customPermissions = customPermissions,
                biometricEnabled = biometricEnabled,
                actorUserId = currentActorUserId()
            )
                .onSuccess {
                    _feedback.value = FeedbackMessage.Success("تم إنشاء حساب ($fullName) وتخصيص صلاحياته بنجاح")
                    onDone()
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر إنشاء الحساب")
                }
        }
    }

    fun updateUserAccount(
        userId: Long,
        fullName: String,
        roleCode: String,
        isActive: Boolean,
        customPermissions: Map<String, Boolean>,
        biometricEnabled: Boolean? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateUserAccount(
                userId = userId,
                fullName = fullName,
                roleCode = roleCode,
                isActive = isActive,
                customPermissions = customPermissions,
                biometricEnabled = biometricEnabled,
                actorUserId = currentActorUserId()
            )
                .onSuccess { updated ->
                    _feedback.value = FeedbackMessage.Success("تم تحديث الحساب (${updated.fullName}) والصلاحيات بنجاح")
                    onDone()
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر تحديث الحساب")
                }
        }
    }

    fun toggleUserBiometric(targetUserId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserBiometricEnabled(
                targetUserId = targetUserId,
                enabled = enabled,
                actorUserId = currentActorUserId()
            )
                .onSuccess { u ->
                    _feedback.value = FeedbackMessage.Success(
                        if (u.biometricEnabled) "تم تفعيل الدخول السريع بالبصمة لحساب ${u.fullName}"
                        else "تم إيقاف الدخول بالبصمة لحساب ${u.fullName}"
                    )
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر تحديث خيار البصمة")
                }
        }
    }

    fun changeUserPassword(
        targetUserId: Long,
        newPassword: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.changeUserPassword(
                targetUserId = targetUserId,
                newPassword = newPassword,
                actorUserId = currentActorUserId()
            )
                .onSuccess {
                    _feedback.value = FeedbackMessage.Success("تم تغيير كلمة المرور بنجاح")
                    onDone()
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر تغيير كلمة المرور")
                }
        }
    }

    fun toggleUserActive(targetUserId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleUserActive(
                targetUserId = targetUserId,
                isActive = isActive,
                actorUserId = currentActorUserId()
            )
                .onSuccess { u ->
                    _feedback.value = FeedbackMessage.Success(
                        "${if (u.isActive) "تم تفعيل" else "تم إيقاف"} حساب ${u.fullName} بنجاح"
                    )
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر تغيير حالة الحساب")
                }
        }
    }

    fun updateUserPermissions(
        targetUserId: Long,
        customPermissions: Map<String, Boolean>,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateUserPermissions(
                targetUserId = targetUserId,
                customPermissions = customPermissions,
                actorUserId = currentActorUserId()
            )
                .onSuccess { u ->
                    _feedback.value = FeedbackMessage.Success("تم تحديث مصفوفة صلاحيات (${u.fullName}) بنجاح")
                    onDone()
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر تحديث الصلاحيات")
                }
        }
    }

    fun loadSubscriberHistory(subscriberId: Long) {
        viewModelScope.launch {
            _selectedSubscriberHistory.value = repository.getSubscriberFullHistory(subscriberId)
        }
    }

    fun clearSubscriberHistory() {
        _selectedSubscriberHistory.value = null
    }

    fun saveSubscriber(input: SubscriberInput, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveSubscriber(input, actorUserId = currentActorUserId())
                .onSuccess { savedId ->
                    val label = if (input.id == 0L) "تمت إضافة المشترك (${input.fullName}) بنجاح" else "تم تحديث بيانات (${input.fullName}) بنجاح"
                    _feedback.value = FeedbackMessage.Success(label)
                    if (_selectedSubscriberHistory.value?.subscriber?.id == savedId) {
                        _selectedSubscriberHistory.value = repository.getSubscriberFullHistory(savedId)
                    }
                    onDone()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر حفظ بيانات المشترك")
                }
        }
    }

    fun deleteSubscriber(subscriberId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteSubscriberSafely(subscriberId, actorUserId = currentActorUserId())
                .onSuccess { msg ->
                    _feedback.value = FeedbackMessage.Success(msg)
                    _selectedSubscriberHistory.value = null
                    onDeleted()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر حذف المشترك")
                }
        }
    }

    fun prepareTariffPreview(targetSubscriberIds: List<Long>?, newPricePerKwh: Double) {
        viewModelScope.launch {
            repository.previewTariffUpdate(targetSubscriberIds, newPricePerKwh)
                .onSuccess { items -> _tariffPreviewItems.value = items }
                .onFailure { error ->
                    _tariffPreviewItems.value = emptyList()
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر معاينة تعديل السعر")
                }
        }
    }

    fun clearTariffPreview() {
        _tariffPreviewItems.value = emptyList()
    }

    fun applyTariffUpdate(
        targetSubscriberIds: List<Long>?,
        newPricePerKwh: Double,
        effectiveFrom: Long,
        reason: String,
        onApplied: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.applyKwhPriceUpdate(
                targetSubscriberIds = targetSubscriberIds,
                newPricePerKwh = newPricePerKwh,
                effectiveFrom = effectiveFrom,
                reason = reason,
                actorUserId = currentActorUserId()
            )
                .onSuccess { count ->
                    _tariffPreviewItems.value = emptyList()
                    val selectedSubId = _selectedSubscriberHistory.value?.subscriber?.id
                    if (selectedSubId != null && (targetSubscriberIds == null || selectedSubId in targetSubscriberIds)) {
                        _selectedSubscriberHistory.value = repository.getSubscriberFullHistory(selectedSubId)
                    }
                    _feedback.value = FeedbackMessage.Success(
                        "تم اعتماد السعر الجديد ($newPricePerKwh / ك.و.س) لعدد $count مشترك وحفظ السجل التاريخي دون المساس بالفواتير القديمة"
                    )
                    onApplied()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر تحديث السعر")
                }
        }
    }

    fun verifyDatabaseIntegrity() {
        viewModelScope.launch {
            repository.verifySchemaIntegrity()
                .onSuccess { statuses ->
                    _feedback.value = FeedbackMessage.Success(
                        "تم التحقق من سلامة ${statuses.size} جدولاً والعلاقات المرجعية بنجاح"
                    )
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "فشل التحقق من قاعدة البيانات")
                }
        }
    }

    fun scheduleImmediateWorkManagerAudit(context: Context) {
        runCatching {
            StationMaintenanceWorker.enqueueImmediateAudit(context)
        }.onSuccess {
            _feedback.value = FeedbackMessage.Success(
                "تمت جدولة مهمة التحقق والتذكير بنجاح عبر WorkManager"
            )
        }.onFailure { error ->
            _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر جدولة مهمة الخلفية")
        }
    }

    fun exportSafBackup(uri: Uri) {
        viewModelScope.launch {
            safBackupManager.exportFoundationBackupToUri(uri)
                .onSuccess { message -> _feedback.value = FeedbackMessage.Success(message) }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر تصدير النسخة الاحتياطية")
                }
        }
    }

    // ---- Phase 4: Full Package Backup, Verification & 18-Step Safe Restore ----

    fun refreshBackupsList() {
        backupAndRestoreManager?.refreshBackupsList()
    }

    fun createManualBackupPackage() {
        val mgr = backupAndRestoreManager ?: return
        viewModelScope.launch {
            val treeUri = backupSessionPrefs.value.customBackupFolderUri?.let { Uri.parse(it) }
            mgr.createBackup(
                backupType = "MANUAL",
                actorUserId = currentActorUserId(),
                customTreeUri = treeUri
            )
                .onSuccess { info ->
                    _feedback.value = FeedbackMessage.Success(
                        "تم إنشاء النسخة الاحتياطية (${info.fileName}) بحجم ${mgr.formatBytes(info.fileSizeBytes)} وتأكيد سلامتها"
                    )
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر إنشاء النسخة الاحتياطية")
                }
        }
    }

    fun verifyBackupPackage(file: File) {
        val mgr = backupAndRestoreManager ?: return
        viewModelScope.launch {
            val res = mgr.verifyBackupFile(file)
            _feedback.value = if (res.isValid) {
                FeedbackMessage.Success(
                    "النسخة سليمة وموثقة (الإصدار v${res.dbVersion}، ${res.tableCount} جدولاً، ${mgr.formatBytes(res.fileSizeBytes)})"
                )
            } else {
                FeedbackMessage.Failure(res.errorMessage ?: "فشل التحقق من النسخة")
            }
        }
    }

    fun exportFullBackupToUri(destinationUri: Uri, sourceFile: File? = null) {
        val mgr = backupAndRestoreManager ?: return
        viewModelScope.launch {
            mgr.exportBackupToUri(
                backupFile = sourceFile,
                destinationUri = destinationUri,
                actorUserId = currentActorUserId()
            )
                .onSuccess { info ->
                    _feedback.value = FeedbackMessage.Success("تم حفظ النسخة ${info.fileName} في المسار المختار بنجاح")
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر تصدير النسخة")
                }
        }
    }

    fun deleteBackupFile(file: File) {
        val mgr = backupAndRestoreManager ?: return
        viewModelScope.launch {
            if (mgr.deleteBackup(file)) {
                _feedback.value = FeedbackMessage.Success("تم حذف ملف النسخة الاحتياطية")
            } else {
                _feedback.value = FeedbackMessage.Failure("تعذر حذف ملف النسخة")
            }
        }
    }

    fun toggleDailyAutoBackup(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setDailyAutoBackupEnabled(enabled)
            if (enabled) {
                DailyAutoBackupWorker.scheduleDailyBackup(context)
                _feedback.value = FeedbackMessage.Success("تم تفعيل النسخ الاحتياطي التلقائي اليومي")
            } else {
                DailyAutoBackupWorker.cancelDailyBackup(context)
                _feedback.value = FeedbackMessage.Success("تم إيقاف النسخ الاحتياطي التلقائي اليومي")
            }
        }
    }

    fun triggerImmediateDailyAutoBackup(context: Context) {
        DailyAutoBackupWorker.enqueueImmediateBackup(context)
        _feedback.value = FeedbackMessage.Success("تم بدء النسخ الاحتياطي التلقائي في الخلفية")
    }

    fun saveCustomBackupFolder(uri: Uri) {
        viewModelScope.launch {
            themePreferences.setCustomBackupFolderUri(uri.toString())
            _feedback.value = FeedbackMessage.Success("تم اعتماد مجلد الحفظ الخارجي للنسخ التلقائي واليدوي")
        }
    }

    fun executeSafeRestoreFromFile(file: File, onCompleted: (RestoreExecutionResult) -> Unit = {}) {
        val mgr = backupAndRestoreManager ?: return
        viewModelScope.launch {
            val res = mgr.executeSafeRestoreFromFile(file, actorUserId = currentActorUserId())
            _feedback.value = if (res.succeeded) {
                FeedbackMessage.Success(res.message)
            } else {
                val detail = res.technicalError?.let { " — $it" }.orEmpty()
                FeedbackMessage.Failure("${res.message}$detail")
            }
            onCompleted(res)
        }
    }

    fun executeSafeRestoreFromUri(uri: Uri, onCompleted: (RestoreExecutionResult) -> Unit = {}) {
        val mgr = backupAndRestoreManager ?: return
        viewModelScope.launch {
            val res = mgr.executeSafeRestoreFromUri(uri, actorUserId = currentActorUserId())
            _feedback.value = if (res.succeeded) {
                FeedbackMessage.Success(res.message)
            } else {
                val detail = res.technicalError?.let { " — $it" }.orEmpty()
                FeedbackMessage.Failure("${res.message}$detail")
            }
            onCompleted(res)
        }
    }

    // ---- Phase 4: Local Peer-to-Peer Wi-Fi Synchronization ----

    fun startWifiSyncHosting(port: Int = LocalWifiSyncEngine.DEFAULT_PORT) {
        val engine = localWifiSyncEngine ?: return
        viewModelScope.launch {
            engine.startHosting(port = port, actorUserId = currentActorUserId())
                .onSuccess { p ->
                    _feedback.value = FeedbackMessage.Success("الاستقبال عبر Wi-Fi جاهز على المنفذ $p")
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر بدء الاستقبال")
                }
        }
    }

    fun stopWifiSyncHosting() {
        localWifiSyncEngine?.stopHosting()
    }

    fun startWifiPeerDiscovery() {
        localWifiSyncEngine?.startDiscovery()
    }

    fun stopWifiPeerDiscovery() {
        localWifiSyncEngine?.stopDiscovery()
    }

    fun syncWithWifiPeer(host: String, port: Int) {
        val engine = localWifiSyncEngine ?: return
        viewModelScope.launch {
            engine.syncWithPeer(host = host, port = port, actorUserId = currentActorUserId())
                .onSuccess { summary ->
                    _feedback.value = FeedbackMessage.Success(
                        "تمت المزامنة بنجاح: إضافة ${summary.insertedCount}، تحديث ${summary.updatedCount}، تعارضات محلولة ${summary.conflictsResolvedCount}"
                    )
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "فشلت المزامنة عبر Wi-Fi")
                }
        }
    }

    fun retryLastFailedWifiSync() {
        val engine = localWifiSyncEngine ?: return
        viewModelScope.launch {
            engine.retryLastFailedSync(actorUserId = currentActorUserId())
                .onSuccess { summary ->
                    _feedback.value = FeedbackMessage.Success(
                        "نجحت إعادة المحاولة: إضافة ${summary.insertedCount}، تحديث ${summary.updatedCount}"
                    )
                }
                .onFailure { err ->
                    _feedback.value = FeedbackMessage.Failure(err.message ?: "تعذر إعادة المزامنة")
                }
        }
    }

    fun updateNotificationPrefs(transform: (NotificationAndPrintPrefs) -> NotificationAndPrintPrefs) {
        viewModelScope.launch {
            themePreferences.updateNotificationPrefs(transform)
        }
    }

    fun scheduleReadingReminder(
        context: Context,
        subscriberId: Long?,
        title: String,
        message: String,
        notes: String,
        scheduledAt: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.scheduleReminder(
                subscriberId = subscriberId,
                reminderType = "METER_READING",
                title = title,
                message = message,
                notes = notes,
                scheduledAt = scheduledAt
            )
                .onSuccess { reminderId ->
                    val delay = (scheduledAt - System.currentTimeMillis()).coerceAtLeast(0L)
                    runCatching {
                        StationMaintenanceWorker.scheduleReminderCheck(context, reminderId, delay)
                    }
                    _feedback.value = FeedbackMessage.Success("تمت جدولة موعد القراءة والتنبيه ($title) بنجاح")
                    onDone()
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر جدولة التذكير")
                }
        }
    }

    fun completeReminder(reminderId: Long) {
        viewModelScope.launch {
            repository.completeReminder(reminderId)
                .onSuccess {
                    _feedback.value = FeedbackMessage.Success("تم إكمال التذكير بنجاح")
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر تحديث التذكير")
                }
        }
    }

    fun snoozeReminder(context: Context, reminderId: Long, delayMillis: Long = 3_600_000L) {
        viewModelScope.launch {
            repository.snoozeReminder(reminderId, delayMillis)
                .onSuccess { updated ->
                    val delay = (updated.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0L)
                    runCatching {
                        StationMaintenanceWorker.scheduleReminderCheck(context, updated.id, delay)
                    }
                    _feedback.value = FeedbackMessage.Success("تم تأجيل التذكير بنجاح")
                }
                .onFailure { error ->
                    _feedback.value = FeedbackMessage.Failure(error.message ?: "تعذر تأجيل التذكير")
                }
        }
    }

    fun sendTestNotification(context: Context) {
        viewModelScope.launch {
            val prefs = themePreferences.getNotificationPrefsOnce()
            val sent = AlasalNotificationService.dispatchNotification(
                context = context,
                notificationId = 1999,
                title = "اختبار تنبيهات محطة العسل 2027",
                body = if (prefs.soundEnabled) "الإشعارات مفعّلة مع الصوت" else "الإشعارات مفعّلة بالوضع الصامت",
                prefs = prefs
            )
            _feedback.value = if (sent) {
                FeedbackMessage.Success("تم إرسال إشعار تجريبي بنجاح")
            } else {
                FeedbackMessage.Failure("الإشعارات معطلة أو يلزم منح إذن الإشعارات")
            }
        }
    }

    fun postFeedback(message: FeedbackMessage) {
        _feedback.value = message
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private data class CoreBundle(
        val dashboard: FoundationDashboardSnapshot,
        val subscribers: List<SubscriberEntity>,
        val tariffs: List<KwhTariffEntity>,
        val tariffLogs: List<TariffChangeLogEntity>,
        val tableStatuses: List<TableSchemaStatus>
    )

    private data class SystemBundle(
        val users: List<UserEntity>,
        val permissions: List<PermissionEntity>,
        val settings: List<AppSettingEntity>,
        val auditLogs: List<FinancialAuditLogEntity>
    )

    class Factory(
        private val repository: AlasalRepository,
        private val themePreferences: ThemePreferencesRepository,
        private val safBackupManager: SafBackupManager,
        private val backupAndRestoreManager: BackupAndRestoreManager? = null,
        private val localWifiSyncEngine: LocalWifiSyncEngine? = null,
        private val preserveUnlockedSession: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FoundationViewModel(
                repository = repository,
                themePreferences = themePreferences,
                safBackupManager = safBackupManager,
                backupAndRestoreManager = backupAndRestoreManager,
                localWifiSyncEngine = localWifiSyncEngine,
                preserveUnlockedSession = preserveUnlockedSession
            ) as T
        }
    }
}
