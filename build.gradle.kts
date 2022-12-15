
plugins {
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.7.0"
    id("net.mamoe.mirai-console") version "2.13.0"
}

group = "KonnyakuCamp"
version = "0.4.9"

repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
}

val exposedVersion = "0.32.1"
val hikariVersion = "5.0.0"
val mysqlVersion = "8.0.25"
val quartzVersion = "2.3.2"
val miraiSlf4jBridgeVersion = "1.2.0"
val sqliteVersion = "3.36.0.3"

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("mysql:mysql-connector-java:$mysqlVersion")
    implementation("org.quartz-scheduler:quartz:$quartzVersion")
    implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    implementation("io.ktor:ktor-client-core:2.2.1")
    implementation("io.ktor:ktor-client-okhttp:2.2.1")
    //implementation("org.slf4j:slf4j-simple:1.7.1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
