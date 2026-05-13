plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kensa.gradle)
}

kensa {
    sourceSets = setOf("test")
    site = true
    sourceTitles.put("test", "Unit & Integration Tests")
}

group = "com.clearwave"
version = "1.0.0"

repositories {
    mavenLocal()        // for Kensa snapshots / local builds
    mavenCentral()
    maven {
        name = "centralSnapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}

configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
}

dependencies {
    implementation(libs.http4k.core)
    implementation(libs.http4k.jackson)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)

    testImplementation(libs.kensa.testng)
    testImplementation(libs.testng)
    testImplementation(libs.kensa.kotest)
    testImplementation(libs.kensa.kotest.test.support)
    testImplementation(libs.kensa.kotest.test.support.xml)
    testImplementation(libs.kensa.kotest.test.support.json)
    testImplementation(libs.kensa.hamcrest)
    testImplementation(libs.hamcrest)
    testImplementation(libs.http4k.okhttp)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.assertions)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useTestNG()
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djava.awt.headless=true") })
}
