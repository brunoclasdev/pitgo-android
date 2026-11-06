import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Properties

private val publishableKeyPattern = Regex("^sb_publishable_[A-Za-z0-9_-]+$")
private val legacyJwtPattern = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
private val roleClaimPattern = Regex("\\\"role\\\"\\s*:")
private val unicodeEscapePattern = Regex("\\\\u[0-9A-Fa-f]{4}")

private data class SupabaseBuildConfiguration(
    val url: String,
    val key: String,
)

private data class MapStyleBuildConfiguration(
    val url: String,
    val providerName: String,
    val attribution: String,
    val attributionUrl: String,
)

private fun javaStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\u000C' -> append("\\f")
            '\r' -> append("\\r")
            else -> {
                if (character.code < 0x20 || character.code == 0x7F ||
                    character == '\u2028' || character == '\u2029'
                ) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

private fun validateMapStyleConfiguration(
    rawUrl: String?,
    rawProviderName: String?,
    rawAttribution: String?,
    rawAttributionUrl: String?,
): MapStyleBuildConfiguration {
    val url = rawUrl?.trim().orEmpty()
    val providerName = rawProviderName?.trim().orEmpty()
    val attribution = rawAttribution?.trim().orEmpty()
    val attributionUrl = rawAttributionUrl?.trim().orEmpty()
    val errors = mutableListOf<String>()

    if (!isValidManagedMapUrl(url, rejectKnownPublicHosts = true)) {
        errors += "'mapStyleUrl' must be a managed absolute HTTPS URL without user-info, " +
            "fragment, placeholder, or public/demo OSM host."
    }
    if (providerName.isBlank() || providerName.isPlaceholder()) {
        errors += "'mapStyleProviderName' must be a public non-placeholder provider name."
    }
    if (attribution.isBlank() || attribution.isPlaceholder() ||
        !attribution.contains("OpenStreetMap", ignoreCase = true)
    ) {
        errors += "'mapStyleAttribution' must include OpenStreetMap and provider attribution."
    }
    if (!isValidManagedMapUrl(attributionUrl, rejectKnownPublicHosts = false)) {
        errors += "'mapStyleAttributionUrl' must be an absolute HTTPS URL without user-info, " +
            "fragment, or placeholder."
    }

    if (errors.isNotEmpty()) {
        throw GradleException(
            errors.joinToString(
                separator = "\n- ",
                prefix = "Invalid MapLibre style configuration:\n- ",
            )
        )
    }

    return MapStyleBuildConfiguration(url, providerName, attribution, attributionUrl)
}

private fun isValidManagedMapUrl(value: String, rejectKnownPublicHosts: Boolean): Boolean =
    runCatching {
        if (value.isBlank() || value.isPlaceholder()) return@runCatching false
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        val knownPublicOrDemoHost = host == "tile.openstreetmap.org" ||
            host.endsWith(".tile.openstreetmap.org") ||
            host == "demotiles.maplibre.org"
        uri.isAbsolute && !uri.isOpaque && uri.scheme.equals("https", ignoreCase = true) &&
            host.isNotBlank() && uri.userInfo == null && uri.fragment == null &&
            (!rejectKnownPublicHosts || !knownPublicOrDemoHost)
    }.getOrDefault(false)

private fun String.isPlaceholder(): Boolean {
    val normalized = uppercase()
    return '<' in this || '>' in this || "CHANGE_ME" in normalized ||
        "YOUR_" in normalized || "PLACEHOLDER" in normalized
}

private fun validateSupabaseConfiguration(
    rawUrl: String?,
    rawKey: String?,
): SupabaseBuildConfiguration {
    val url = rawUrl?.trim().orEmpty()
    val key = rawKey?.trim().orEmpty()
    val errors = mutableListOf<String>()

    when {
        url.isEmpty() -> errors +=
            "'supabaseUrl' is required in the selected config/<environment>.properties file."
        !isValidSupabaseUrl(url) -> errors +=
            "'supabaseUrl' must be an absolute HTTPS URL with a host and no user-info."
    }

    when {
        key.isEmpty() -> errors +=
            "'supabaseKey' is required in the selected config/<environment>.properties file."
        !isClientSafeSupabaseKey(key) -> errors +=
            "'supabaseKey' must be a publishable key or legacy anon key; " +
                "secret and service-role keys are forbidden."
    }

    if (errors.isNotEmpty()) {
        throw GradleException(
            errors.joinToString(
                separator = "\n- ",
                prefix = "Invalid Supabase configuration:\n- ",
            )
        )
    }

    return SupabaseBuildConfiguration(url = url, key = key)
}

private fun isValidSupabaseUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.isAbsolute &&
        !uri.isOpaque &&
        uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null
}.getOrDefault(false)

private fun isClientSafeSupabaseKey(value: String): Boolean =
    publishableKeyPattern.matches(value) || isLegacyAnonKey(value)

private val mercadoPagoPublicKeyPattern = Regex("^(TEST-|APP_USR-)[A-Za-z0-9-]+$")

/**
 * Only the Mercado Pago Public Key ever reaches the app; the Access Token stays in Supabase
 * Secrets (see supabase/functions/mp-*). Still validated so a placeholder never ships silently.
 */
private fun validateMercadoPagoPublicKey(rawKey: String?): String {
    val key = rawKey?.trim().orEmpty()
    if (key.isBlank() || key.isPlaceholder() || !mercadoPagoPublicKeyPattern.matches(key)) {
        throw GradleException(
            "Invalid Mercado Pago configuration:\n- 'mercadoPagoPublicKey' must be a real " +
                "TEST-/APP_USR- public key (never the Access Token). Provide it through a " +
                "Gradle project property, ORG_GRADLE_PROJECT_mercadoPagoPublicKey, or " +
                "local.properties."
        )
    }
    return key
}

private fun isLegacyAnonKey(value: String): Boolean {
    if (!legacyJwtPattern.matches(value)) return false

    return runCatching {
        val payloadBytes = Base64.getUrlDecoder().decode(value.split('.')[1])
        val payload = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(payloadBytes))
            .toString()

        if (unicodeEscapePattern.containsMatchIn(payload) ||
            roleClaimPattern.findAll(payload).count() != 1
        ) {
            return@runCatching false
        }

        val claims = JsonSlurper().parseText(payload) as? Map<*, *>
            ?: return@runCatching false
        claims.keys.count { it == "role" } == 1 && claims["role"] == "anon"
    }.getOrDefault(false)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

private val environmentNames = listOf("dev", "homol", "prod")
private val allowedEnvironmentProperties = setOf("environmentId", "supabaseProjectRef", "supabaseUrl", "supabaseKey", "mapStyleUrl", "mapStyleProviderName", "mapStyleAttribution", "mapStyleAttributionUrl", "mercadoPagoPublicKey")
private fun loadEnvironment(name: String): Properties {
    val file = rootProject.file("config/$name.properties")
    if (!file.isFile) throw GradleException("Missing configuration file for $name")
    val values = Properties().apply { file.reader().use { load(it) } }
    if (values.stringPropertyNames().any { it !in allowedEnvironmentProperties }) throw GradleException("Unsupported configuration property for $name")
    if (values.getProperty("environmentId") != name) throw GradleException("Invalid environmentId for $name")
    val ref = values.getProperty("supabaseProjectRef").orEmpty()
    val url = values.getProperty("supabaseUrl").orEmpty()
    if (!Regex("[a-z0-9]{20}").matches(ref) || url != "https://$ref.supabase.co") throw GradleException("Invalid backend identity for $name")
    validateSupabaseConfiguration(url, values.getProperty("supabaseKey"))
    validateMapStyleConfiguration(values.getProperty("mapStyleUrl"), values.getProperty("mapStyleProviderName"), values.getProperty("mapStyleAttribution"), values.getProperty("mapStyleAttributionUrl"))
    if (name != "dev") {
        val publicKey = validateMercadoPagoPublicKey(values.getProperty("mercadoPagoPublicKey"))
        if (!publicKey.startsWith("APP_USR-")) throw GradleException("Real Mercado Pago public key required for $name")
    }
    return values
}
val validateEnvironmentIsolation = tasks.register("validateEnvironmentIsolation") {
    group = "verification"
    doLast {
        val configurations = environmentNames.map { loadEnvironment(it) }
        for (property in listOf("supabaseProjectRef", "supabaseUrl")) {
            if (configurations.first().getProperty(property) == configurations.last().getProperty(property)) {
                throw GradleException("Development backend must differ from production")
            }
        }
        for (property in allowedEnvironmentProperties - "environmentId") {
            if (configurations[1].getProperty(property) != configurations[2].getProperty(property)) {
                throw GradleException("Homol must mirror production configuration")
            }
        }
    }
}

android {
    namespace = "com.trustarx.pitgo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trustarx.pitgo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    flavorDimensions += "environment"
    productFlavors {
        environmentNames.forEach { environment ->
            create(environment) {
                dimension = "environment"
                buildConfigField("String", "ENVIRONMENT_ID", javaStringLiteral(environment))
                buildConfigField("boolean", "SIMULATED_PAYMENTS", (environment == "dev").toString())
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.42")
    testImplementation("junit:junit:4.13.2")
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.supabase.core)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.androidx.exifinterface)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.maplibre.android.opengl)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(platform(libs.mercadopago.sdk.bom))
    implementation(libs.mercadopago.sdk.core.methods)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val environment = variant.productFlavors.single { it.first == "environment" }.second
        val configurationFile = rootProject.layout.projectDirectory.file("config/$environment.properties")
        val values = providers.fileContents(configurationFile).asText.map { loadEnvironment(environment) }
        val fields = mapOf("SUPABASE_URL" to "supabaseUrl", "SUPABASE_KEY" to "supabaseKey", "SUPABASE_PROJECT_REF" to "supabaseProjectRef", "MAP_STYLE_URL" to "mapStyleUrl", "MAP_STYLE_PROVIDER_NAME" to "mapStyleProviderName", "MAP_STYLE_ATTRIBUTION" to "mapStyleAttribution", "MAP_STYLE_ATTRIBUTION_URL" to "mapStyleAttributionUrl", "MERCADO_PAGO_PUBLIC_KEY" to "mercadoPagoPublicKey")
        fields.forEach { (field, property) ->
            requireNotNull(variant.buildConfigFields).put(field, values.map { configuration ->
                com.android.build.api.variant.BuildConfigField("String", javaStringLiteral(if (environment == "dev" && property == "mercadoPagoPublicKey") "" else configuration.getProperty(property).orEmpty()), "Public environment configuration")
            })
        }
        tasks.matching {
            variant.buildType == "release" && it.name in listOf(
                "assemble${variant.name.replaceFirstChar { it.uppercase() }}",
                "bundle${variant.name.replaceFirstChar { it.uppercase() }}",
            )
        }.configureEach {
            dependsOn(validateEnvironmentIsolation)
        }
    }
}
