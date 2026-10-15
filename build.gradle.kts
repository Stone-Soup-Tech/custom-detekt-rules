plugins {
    kotlin("jvm") version "2.4.21"
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
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")

    implementation("org.jetbrains.kotlin:kotlin-stdlib") {
        // Detekt 1.23.8 is not compatible with Kotlin 2.4.21, so we use an older version here.
        version { strictly("2.0.21") }
    }
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable") {
        // Detekt 1.23.8 is not compatible with Kotlin 2.4.21, so we use an older version here.
        version { strictly("2.0.21") }
    }
    testImplementation("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
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
