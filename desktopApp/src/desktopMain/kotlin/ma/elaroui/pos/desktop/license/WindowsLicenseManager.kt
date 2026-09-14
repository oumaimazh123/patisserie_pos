package ma.elaroui.pos.desktop.license

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class WindowsLicenseStatus { TRIAL_ACTIVE, TRIAL_EXPIRED, VALID, EXPIRED, INVALID, WRONG_DEVICE, CLOCK_ROLLBACK }
data class WindowsLicenseState(val status:WindowsLicenseStatus,val installationId:String,val daysRemaining:Int=0,val customer:String?=null,val expiresAt:Long?=null) { val allowsUse get()=status==WindowsLicenseStatus.TRIAL_ACTIVE||status==WindowsLicenseStatus.VALID }

interface DesktopLicenseStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class WindowsLicenseManager(
    private val storage: DesktopLicenseStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    publicKeyPem: String = PUBLIC_KEY,
    private val deviceFingerprintProvider: DeviceFingerprintProvider = WindowsDeviceFingerprintProvider(storage)
) {
    private val publicKey=parsePublicKey(publicKeyPem)
    val installationId:String by lazy {
        runCatching { deviceFingerprintProvider.getFingerprint() }.getOrDefault(UNAVAILABLE_DEVICE_ID)
    }
    fun requestCode():String { val json="{\"appId\":\"$APP_ID\",\"productId\":\"$PRODUCT_ID\",\"version\":\"1.0\",\"installationId\":\"$installationId\"}";return Base64.getEncoder().encodeToString(json.toByteArray()) }
    fun state():WindowsLicenseState = runCatching { evaluateState() }
        .getOrElse { WindowsLicenseState(WindowsLicenseStatus.INVALID, installationId) }

    private fun evaluateState():WindowsLicenseState {
        val time=now();val last=storage.get("last_trusted")?.toLongOrNull()?:0
        if(last>0&&time<last-300_000)return WindowsLicenseState(WindowsLicenseStatus.CLOCK_ROLLBACK,installationId)
        storage.put("last_trusted",maxOf(last,time).toString())
        storage.get("license")?.let{return verify(it,time)}
        val first=storage.get("first_launch")?.toLongOrNull()?:time.also{storage.put("first_launch",it.toString())}
        val remaining=first+TRIAL_MS-time
        return if(remaining>0)WindowsLicenseState(WindowsLicenseStatus.TRIAL_ACTIVE,installationId,((remaining+86_399_999)/86_400_000).toInt()) else WindowsLicenseState(WindowsLicenseStatus.TRIAL_EXPIRED,installationId)
    }
    fun activate(value:String):WindowsLicenseState = runCatching {
        val result=verify(value.trim(),now());if(result.status==WindowsLicenseStatus.VALID)storage.put("license",value.trim());result
    }.getOrElse { WindowsLicenseState(WindowsLicenseStatus.INVALID, installationId) }
    fun verify(value:String,time:Long=now()):WindowsLicenseState=runCatching {
        val parts=value.split('.');require(parts.size==2);val payload=String(Base64.getDecoder().decode(parts[0]),StandardCharsets.UTF_8);val signature=Base64.getDecoder().decode(parts[1]);val verifier=Signature.getInstance(if(publicKey.algorithm=="EC")"SHA256withECDSA"else"SHA256withRSA");verifier.initVerify(publicKey);verifier.update(payload.toByteArray());if(!verifier.verify(signature))return WindowsLicenseState(WindowsLicenseStatus.INVALID,installationId)
        fun text(k:String)=Regex("\"$k\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
        fun number(k:String)=Regex("\"$k\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLongOrNull()
        if(text("appId")!=APP_ID||text("productId")!=PRODUCT_ID)return WindowsLicenseState(WindowsLicenseStatus.INVALID,installationId)
        if(!text("installationId").equals(installationId,true))return WindowsLicenseState(WindowsLicenseStatus.WRONG_DEVICE,installationId)
        val expiry=number("expirationDateMs");if(expiry!=null&&time>expiry)return WindowsLicenseState(WindowsLicenseStatus.EXPIRED,installationId,expiresAt=expiry)
        WindowsLicenseState(WindowsLicenseStatus.VALID,installationId,customer=text("customerName"),expiresAt=expiry)
    }.getOrElse{WindowsLicenseState(WindowsLicenseStatus.INVALID,installationId)}
    companion object { const val APP_ID="ma.elaroui.generalpos";const val PRODUCT_ID="GENERAL_POS_V1";const val TRIAL_MS=604_800_000L;const val PUBLIC_KEY="""-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECVmpTz2FAi0yd0F8O4A7CkO/10qD
5HyZx/xT53ZaIIqBDnGv4FJlWZcDpBGPVVIJPD8q/neVHP5WoTFB1pCAmw==
-----END PUBLIC KEY-----"""
        const val UNAVAILABLE_DEVICE_ID="DEVICE-UNAVAILABLE"
        private fun parsePublicKey(pem:String):PublicKey { val bytes=Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replace("\\s".toRegex(),""));return listOf("EC","RSA").firstNotNullOf{runCatching{KeyFactory.getInstance(it).generatePublic(X509EncodedKeySpec(bytes))}.getOrNull()} }
    }
}

class WindowsSecureStore(private val file:Path) : DesktopLicenseStore {
    init { runCatching { file.parent?.let(Files::createDirectories) } }
    private fun load():MutableMap<String,String> = if(!Files.exists(file))mutableMapOf() else runCatching {
        val rawBytes = Files.readAllBytes(file)
        val plain = runCatching { Crypt32Util.cryptUnprotectData(rawBytes) }.getOrElse { rawBytes }
        String(plain, StandardCharsets.UTF_8).lineSequence().mapNotNull { line ->
            line.indexOf('=').takeIf { it > 0 }?.let { i ->
                line.substring(0, i) to runCatching { String(Base64.getDecoder().decode(line.substring(i + 1)), StandardCharsets.UTF_8) }.getOrDefault(line.substring(i + 1))
            }
        }.toMap().toMutableMap()
    }.getOrDefault(mutableMapOf())

    @Synchronized override fun get(key:String): String? = runCatching { load()[key] }.getOrNull()

    @Synchronized override fun put(key:String, value:String) {
        runCatching {
            val data = load()
            data[key] = value
            val text = data.entries.joinToString("\n") { "${it.key}=${Base64.getEncoder().encodeToString(it.value.toByteArray())}" }
            val protected = runCatching { Crypt32Util.cryptProtectData(text.toByteArray()) }.getOrElse { text.toByteArray() }
            Files.write(file, protected)
        }
    }
}
