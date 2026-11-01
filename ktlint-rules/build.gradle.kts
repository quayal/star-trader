import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
	kotlin("jvm")
	id("org.jlleitschuh.gradle.ktlint")
}

val ktlintVersion: String by project

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// ktlint supplies these when it runs the rules, so the ruleset jar doesn't bring its own copy.
	compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core:$ktlintVersion")
	compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core:$ktlintVersion")

	testImplementation("com.pinterest.ktlint:ktlint-cli-ruleset-core:$ktlintVersion")
	testImplementation("com.pinterest.ktlint:ktlint-test:$ktlintVersion")
	testImplementation(platform("org.junit:junit-bom:5.12.2"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// ktlint-test logs through SLF4J but leaves the binding to the caller.
	testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
		// The rules run on ktlint's own Kotlin runtime (2.1 for ktlint 1.5), so they mustn't call anything newer.
		apiVersion = KotlinVersion.KOTLIN_2_1
		languageVersion = KotlinVersion.KOTLIN_2_1
	}
}

ktlint {
	version = ktlintVersion
}

tasks.withType<Test> {
	useJUnitPlatform()
}
