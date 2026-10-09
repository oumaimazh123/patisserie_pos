import java.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ma.elaroui.pos"
    compileSdk = 37

    defaultConfig {
        applicationId = "ma.elaroui.generalpos"
        minSdk = 29
        targetSdk = 36
        versionCode = 17
        versionName = "1.2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":sharedLogic"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val licenseRequestCodeProvider = providers.gradleProperty("requestCode")
val licenseCustomerProvider = providers.gradleProperty("customer")
val licenseDaysProvider = providers.gradleProperty("days")
val licenseMinutesProvider = providers.gradleProperty("minutes")
val licensePrivateKeyPathProvider = providers.gradleProperty("privateKeyPath")
    .orElse(providers.environmentVariable("POS_LICENSE_PRIVATE_KEY"))
val licenseProjectDirectory = layout.projectDirectory.asFile.absolutePath

tasks.register("generateLicense") {
    group = "licensing"
    description = "Generates a signed license key from an external EC or RSA private key"
    notCompatibleWithConfigurationCache(
        "License signing intentionally executes in-process and reads an external private key."
    )
    doLast {
        val reqCode = licenseRequestCodeProvider.orNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error(
                "Usage: ./gradlew generateLicense -PrequestCode=\"<BASE64_CODE>\" " +
                    "-PprivateKeyPath=\"<PKCS8_PRIVATE_KEY.pem>\" " +
                    "[-Pcustomer=\"<NAME>\"] [-Pdays=<DAYS> | -Pminutes=<MINUTES>]"
            )
        val customerName = licenseCustomerProvider.orNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Client POS"
        val days = licenseDaysProvider.orNull?.trim()?.toLongOrNull()?.also {
            require(it > 0) { "days must be a positive number." }
        }
        val minutes = licenseMinutesProvider.orNull?.trim()?.toLongOrNull()?.also {
            require(it > 0) { "minutes must be a positive number." }
        }
        require(days == null || minutes == null) {
            "Use either -Pdays or -Pminutes, not both."
        }

        val privateKeyPath = licensePrivateKeyPathProvider.orNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error(
                "Private key path is required. Pass -PprivateKeyPath=\"<PKCS8_PRIVATE_KEY.pem>\" " +
                    "or set POS_LICENSE_PRIVATE_KEY."
            )
        val configuredPrivateKeyFile = File(privateKeyPath)
        val privateKeyFile = if (configuredPrivateKeyFile.isAbsolute) {
            configuredPrivateKeyFile
        } else {
            File(licenseProjectDirectory, privateKeyPath)
        }
        require(privateKeyFile.isFile) {
            "Private key file not found: ${privateKeyFile.absolutePath}"
        }

        val pem = privateKeyFile.readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(pem)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val privateKey = listOf("EC", "RSA").firstNotNullOfOrNull { algorithm ->
            runCatching {
                KeyFactory.getInstance(algorithm).generatePrivate(spec)
            }.getOrNull()
        } ?: error("Unsupported or invalid PKCS#8 private key.")

        val reqJsonStr = runCatching {
            String(Base64.getDecoder().decode(reqCode), Charsets.UTF_8)
        }.getOrElse {
            error("Invalid request code: expected Base64-encoded request data.")
        }
        
        // Extract installationId from JSON string
        val instIdMatch = Regex("\"installationId\"\\s*:\\s*\"([^\"]+)\"").find(reqJsonStr)
        val instId = instIdMatch?.groupValues?.get(1)
            ?: error("Invalid request code: missing installationId")

        val appIdMatch = Regex("\"appId\"\\s*:\\s*\"([^\"]+)\"").find(reqJsonStr)
        val appId = appIdMatch?.groupValues?.get(1) ?: "ma.elaroui.generalpos"
        require(appId == "ma.elaroui.generalpos") {
            "Request code is for an unsupported application: $appId"
        }

        val prodIdMatch = Regex("\"productId\"\\s*:\\s*\"([^\"]+)\"").find(reqJsonStr)
        val productId = prodIdMatch?.groupValues?.get(1) ?: "GENERAL_POS_V1"
        require(productId == "GENERAL_POS_V1") {
            "Request code is for an unsupported product: $productId"
        }

        val issueMs = System.currentTimeMillis()
        val durationMs = when {
            days != null -> Math.multiplyExact(days, 24L * 60L * 60L * 1000L)
            minutes != null -> Math.multiplyExact(minutes, 60L * 1000L)
            else -> null
        }
        val expMs = durationMs?.let { Math.addExact(issueMs, it) }
        val licType = if (expMs != null) "SUBSCRIPTION" else "FULL_LIFETIME"
        val licId = "LIC-${issueMs.toString().takeLast(6)}"

        val expJson = if (expMs != null) ",\"expirationDateMs\":$expMs" else ""
        val payloadJson = "{\"licenseId\":\"$licId\",\"productId\":\"$productId\",\"appId\":\"$appId\",\"installationId\":\"$instId\",\"customerName\":\"$customerName\",\"issueDateMs\":$issueMs$expJson,\"licenseType\":\"$licType\",\"schemaVersion\":\"1.0\"}"

        val signatureAlgorithm = when (privateKey.algorithm.uppercase()) {
            "EC", "ECDSA" -> "SHA256withECDSA"
            "RSA" -> "SHA256withRSA"
            else -> error("Unsupported private-key algorithm: ${privateKey.algorithm}")
        }
        val sig = Signature.getInstance(signatureAlgorithm)
        sig.initSign(privateKey)
        sig.update(payloadJson.toByteArray(Charsets.UTF_8))
        val sigBytes = sig.sign()

        val payloadB64 = Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        val sigB64 = Base64.getEncoder().encodeToString(sigBytes)
        val licenseKey = "$payloadB64.$sigB64"

        println("\n==========================================================")
        println("   SUCCESSFULLY GENERATED SIGNED LICENSE KEY              ")
        println("   Signature: $signatureAlgorithm")
        println("==========================================================")
        println(licenseKey)
        println("==========================================================\n")
    }
}
