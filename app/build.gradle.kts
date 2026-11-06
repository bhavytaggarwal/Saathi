plugins {
    id("com.android.application")
}

// A personal build can carry a Gemini key, read from the git-ignored secrets.properties:
//   ./gradlew assembleRelease -Psaathi.embedKey=true
// Anyone holding that APK can extract the key, so it must never be shared or uploaded.
// The default build carries no key and asks for one in setup.
val embeddedGeminiKey: String = if (providers.gradleProperty("saathi.embedKey").orNull == "true") {
    val secrets = providers.fileContents(rootProject.layout.projectDirectory.file("secrets.properties")).asText.get()
    Regex("(?m)^GEMINI_API_KEY=(.+)$").find(secrets)?.groupValues?.get(1)?.trim()
        ?: throw GradleException("secrets.properties has no GEMINI_API_KEY")
} else {
    ""
}

android {
    namespace = "com.saathi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saathi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "EMBEDDED_GEMINI_KEY", "\"$embeddedGeminiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // v1 is installed directly on the phone, so the debug key is enough. A Play release
            // would need a real upload key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // The app has no runtime libraries beyond the Kotlin standard library. These are test-only.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// The product rule "Saathi never taps, types or swipes" is enforced here: any build fails if the
// source gains a way to act on another app, or if the service config gains a forbidden ability.
val auditReadOnly = tasks.register("auditReadOnly") {
    group = "verification"
    description = "Fails the build if Saathi gains any way to act on another app."
    val auditRoot = layout.projectDirectory.dir("src/main").asFile
    inputs.dir(auditRoot)
    doLast {
        val forbiddenCode = Regex(
            "performAction|performGlobalAction|dispatchGesture|GestureDescription|" +
                "ACTION_CLICK|ACTION_SET_TEXT|ACTION_PASTE|ACTION_SCROLL"
        )
        val problems = mutableListOf<String>()
        auditRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (forbiddenCode.containsMatchIn(line)) {
                    problems += "${file.name}:${index + 1} uses a forbidden action API"
                }
            }
        }
        val config = auditRoot.resolve("res/xml/accessibility_service_config.xml").readText()
        if (Regex("canPerformGestures\\s*=\\s*\"true\"").containsMatchIn(config)) {
            problems += "accessibility_service_config.xml declares canPerformGestures"
        }
        val allowlist = Regex("android:packageNames=\"([^\"]*)\"").find(config)?.groupValues?.get(1)
        if (allowlist.isNullOrBlank()) {
            problems += "accessibility_service_config.xml has no package allowlist"
        } else {
            listOf("whatsapp", "messaging", "truecaller", "android.gm", "systemui", "sbi", "hdfc", "icici")
                .filter { allowlist.contains(it, ignoreCase = true) }
                .forEach { problems += "allowlist contains never-allowed package: $it" }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Read-only audit failed:\n" + problems.joinToString("\n"))
        }
    }
}
tasks.named("preBuild") { dependsOn(auditReadOnly) }
