package com.zyz4.gamepademu.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.zyz4.gamepademu.data.PairingStateRepository
import com.zyz4.gamepademu.data.SettingsRepository
import com.zyz4.gamepademu.model.AppSettings
import com.zyz4.gamepademu.model.ConnectionMode
import com.zyz4.gamepademu.model.ControllerMode
import com.zyz4.gamepademu.model.TargetPlatform
import com.zyz4.gamepademu.proto.Hello
import com.zyz4.gamepademu.proto.ClientToServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class ConnectionState(
    val connected: Boolean = false,
    val statusText: String = "未启动",
    val batteryLevel: Int = 100,
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val error: ConnectionError? = null,
    val transportType: BluetoothTransportType? = null,
)

@Singleton
class ConnectionManager @Inject constructor(
    private val context: Context,
    private val pairingStateRepository: PairingStateRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val pairedDeviceAddress: StateFlow<String?> = pairingStateRepository.pairedDeviceAddress
        .stateIn(scope, SharingStarted.Eagerly, null)
    val pairedDeviceName: StateFlow<String?> = pairingStateRepository.pairedDeviceName
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val tcpServer = TcpServerService()
    private var bluetoothService: BluetoothHidService? = null
    val isBluetoothRunning: Boolean get() = bluetoothService != null
    private var serverJob: Job? = null
    private var broadcastJob: Job? = null
    private var btPhaseJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var sendTimeEma = 0.0
    private var currentPollingRate = 144

    private val _pollingIntervalMs = MutableStateFlow(1000 / 144)
    val pollingIntervalMs: StateFlow<Int> = _pollingIntervalMs.asStateFlow()

    companion object {
        private const val RATE_HIGH = 144
        private const val RATE_LOW = 60
        private const val EMA_ALPHA = 0.2
        private const val BACKOFF_THRESHOLD_MS = 5.0
        private const val RESTORE_THRESHOLD_MS = 2.0
    }

    init {
        _settings.value = runBlocking(Dispatchers.IO) {
            settingsRepository.settings.first()
        }
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        scope.launch {
            settingsRepository.saveSettings(newSettings)
        }
    }

    fun startServer(scope: CoroutineScope) {
        val s = _settings.value
        _connectionState.value = _connectionState.value.copy(statusText = "启动服务...")
        when (s.connectionMode) {
            ConnectionMode.WIFI -> {
                serverJob = scope.launch { startWifiServer(s) }
            }
            ConnectionMode.BLUETOOTH -> {
                startBluetooth(scope, s)
            }
        }
    }

    private suspend fun startWifiServer(settings: AppSettings) {
        sendTimeEma = 0.0
        currentPollingRate = RATE_HIGH
        _pollingIntervalMs.value = 1000 / RATE_HIGH
        val port = settings.wifiServerPort
        try {
            startBroadcast(settings.deviceName, port)
            _connectionState.value = _connectionState.value.copy(
                statusText = "服务已启动，等待客户端连接..."
            )
            tcpServer.start(port,
                onClientConnected = {
                    _connectionState.value = _connectionState.value.copy(
                        connected = true, statusText = "已连接 (WiFi)"
                    )
                    val protoMode = com.zyz4.gamepademu.proto.ControllerMode.forNumber(
                        settings.controllerMode.ordinal
                    ) ?: com.zyz4.gamepademu.proto.ControllerMode.XBOX_360
                    val hello = Hello.newBuilder()
                        .setProtocolVersion(1)
                        .setDeviceName(Build.MODEL)
                        .setControllerMode(protoMode)
                        .build()
                    val msg = ClientToServer.newBuilder()
                        .setHello(hello)
                        .build()
                    scope.launch { tcpServer.send(msg) }
                },
                onClientDisconnected = {
                    _connectionState.value = _connectionState.value.copy(
                        connected = false, statusText = "客户端已断开"
                    )
                },
                onMessage = { data -> handleClientMessage(data) }
            )
        } catch (e: Exception) {
            _connectionState.value = _connectionState.value.copy(
                connected = false, statusText = "服务异常: ${e.message}"
            )
        }
    }

    private fun startBluetooth(scope: CoroutineScope, settings: AppSettings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _connectionState.value = _connectionState.value.copy(
                connected = false, statusText = "蓝牙 HID 需要 Android 9+"
            )
            return
        }
        stopBluetooth()

        val transport: BluetoothHidService = ClassicHidTransport(context, pairingStateRepository)
        bluetoothService = transport

        _connectionState.value = _connectionState.value.copy(
            transportType = transport.transportType
        )

        transport.start(settings) { outputReport -> handleBtOutputReport(outputReport) }

        btPhaseJob = scope.launch {
            transport.connectionPhase.collect { phase ->
                updateBtState(phase, transport.transportType)
            }
        }
    }

    private fun updateBtState(phase: ConnectionPhase, type: BluetoothTransportType) {
        val (connected, text) = when (phase) {
            ConnectionPhase.IDLE -> false to "未启动"
            ConnectionPhase.REQUESTING_PERMISSIONS -> false to "请求蓝牙权限..."
            ConnectionPhase.REGISTERING_PROFILE -> false to "正在注册 HID 配置文件..."
            ConnectionPhase.RECONNECTING -> false to "正在自动回连已配对设备..."
            ConnectionPhase.DISCOVERABLE -> false to "等待主机连接 — 手机可被发现 (经典蓝牙)"
            ConnectionPhase.PAIRING -> false to "正在配对..."
            ConnectionPhase.CONNECTED -> true to "已连接 (蓝牙)"
            ConnectionPhase.DISCONNECTED -> false to "主机已断开"
            ConnectionPhase.ERROR -> false to "蓝牙错误"
        }
        _connectionState.value = _connectionState.value.copy(
            connected = connected,
            statusText = text,
            phase = phase,
        )
    }

    private fun handleBtOutputReport(data: ByteArray) {
        if (data.size < 8) return
        val leftMotor = data[1].toInt() and 0xFF
        val rightMotor = data[2].toInt() and 0xFF
        val speed = min(255, (leftMotor + rightMotor) / 2)
        if (speed > 0) {
            triggerVibration(speed)
        }
    }

    private fun startBroadcast(deviceName: String, port: Int) {
        broadcastJob?.cancel()
        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val msg = "GAMEPAD_SERVER:$deviceName".toByteArray()
                while (true) {
                    val packet = DatagramPacket(msg, msg.size,
                        InetAddress.getByName("255.255.255.255"), port)
                    socket.send(packet)
                    delay(3000)
                }
            } catch (_: Exception) {}
        }
    }

    fun unpairDevice() {
        scope.launch {
            pairingStateRepository.clearPairedDevice()
            stopBluetooth()
            _connectionState.value = ConnectionState()
        }
    }

    fun stopServer() {
        broadcastJob?.cancel()
        broadcastJob = null
        serverJob?.cancel()
        serverJob = null
        btPhaseJob?.cancel()
        btPhaseJob = null
        tcpServer.stop()
        stopBluetooth()
        _connectionState.value = ConnectionState()
    }

    private fun stopBluetooth() {
        btPhaseJob?.cancel()
        btPhaseJob = null
        bluetoothService?.stop()
        bluetoothService = null
    }

    var onControllerModeChanged: ((ControllerMode) -> Unit)? = null
    var onGyroCalibrationRequested: ((durationMs: Int) -> Unit)? = null

    private fun handleClientMessage(data: ByteArray) {
        try {
            val msg = com.zyz4.gamepademu.proto.ServerToClient.parseFrom(data)
            when (msg.payloadCase) {
                com.zyz4.gamepademu.proto.ServerToClient.PayloadCase.VIBRATION -> {
                    triggerVibration(msg.vibration.motorSpeed.toInt())
                }
                com.zyz4.gamepademu.proto.ServerToClient.PayloadCase.DISCONNECT -> {
                    _connectionState.value = _connectionState.value.copy(
                        connected = false, statusText = "客户端断开"
                    )
                }
                com.zyz4.gamepademu.proto.ServerToClient.PayloadCase.SET_CONTROLLER_MODE -> {
                    val protoMode = msg.setControllerMode.mode
                    val newMode = if (protoMode == com.zyz4.gamepademu.proto.ControllerMode.DS4)
                        ControllerMode.DS4 else ControllerMode.XBOX_360
                    _settings.value = _settings.value.copy(controllerMode = newMode)
                    scope.launch { settingsRepository.saveSettings(_settings.value) }
                    resendHello(newMode)
                    onControllerModeChanged?.invoke(newMode)
                }
                com.zyz4.gamepademu.proto.ServerToClient.PayloadCase.START_GYRO_CALIBRATION -> {
                    val durationMs = msg.startGyroCalibration.durationMs
                    onGyroCalibrationRequested?.invoke(durationMs.toInt())
                }
                else -> {}
            }
        } catch (_: Exception) {}
    }

    private fun resendHello(mode: ControllerMode) {
        val protoMode = com.zyz4.gamepademu.proto.ControllerMode.forNumber(mode.ordinal)
            ?: com.zyz4.gamepademu.proto.ControllerMode.XBOX_360
        val hello = com.zyz4.gamepademu.proto.Hello.newBuilder()
            .setProtocolVersion(1)
            .setDeviceName(android.os.Build.MODEL)
            .setControllerMode(protoMode)
            .build()
        val msg = com.zyz4.gamepademu.proto.ClientToServer.newBuilder()
            .setHello(hello)
            .build()
        scope.launch { tcpServer.send(msg) }
    }

    fun triggerVibration(speed: Int) {
        val amplitude = min(255, speed).let { s ->
            if (s < 1) return
            (s * VibrationEffect.DEFAULT_AMPLITUDE / 255).coerceAtLeast(1)
        }
        val effect = VibrationEffect.createOneShot(50, amplitude)
        vibrator.cancel()
        vibrator.vibrate(effect)
    }

    private fun updateRate(sendTimeMs: Double) {
        sendTimeEma = EMA_ALPHA * sendTimeMs + (1.0 - EMA_ALPHA) * sendTimeEma

        val newRate = when {
            sendTimeEma > BACKOFF_THRESHOLD_MS -> RATE_LOW
            sendTimeEma < RESTORE_THRESHOLD_MS -> RATE_HIGH
            else -> currentPollingRate
        }
        if (newRate != currentPollingRate) {
            currentPollingRate = newRate
            _pollingIntervalMs.value = 1000 / newRate
        }
    }

    suspend fun sendGamepadState(state: com.zyz4.gamepademu.proto.GamepadInput) {
        when (_settings.value.connectionMode) {
            ConnectionMode.WIFI -> {
                if (tcpServer.isClientConnected) {
                    val msg = com.zyz4.gamepademu.proto.ClientToServer.newBuilder()
                        .setGamepadInput(state).build()
                    val start = System.nanoTime()
                    tcpServer.send(msg)
                    val elapsed = (System.nanoTime() - start) / 1_000_000.0
                    updateRate(elapsed)
                }
            }
            ConnectionMode.BLUETOOTH -> {
                val target = _settings.value.targetPlatform
                val report = GamepadStateMapper.map(state, target)
                bluetoothService?.sendReport(report)
            }
        }
    }

    suspend fun sendGyroCalibration(biasX: Float, biasY: Float, biasZ: Float) {
        if (_settings.value.connectionMode != ConnectionMode.WIFI) return
        if (!tcpServer.isClientConnected) return
        val cal = com.zyz4.gamepademu.proto.GyroCalibration.newBuilder()
            .setBiasX(biasX)
            .setBiasY(biasY)
            .setBiasZ(biasZ)
            .build()
        val msg = com.zyz4.gamepademu.proto.ClientToServer.newBuilder()
            .setGyroCalibration(cal)
            .build()
        tcpServer.send(msg)
    }

    fun getServerIp(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address) return addr.hostAddress ?: ""
                }
            }
            ""
        } catch (_: Exception) { "" }
    }
}
