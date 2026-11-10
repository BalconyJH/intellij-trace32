import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val localPlatformPath = providers.gradleProperty("localPlatformPath")
val platformVersion = providers.gradleProperty("platformVersion")

dependencies {
    implementation(project(":practice-core"))
    testImplementation(libs.junit)

    intellijPlatform {
        if (localPlatformPath.isPresent) {
            local(localPlatformPath)
        } else {
            clion(platformVersion)
        }
        testFramework(TestFrameworkType.Platform)
        pluginVerifier(libs.versions.plugin.verifier)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        apiVersion = KotlinVersion.KOTLIN_2_2
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

intellijPlatform {
    projectName = "trace32-toolkit"
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    pluginVerification {
        ides {
            current()
        }
    }
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF") }
    from(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md")) { into("META-INF") }
}

tasks.test {
    useJUnit()
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
}
