plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

// JitPack resolves artifacts using the GitHub owner, repository name, and requested revision.
val isJitPack = providers.environmentVariable("JITPACK").orNull == "true"
group = if (isJitPack) {
    providers.environmentVariable("GROUP").get()
} else {
    "com.stonesouptech.detekt"
}
version = if (isJitPack) {
    providers.environmentVariable("VERSION").get()
} else {
    providers.gradleProperty("projectVersion").get()
}

repositories {
    mavenCentral()
}

dependencies {
    // Detekt supplies its API and compiler at runtime.
    compileOnly(libs.detekt.api)

    // The catalog strictly pins these to Detekt's embedded Kotlin version.
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.detekt.api)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("detektRules") {
            from(components["java"])
            artifactId = if (isJitPack) {
                providers.environmentVariable("ARTIFACT").get()
            } else {
                rootProject.name
            }
            pom {
                name.set("Android Detekt Rules")
                description.set("Custom Kotlin and Android coding conventions for Detekt 1.23.8")
            }
        }
    }
}
