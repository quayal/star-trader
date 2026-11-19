package dev.adriangrzebyk.trader

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * The package boundaries of engineering standards §1, §2 and §14, checked on the production sources: the domain
 * imports no framework, nothing but an adapter imports an adapter, and `board` and the `game` domain stay independent
 * of the packages that use them. A new concept package or adapter kind extends the lists here.
 */
class ArchitectureTest {

	private val files = Konsist.scopeFromProduction().files

	@Test
	fun shouldKeepFrameworksOutOfTheDomain() {
		files.filter { it.isDomainOf(CONCEPTS) }.assertFalse(testName = "domain imports a framework") { file ->
			file.importNames().any { name -> FRAMEWORKS.any { name.startsWith(it) } }
		}
	}

	@Test
	fun shouldImportAdaptersOnlyFromAdapters() {
		files.filterNot { it.isAdapter() }.assertFalse(testName = "non-adapter imports an adapter") { file ->
			file.importNames().any { ADAPTER in it }
		}
	}

	@Test
	fun shouldKeepTheBoardIndependentOfItsUsers() {
		files.filter { it.isIn("board") }.assertFalse(testName = "board imports game or login") { file ->
			file.importNames().any { it.startsWith("$ROOT.game.") || it.startsWith("$ROOT.login.") }
		}
	}

	@Test
	fun shouldKeepLoginOutOfTheGameDomain() {
		files.filter { it.isDomainOf(listOf("game")) }.assertFalse(testName = "game domain imports login") { file ->
			file.importNames().any { it.startsWith("$ROOT.login.") }
		}
	}

	private fun KoFileDeclaration.importNames(): List<String> = imports.map { it.name }

	private fun KoFileDeclaration.isIn(concept: String): Boolean {
		val name = packagee?.name ?: return false
		return name == "$ROOT.$concept" || name.startsWith("$ROOT.$concept.")
	}

	private fun KoFileDeclaration.isAdapter(): Boolean = packagee?.name?.contains(ADAPTER) == true

	private fun KoFileDeclaration.isDomainOf(concepts: List<String>): Boolean = concepts.any { isIn(it) } && !isAdapter()

	companion object {
		private const val ROOT = "dev.adriangrzebyk.trader"
		private const val ADAPTER = ".adapter."

		/** Concept packages whose domain must stay framework-free. `web`, the top-level Spring config, is exempt. */
		private val CONCEPTS = listOf("game", "login", "board")

		private val FRAMEWORKS = listOf("org.springframework", "software.amazon", "com.fasterxml", "jakarta")
	}
}
