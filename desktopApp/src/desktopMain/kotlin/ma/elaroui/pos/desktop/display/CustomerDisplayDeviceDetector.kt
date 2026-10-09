package ma.elaroui.pos.desktop.display

import java.io.File

data class DiscoveredCustomerDisplayDevice(
    val portName: String,
    val description: String,
    val isUsb: Boolean
)

object CustomerDisplayDeviceDetector {

    fun detectDevices(): List<DiscoveredCustomerDisplayDevice> {
        val os = System.getProperty("os.name", "").lowercase()
        return if (os.contains("win")) {
            detectWindowsPorts()
        } else {
            detectLinuxPorts()
        }
    }

    private fun detectWindowsPorts(): List<DiscoveredCustomerDisplayDevice> {
        val ports = linkedSetOf<String>()

        // 1. Read Windows Registry SERIALCOMM
        try {
            val hklm = com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE
            val path = "HARDWARE\\DEVICEMAP\\SERIALCOMM"
            val values = com.sun.jna.platform.win32.Advapi32Util.registryGetValues(hklm, path)
            for ((_, portVal) in values) {
                val p = portVal.toString().trim()
                if (p.isNotBlank()) {
                    ports.add(p)
                }
            }
        } catch (_: Throwable) {
            // Registry key might not exist or access denied
        }

        // 2. Standard common COM ports fallback probe
        for (i in 1..16) {
            val portName = "COM$i"
            if (portName !in ports) {
                // Quick probe via Win32 or File exists
                val probeFile = File("\\\\.\\$portName")
                if (probeFile.exists()) {
                    ports.add(portName)
                }
            }
        }

        val virtualWinFile = File(System.getProperty("java.io.tmpdir"), "virtual_customer_display.bin")
        if (virtualWinFile.exists()) {
            ports.add(virtualWinFile.absolutePath)
        }

        if (ports.isEmpty()) {
            // Provide standard defaults so user can select manually
            ports.addAll(listOf("COM1", "COM2", "COM3", "COM4"))
        }

        return ports.map { name ->
            val isVirtual = name.contains("virtual_customer_display", ignoreCase = true)
            val isUsb = name.contains("USB", ignoreCase = true) || name.startsWith("COM3") || name.startsWith("COM4") || isVirtual
            val desc = if (isVirtual) "Afficheur Client Digital Virtuel (CI / Test)" else "Port série Windows ($name)"
            DiscoveredCustomerDisplayDevice(
                portName = name,
                description = desc,
                isUsb = isUsb
            )
        }
    }

    private fun detectLinuxPorts(): List<DiscoveredCustomerDisplayDevice> {
        val devices = mutableListOf<DiscoveredCustomerDisplayDevice>()

        val virtualLinuxFile = File("/tmp/virtual_customer_display.bin")
        if (virtualLinuxFile.exists()) {
            devices.add(
                DiscoveredCustomerDisplayDevice(
                    portName = virtualLinuxFile.absolutePath,
                    description = "Afficheur Client Digital Virtuel (CI / Test)",
                    isUsb = true
                )
            )
        }

        val devDir = File("/dev")
        if (devDir.exists() && devDir.isDirectory) {
            val files = devDir.listFiles() ?: emptyArray()
            files.forEach { file ->
                val name = file.name
                if (name.startsWith("ttyUSB") || name.startsWith("ttyACM")) {
                    devices.add(
                        DiscoveredCustomerDisplayDevice(
                            portName = file.absolutePath,
                            description = "Afficheur USB Linux (${file.absolutePath})",
                            isUsb = true
                        )
                    )
                } else if (name.startsWith("ttyS") && name.removePrefix("ttyS").toIntOrNull() in 0..3) {
                    devices.add(
                        DiscoveredCustomerDisplayDevice(
                            portName = file.absolutePath,
                            description = "Port série RS-232 Linux (${file.absolutePath})",
                            isUsb = false
                        )
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            devices.add(DiscoveredCustomerDisplayDevice("/dev/ttyUSB0", "Afficheur USB standard (/dev/ttyUSB0)", true))
            devices.add(DiscoveredCustomerDisplayDevice("/dev/ttyACM0", "Afficheur ACM standard (/dev/ttyACM0)", true))
            devices.add(DiscoveredCustomerDisplayDevice("/dev/ttyS0", "Port série standard (/dev/ttyS0)", false))
        }

        return devices
    }
}
