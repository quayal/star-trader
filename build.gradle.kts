import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "dev.adriangrzebyk"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin:3.2.3")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.assertj:assertj-core")
	testImplementation("org.junit.jupiter:junit-jupiter-params")
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

val checkTokens by tasks.registering(Exec::class) {
	description = "Fails when a color literal appears in the static frontend outside css/tokens.css."
	group = "verification"
	inputs.dir("src/main/resources/static")
	inputs.file("scripts/check-tokens.sh")
	outputs.upToDateWhen { false }
	commandLine("bash", "scripts/check-tokens.sh")
}

tasks.named("check") {
	dependsOn(checkTokens)
}

// Serve static files straight from src/ during bootRun so prototype and page edits show on refresh.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	sourceResources(sourceSets.main.get())
}
