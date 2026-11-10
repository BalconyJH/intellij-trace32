plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.intellij.platform) apply false
}

allprojects {
    group = providers.gradleProperty("pluginGroup").get()
    version = providers.gradleProperty("pluginVersion").get()
}

tasks.check {
    dependsOn(":practice-core:check", ":intellij-plugin:check")
}

tasks.assemble {
    dependsOn(":intellij-plugin:buildPlugin")
}
