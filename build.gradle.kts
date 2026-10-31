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
	implementation(platform("software.amazon.awssdk:bom:2.56.0"))
	implementation("software.amazon.awssdk:dynamodb")
	// bootRun starts DynamoDB Local from compose.yaml; tests and the packaged jar never get this.
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.assertj:assertj-core")
	testImplementation("org.junit.jupiter:junit-jupiter-params")
	testImplementation("org.testcontainers:testcontainers")

	// This project's own ktlint rules (engineering standards §5), run by ktlintCheck with the standard ones.
	ktlintRuleset(project(":ktlint-rules"))
}

val ktlintVersion: String by project

ktlint {
	version = ktlintVersion
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

val checkTokens = tasks.register<Exec>("checkTokens") {
	description = "Fails when a color literal appears in the static frontend outside css/tokens.css."
	group = "verification"
	inputs.dir("src/main/resources/static")
	inputs.file("scripts/check-tokens.sh")
	outputs.upToDateWhen { false }
	commandLine("bash", "scripts/check-tokens.sh")
}

// A clean JDK-only checkout still builds without Node; CI sets CI=true and never skips this.
val nodeOnPath = System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, "node").canExecute() }

val jsTest = tasks.register<Exec>("jsTest") {
	description = "Runs the static frontend's JavaScript unit tests with Node's built-in test runner."
	group = "verification"
	inputs.dir("src/main/resources/static/js")
	inputs.dir("src/test/js")
	outputs.upToDateWhen { false }
	onlyIf("node is on the PATH, or this is CI") {
		if (!nodeOnPath && System.getenv("CI") == null) {
			logger.warn("Skipping jsTest: node is not on the PATH.")
		}
		nodeOnPath || System.getenv("CI") != null
	}
	commandLine("node", "--test", "src/test/js/**/*.test.mjs")
}

tasks.named("check") {
	dependsOn(checkTokens, jsTest)
}

// Serve static files straight from src/ during bootRun so prototype and page edits show on refresh.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	sourceResources(sourceSets.main.get())
}
