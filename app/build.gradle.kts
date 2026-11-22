plugins {
    id("com.android.application")
}

// A test-phone build that writes WhatsApp's button labels to a file, for checking ControlsOnly:
//   ./gradlew assembleRelease -Psaathi.labelProbe=true
val labelProbe = providers.gradleProperty("saathi.labelProbe").orNull == "true"

// Reads BACKEND_URL and CLIENT_SECRET from the git-ignored secrets.properties into BuildConfig.
val secretsFile = rootProject.layout.projectDirectory.file("secrets.properties")
val secretsText: String = if (!labelProbe && secretsFile.asFile.exists()) {
    providers.fileContents(secretsFile).asText.get()
} else {
    ""
}
val backendUrl: String = Regex("(?m)^BACKEND_URL=(.+)$").find(secretsText)?.groupValues?.get(1)?.trim().orEmpty()
val clientSecret: String = Regex("(?m)^CLIENT_SECRET=(.+)$").find(secretsText)?.groupValues?.get(1)?.trim().orEmpty()
val keystorePath: String = Regex("(?m)^KEYSTORE_FILE=(.+)$").find(secretsText)?.groupValues?.get(1)?.trim().orEmpty()
val keystorePassword: String = Regex("(?m)^KEYSTORE_PASSWORD=(.+)$").find(secretsText)?.groupValues?.get(1)?.trim().orEmpty()
val keyAliasValue: String = Regex("(?m)^KEY_ALIAS=(.+)$").find(secretsText)?.groupValues?.get(1)?.trim().orEmpty()
val keyPasswordValue: String = Regex("(?m)^KEY_PASSWORD=(.+)$").find(secretsText)?.groupValues?.get(1)?.trim().orEmpty()
val keystoreFile = keystorePath.takeIf { it.isNotEmpty() }?.let { rootProject.file(it) }

android {
    namespace = "com.saathi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.getsaathi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "CLIENT_SECRET", "\"$clientSecret\"")
        buildConfigField("boolean", "LABEL_PROBE", labelProbe.toString())
        if (labelProbe) versionNameSuffix = "-probe"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystoreFile != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

// GuidesTest reads the router cases in eval/, so editing them reruns the tests.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.layout.projectDirectory.file("eval/router_cases.jsonl"))
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
        if (!allowlist.isNullOrBlank()) {
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
