plugins {
    kotlin("jvm") version "1.7.10"
}

group = "xyz.scootaloo"
version = "0.1"


repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {

    implementation("io.vertx:vertx-core:4.3.6")
    implementation("io.vertx:vertx-web:4.3.6")
    implementation("io.vertx:vertx-lang-kotlin:4.3.6")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:4.3.6")

    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-core:1.2.11")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    testImplementation(kotlin("test"))
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-Xjvm-default=enable"
        )
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

