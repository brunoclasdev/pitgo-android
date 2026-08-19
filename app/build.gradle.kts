import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
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

private fun Project.supabaseProperty(name: String): String? {
    val gradleProperty = providers.gradleProperty(name)
    if (gradleProperty.isPresent) return gradleProperty.get()

    val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
    val localPropertiesText = providers.fileContents(localPropertiesFile).asText.orNull
        ?: return null

    return runCatching {
        Properties().apply { load(localPropertiesText.reader()) }.getProperty(name)
    }.getOrNull()
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
            "'supabaseUrl' is required. Provide it through a Gradle project property, " +
                "ORG_GRADLE_PROJECT_supabaseUrl, or local.properties."
        !isValidSupabaseUrl(url) -> errors +=
            "'supabaseUrl' must be an absolute HTTPS URL with a host and no user-info."
    }

    when {
        key.isEmpty() -> errors +=
            "'supabaseKey' is required. Provide it through a Gradle project property, " +
                "ORG_GRADLE_PROJECT_supabaseKey, or local.properties."
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
}

private val supabaseConfiguration = validateSupabaseConfiguration(
    rawUrl = project.supabaseProperty("supabaseUrl"),
    rawKey = project.supabaseProperty("supabaseKey"),
)

android {
    namespace = "com.trustarx.pitgo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trustarx.pitgo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            javaStringLiteral(supabaseConfiguration.url),
        )
        buildConfigField(
            "String",
            "SUPABASE_KEY",
            javaStringLiteral(supabaseConfiguration.key),
        )
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
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
    implementation(libs.ktor.client.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
