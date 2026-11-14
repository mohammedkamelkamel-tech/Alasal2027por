package com.example.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.data.repository.AlasalRepository
import com.example.data.repository.StationSyncRepository
import com.example.domain.auth.StationPermission
import com.example.domain.sync.StationSyncEnvelope
import com.example.domain.sync.SyncConnectionState
import com.example.domain.sync.SyncLogEntry
import com.example.domain.sync.SyncMergeSummary
import com.example.domain.sync.SyncPeerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Local Peer-to-Peer Wi-Fi Synchronization Engine (Zero internet / Zero paid server).
 * Uses TCP sockets + length-prefixed UTF-8 framing + Android NSD discovery with manual IP:Port fallback.
 */
class LocalWifiSyncEngine(
    private val context: Context,
    @Volatile private var coreRepository: AlasalRepository,
    @Volatile private var syncRepository: StationSyncRepository,
    val deviceId: String = "DEV-${UUID.randomUUID().toString().take(8)}",
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "جهاز محطة العسل" }
) {
    companion object {
        const val DEFAULT_PORT = 20270
        const val NSD_SERVICE_TYPE = "_alasal2027._tcp."
        private const val MAX_FRAME_BYTES = 32 * 1024 * 1024 // 32 MB safety guard
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val SOCKET_READ_TIMEOUT_MS = 12000
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(SyncConnectionState.IDLE)
    val connectionState: StateFlow<SyncConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("جاهز للمزامنة عبر شبكة Wi-Fi المحلية دون خادم")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _hostingPort = MutableStateFlow<Int?>(null)
    val hostingPort: StateFlow<Int?> = _hostingPort.asStateFlow()

    private val _localIpAddress = MutableStateFlow(resolveLocalWifiIpv4())
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<SyncPeerInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<SyncPeerInfo>> = _discoveredPeers.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val syncLogs: StateFlow<List<SyncLogEntry>> = _syncLogs.asStateFlow()

    private val _lastSummary = MutableStateFlow<SyncMergeSummary?>(null)
    val lastSummary: StateFlow<SyncMergeSummary?> = _lastSummary.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    private var lastAttemptedTarget: Pair<String, Int>? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun rebindRepositories(core: AlasalRepository, sync: StationSyncRepository) {
        this.coreRepository = core
        this.syncRepository = sync
    }

    fun refreshLocalIp() {
        _localIpAddress.value = resolveLocalWifiIpv4()
    }

    fun hasRetryTarget(): Boolean = lastAttemptedTarget != null

    /**
     * Starts local TCP server socket (and optional NSD registration) so another device on Wi-Fi can sync.
     * Supports port = 0 for ephemeral test port binding.
     */
    suspend fun startHosting(
        port: Int = DEFAULT_PORT,
        actorUserId: Long? = null,
        registerNsd: Boolean = true
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (actorUserId != null) {
                coreRepository.verifyPermission(actorUserId, StationPermission.MANAGE_SYNC.key)
            }
            stopHostingInternal()
            val server = ServerSocket(port)
            val boundPort = server.localPort
            serverSocket = server
            _hostingPort.value = boundPort
            _localIpAddress.value = resolveLocalWifiIpv4()
            _connectionState.value = SyncConnectionState.HOSTING
            _statusMessage.value = "الاستقبال مفعّل على ${_localIpAddress.value}:$boundPort"

            if (registerNsd) {
                registerNsdService(boundPort)
            }

            acceptJob = engineScope.launch {
                while (!server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch {
                        handleIncomingClient(client)
                    }
                }
            }

            appendLog(
                peerLabel = "${_localIpAddress.value}:$boundPort",
                isSuccess = true,
                message = "بدء وضع الاستقبال للمزامنة عبر Wi-Fi على المنفذ $boundPort"
            )
            boundPort
        }.onFailure { err ->
            _connectionState.value = SyncConnectionState.FAILED
            _statusMessage.value = "تعذر تشغيل خادم المزامنة المحلي: ${err.message}"
            appendLog(
                peerLabel = "host:$port",
                isSuccess = false,
                message = "فشل تشغيل خادم المزامنة",
                details = err.message.orEmpty()
            )
        }
    }

    fun stopHosting() {
        engineScope.launch {
            stopHostingInternal()
            _connectionState.value = SyncConnectionState.IDLE
            _statusMessage.value = "تم إيقاف استقبال المزامنة"
        }
    }

    fun stopHostingBlocking() {
        stopHostingInternal()
        _connectionState.value = SyncConnectionState.IDLE
    }

    private fun stopHostingInternal() {
        unregisterNsdService()
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _hostingPort.value = null
    }

    private suspend fun handleIncomingClient(socket: Socket) {
        val peerAddr = socket.inetAddress?.hostAddress ?: "peer"
        try {
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            val incomingJson = readFrame(input)
            val incomingEnvelope = StationSyncEnvelope.fromJsonString(incomingJson)
            val mergeResult = syncRepository.mergeIncomingEnvelope(incomingEnvelope)
            if (mergeResult.isFailure) {
                val errMsg = mergeResult.exceptionOrNull()?.message ?: "فشل دمج البيانات الواردة"
                writeFrame(output, "ERR:$errMsg")
                appendLog(
                    peerLabel = peerAddr,
                    isSuccess = false,
                    message = "تم رفض حزمة مزامنة من $peerAddr",
                    details = errMsg
                )
                return
            }

            val summary = mergeResult.getOrThrow()
            val responseEnvelope = syncRepository.exportSyncEnvelope(deviceId, deviceName)
            writeFrame(output, "OK:${responseEnvelope.toJsonString()}")
            _lastSummary.value = summary
            _statusMessage.value = "تمت المزامنة مع ${incomingEnvelope.deviceName} بنجاح"
            appendLog(
                peerLabel = incomingEnvelope.deviceName,
                isSuccess = true,
                message = "استقبال ودمج مزامنة من ${incomingEnvelope.deviceName}",
                details = "إضافة ${summary.insertedCount} | تحديث ${summary.updatedCount} | تعارضات محلولة ${summary.conflictsResolvedCount} | عمليات مالية محفوظة ${summary.financialOpsPreservedCount}"
            )
        } catch (ex: Exception) {
            appendLog(
                peerLabel = peerAddr,
                isSuccess = false,
                message = "انقطع الاتصال أو وصلت حزمة تالفة أثناء استقبال المزامنة",
                details = ex.message.orEmpty()
            )
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Initiates bidirectional sync with a peer at [host]:[port].
     * Retains database integrity even if stream drops mid-transfer and remembers last target for retry.
     */
    suspend fun syncWithPeer(
        host: String,
        port: Int,
        actorUserId: Long? = null
    ): Result<SyncMergeSummary> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        lastAttemptedTarget = cleanHost to port
        runCatching {
            require(cleanHost.isNotEmpty()) { "يرجى إدخال عنوان IP للجهاز الآخر" }
            require(port in 1..65535) { "رقم المنفذ غير صالح" }
            if (actorUserId != null) {
                coreRepository.verifyPermission(actorUserId, StationPermission.MANAGE_SYNC.key)
            }

            _connectionState.value = SyncConnectionState.SYNCING
            _statusMessage.value = "جارٍ الاتصال والمزامنة مع $cleanHost:$port ..."

            val outgoingEnvelope = syncRepository.exportSyncEnvelope(deviceId, deviceName)
            val responseEnvelope = Socket().use { socket ->
                socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                socket.connect(InetSocketAddress(cleanHost, port), CONNECT_TIMEOUT_MS)
                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())
                writeFrame(out, outgoingEnvelope.toJsonString())
                val response = readFrame(inp)
                if (response.startsWith("ERR:")) {
                    error(response.removePrefix("ERR:"))
                }
                if (!response.startsWith("OK:")) {
                    error("استجابة غير متوقعة من الجهاز الآخر")
                }
                StationSyncEnvelope.fromJsonString(response.removePrefix("OK:"))
            }

            val mergeSummary = syncRepository.mergeIncomingEnvelope(responseEnvelope, actorUserId).getOrThrow()
            _lastSummary.value = mergeSummary
            _connectionState.value = SyncConnectionState.SUCCEEDED
            _statusMessage.value =
                "اكتملت المزامنة مع ${responseEnvelope.deviceName}: إضافة ${mergeSummary.insertedCount}، تحديث ${mergeSummary.updatedCount}، تعارضات محلولة ${mergeSummary.conflictsResolvedCount}"
            appendLog(
                peerLabel = "${responseEnvelope.deviceName} ($cleanHost:$port)",
                isSuccess = true,
                message = "مزامنة ثنائية ناجحة",
                details = "إضافة ${mergeSummary.insertedCount} | تحديث ${mergeSummary.updatedCount} | تعارضات ${mergeSummary.conflictsResolvedCount} | مالية محفوظة ${mergeSummary.financialOpsPreservedCount}"
            )
            mergeSummary
        }.onFailure { err ->
            _connectionState.value = SyncConnectionState.FAILED
            val userMsg = "تعذر إتمام المزامنة مع $cleanHost:$port — ${err.message ?: "انقطاع الشبكة"}"
            _statusMessage.value = userMsg
            appendLog(
                peerLabel = "$cleanHost:$port",
                isSuccess = false,
                message = "فشل المزامنة عبر Wi-Fi (يمكن إعادة المحاولة)",
                details = err.message.orEmpty()
            )
        }
    }

    suspend fun retryLastFailedSync(actorUserId: Long? = null): Result<SyncMergeSummary> {
        val target = lastAttemptedTarget
            ?: return Result.failure(IllegalStateException("لا توجد محاولة مزامنة سابقة لإعادة تنفيذها"))
        return syncWithPeer(target.first, target.second, actorUserId)
    }

    fun startDiscovery() {
        runCatching {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            stopDiscovery()
            _connectionState.value = SyncConnectionState.DISCOVERING
            _statusMessage.value = "جارٍ البحث عن أجهزة محطة العسل على شبكة Wi-Fi..."
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType != NSD_SERVICE_TYPE) return
                    @Suppress("DEPRECATION")
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val ip = serviceInfo.host?.hostAddress ?: return
                            if (ip == _localIpAddress.value && serviceInfo.port == _hostingPort.value) return
                            val peer = SyncPeerInfo(
                                serviceName = serviceInfo.serviceName,
                                deviceName = serviceInfo.serviceName.removePrefix("Alasal2027-"),
                                hostAddress = ip,
                                port = serviceInfo.port
                            )
                            val updated = (_discoveredPeers.value.filterNot {
                                it.hostAddress == peer.hostAddress && it.port == peer.port
                            } + peer)
                            _discoveredPeers.value = updated
                        }
                    })
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    _discoveredPeers.value = _discoveredPeers.value.filterNot { it.serviceName == service.serviceName }
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsd.stopServiceDiscovery(this) }
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            discoveryListener = listener
            nsd.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsd?.stopServiceDiscovery(listener)
        }
    }

    private fun registerNsdService(port: Int) {
        runCatching {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            val info = NsdServiceInfo().apply {
                serviceName = "Alasal2027-$deviceName"
                serviceType = NSD_SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsdService() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsd?.unregisterService(listener)
        }
    }

    private fun writeFrame(output: DataOutputStream, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FRAME_BYTES) { "حجم حزمة المزامنة يتجاوز الحد المسموح" }
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readFrame(input: DataInputStream): String {
        val len = input.readInt()
        require(len in 1..MAX_FRAME_BYTES) { "حجم الحزمة الواردة غير صالح أو تالف ($len)" }
        val buf = ByteArray(len)
        input.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private fun appendLog(
        peerLabel: String,
        isSuccess: Boolean,
        message: String,
        details: String = ""
    ) {
        val entry = SyncLogEntry(
            peerLabel = peerLabel,
            isSuccess = isSuccess,
            message = message,
            details = details
        )
        _syncLogs.value = (listOf(entry) + _syncLogs.value).take(40)
    }

    private fun resolveLocalWifiIpv4(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { addr ->
                    addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress
                }
                ?.hostAddress ?: "127.0.0.1"
        }.getOrElse { "127.0.0.1" }
    }
}
