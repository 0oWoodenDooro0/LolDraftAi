plugins {
    application
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.loldraft"
version = "0.1.0-SNAPSHOT"

application {
    mainClass.set("com.loldraft.server.ApplicationKt")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    val ktorVersion = "3.1.1"

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")

    // Compose Multiplatform Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Ktor Server & WebSockets
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets:$ktorVersion")
}

tasks.register<JavaExec>("runCompose") {
    group = "application"
    description = "Runs the Compose Multiplatform Desktop application"
    mainClass.set("com.loldraft.client.compose.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
}

ktlint {
    filter {
        exclude("**/lol_draft_ai/**")
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

kotlin {
    jvmToolchain(21)
}
