import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val liteRtLmVersion = "0.19.1"

android {
    namespace = "dev.hanzicho.hyperchat"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hanzicho.hyperchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        ndk.abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
        buildConfigField("String", "LITERT_LM_VERSION", "\"$liteRtLmVersion\"")
    }
    buildFeatures.buildConfig = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources {
        val noticeGlobs = listOf("/LICENSE", "/NOTICE", "/META-INF/LICENSE", "/META-INF/NOTICE")
        excludes.removeAll(noticeGlobs.flatMap { listOf(it, "$it.txt") }.toSet())
        merges.addAll(noticeGlobs.map { "$it*" } + "/META-INF/DEPENDENCIES")
    }
}

kotlin { jvmToolchain(17) }

// AAR-root notices are outside classes.jar and AGP does not automatically package them.
abstract class CollectAarNotices : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val aarFiles: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject abstract val archiveOperations: ArchiveOperations
    @get:Inject abstract val fileOperations: FileSystemOperations

    @TaskAction fun collect() {
        val inputs = aarFiles.files.sortedBy { it.name }
        fileOperations.sync {
            into(outputDirectory)
            includeEmptyDirs = false
            duplicatesStrategy = DuplicatesStrategy.FAIL
            for (aar in inputs) {
                from(archiveOperations.zipTree(aar)) {
                    include("LICENSE*", "NOTICE*", "COPYING*", "THIRD_PARTY_NOTICE*", "third_party_licenses.*")
                    into("licenses/dependencies/${aar.nameWithoutExtension}")
                }
            }
        }
    }
}

androidComponents.onVariants { variant ->
    val aars = variant.runtimeConfiguration.incoming.artifacts.artifactFiles.filter { it.extension == "aar" }
    val notices = tasks.register<CollectAarNotices>("collect${variant.name.replaceFirstChar { it.uppercase() }}AarNotices") {
        aarFiles.from(aars)
    }
    variant.sources.assets?.addGeneratedSourceDirectory(notices, CollectAarNotices::outputDirectory)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmVersion")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
