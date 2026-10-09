package ma.elaroui.pos.desktop.display

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import ma.elaroui.pos.shared.display.CustomerDisplayConfig
import ma.elaroui.pos.shared.display.VfdBaudRate
import ma.elaroui.pos.shared.display.VfdDataBits
import ma.elaroui.pos.shared.display.VfdParity
import ma.elaroui.pos.shared.display.VfdStopBits
import ma.elaroui.pos.shared.display.VfdTransport
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class DesktopSerialVfdTransport : VfdTransport {

    private var activeConfig: CustomerDisplayConfig? = null
    private var outputStream: OutputStream? = null
    private var winHandle: WinNT.HANDLE? = null
    private var _isConnected: Boolean = false

    override val isConnected: Boolean get() = _isConnected

    @Synchronized
    override fun connect(config: CustomerDisplayConfig): Result<Unit> {
        disconnect()
        if (config.portName.isBlank()) {
            return Result.failure(IllegalArgumentException("Aucun port série spécifié"))
        }

        activeConfig = config
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

        return runCatching {
            if (isWindows) {
                connectWindows(config)
            } else {
                connectLinux(config)
            }
            _isConnected = true
        }.onFailure {
            disconnect()
            _isConnected = false
        }
    }

    private fun connectWindows(config: CustomerDisplayConfig) {
        val directFile = File(config.portName)
        if (directFile.exists() && directFile.isFile) {
            outputStream = FileOutputStream(directFile, true)
            return
        }

        val portDeviceName = if (config.portName.startsWith("\\\\.\\")) {
            config.portName
        } else {
            "\\\\.\\" + config.portName.trim()
        }

        try {
            // First attempt: Win32 CreateFile with DCB serial setup
            val handle = Kernel32.INSTANCE.CreateFile(
                portDeviceName,
                WinNT.GENERIC_READ or WinNT.GENERIC_WRITE,
                0,
                null,
                WinNT.OPEN_EXISTING,
                0,
                null
            )

            if (handle != null && handle != WinBase.INVALID_HANDLE_VALUE) {
                winHandle = handle

                // Configure DCB
                val dcb = WinBase.DCB()
                if (Kernel32.INSTANCE.GetCommState(handle, dcb)) {
                    dcb.BaudRate = com.sun.jna.platform.win32.WinDef.DWORD(config.baudRate.rate.toLong())
                    dcb.ByteSize = com.sun.jna.platform.win32.WinDef.BYTE(config.dataBits.bits.toLong())
                    dcb.StopBits = com.sun.jna.platform.win32.WinDef.BYTE(
                        when (config.stopBits) {
                            VfdStopBits.ONE -> WinBase.ONESTOPBIT.toLong()
                            VfdStopBits.TWO -> WinBase.TWOSTOPBITS.toLong()
                        }
                    )
                    dcb.Parity = com.sun.jna.platform.win32.WinDef.BYTE(
                        when (config.parity) {
                            VfdParity.NONE -> WinBase.NOPARITY.toLong()
                            VfdParity.EVEN -> WinBase.EVENPARITY.toLong()
                            VfdParity.ODD -> WinBase.ODDPARITY.toLong()
                        }
                    )
                    Kernel32.INSTANCE.SetCommState(handle, dcb)
                }

                // Configure timeouts (100ms read/write timeout)
                val timeouts = WinBase.COMMTIMEOUTS()
                timeouts.ReadIntervalTimeout = com.sun.jna.platform.win32.WinDef.DWORD(50L)
                timeouts.ReadTotalTimeoutMultiplier = com.sun.jna.platform.win32.WinDef.DWORD(10L)
                timeouts.ReadTotalTimeoutConstant = com.sun.jna.platform.win32.WinDef.DWORD(100L)
                timeouts.WriteTotalTimeoutMultiplier = com.sun.jna.platform.win32.WinDef.DWORD(10L)
                timeouts.WriteTotalTimeoutConstant = com.sun.jna.platform.win32.WinDef.DWORD(100L)
                Kernel32.INSTANCE.SetCommTimeouts(handle, timeouts)
                return
            }
        } catch (_: Throwable) {
            // Fallback to FileOutputStream
        }

        // Secondary fallback: FileOutputStream to \\.\COMx
        val file = File(portDeviceName)
        outputStream = FileOutputStream(file)
    }

    private fun connectLinux(config: CustomerDisplayConfig) {
        val devFile = File(config.portName)
        if (!devFile.exists()) {
            throw IllegalArgumentException("Périphérique introuvable : ${config.portName}")
        }

        // Configure baud rate using stty if available
        try {
            val parityArg = when (config.parity) {
                VfdParity.NONE -> "-parenb"
                VfdParity.EVEN -> "parenb -parodd"
                VfdParity.ODD -> "parenb parodd"
            }
            val stopBitsArg = if (config.stopBits == VfdStopBits.TWO) "cstopb" else "-cstopb"
            val dataBitsArg = "cs${config.dataBits.bits}"

            val pb = ProcessBuilder(
                "stty", "-F", config.portName,
                config.baudRate.rate.toString(),
                dataBitsArg,
                stopBitsArg,
                parityArg,
                "raw", "-echo"
            )
            pb.start().waitFor()
        } catch (_: Throwable) {
            // stty may be unavailable or restricted, proceed with direct stream
        }

        outputStream = FileOutputStream(devFile, true)
    }

    @Synchronized
    override fun disconnect() {
        _isConnected = false
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (_: Throwable) {}
        outputStream = null

        val h = winHandle
        if (h != null && h != WinBase.INVALID_HANDLE_VALUE) {
            try {
                Kernel32.INSTANCE.CloseHandle(h)
            } catch (_: Throwable) {}
            winHandle = null
        }
    }

    @Synchronized
    override fun send(bytes: ByteArray): Result<Unit> {
        val h = winHandle
        if (h != null && h != WinBase.INVALID_HANDLE_VALUE) {
            val written = com.sun.jna.ptr.IntByReference()
            val success = Kernel32.INSTANCE.WriteFile(h, bytes, bytes.size, written, null)
            return if (success) {
                Result.success(Unit)
            } else {
                _isConnected = false
                Result.failure(IllegalStateException("Échec d'écriture série Win32 (code ${Kernel32.INSTANCE.GetLastError()})"))
            }
        }

        val stream = outputStream
        if (stream != null) {
            return runCatching {
                stream.write(bytes)
                stream.flush()
            }.onFailure {
                _isConnected = false
            }
        }

        // Try reconnecting once if config is available
        val cfg = activeConfig
        if (cfg != null && cfg.enabled) {
            val reconnectResult = connect(cfg)
            if (reconnectResult.isSuccess) {
                return send(bytes)
            }
        }

        return Result.failure(IllegalStateException("Afficheur client non connecté"))
    }

    override fun isDeviceAvailable(portName: String): Boolean {
        if (portName.isBlank()) return false
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        return if (isWindows) {
            val target = if (portName.startsWith("\\\\.\\")) portName else "\\\\.\\$portName"
            File(target).exists() || CustomerDisplayDeviceDetector.detectDevices().any { it.portName.equals(portName, ignoreCase = true) }
        } else {
            File(portName).exists()
        }
    }
}
