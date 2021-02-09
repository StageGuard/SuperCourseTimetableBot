
plugins {
    kotlin("jvm") version "1.4.20"
    kotlin("plugin.serialization") version "1.4.20"
    id("net.mamoe.mirai-console") version "2.0.0"
}

group = "KonnyakuCamp"
version = "0.3.5"

repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
    gradlePluginPortal()
}

val exposedVersion = "0.25.1"
val hikariVersion = "3.4.5"
val mysqlVersion = "8.0.19"
val yamlKtVersion = "0.7.4"
val quartzVersion = "2.3.2"
val miraiSlf4jBridgeVersion = "1.1.0"

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("mysql:mysql-connector-java:$mysqlVersion")
    implementation("net.mamoe.yamlkt:yamlkt-jvm:$yamlKtVersion")
    implementation("org.quartz-scheduler:quartz:$quartzVersion")
    implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")
    //implementation("org.slf4j:slf4j-simple:1.7.1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}