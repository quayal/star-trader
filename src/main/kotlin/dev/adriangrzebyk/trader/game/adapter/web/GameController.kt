package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.GameCreationService
import dev.adriangrzebyk.trader.game.GameIdGenerator
import dev.adriangrzebyk.trader.game.GameJoiningService
import dev.adriangrzebyk.trader.game.GameNotFoundException
import dev.adriangrzebyk.trader.game.GamePollingService
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.NotAGameMemberException
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class GameController(idGenerator: GameIdGenerator, gameStore: GameStore, private val sessionStore: SessionStore,) {

	private val gameCreationService = GameCreationService(idGenerator, gameStore)
	private val gameJoiningService = GameJoiningService(gameStore)
	private val gamePollingService = GamePollingService(gameStore)

	@PostMapping("/games")
	@ResponseStatus(HttpStatus.CREATED)
	fun createGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@RequestBody(required = false) request: CreateGameRequestDto?,
	): GameResponseDto {
		val session = sessionFor(authorization)
		val creator = Player.newcomer(session.email, session.playerName, session.corporationName)
		val game = try {
			gameCreationService.createGame(creator, request?.wholeTurns())
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		}

		return game.toResponseDto()
	}

	@PostMapping("/games/{id}/join")
	fun joinGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
	): GameResponseDto {
		val session = sessionFor(authorization)
		val player = Player.newcomer(session.email, session.playerName, session.corporationName)
		val game = try {
			gameJoiningService.joinGame(id, player)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return game.toResponseDto()
	}

	@GetMapping("/games/{id}")
	fun pollGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			gamePollingService.poll(id, session.email)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		}

		return game.toResponseDto()
	}

	private fun sessionFor(authorization: String?): Session {
		val token = authorization?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
			?.substring(BEARER_PREFIX.length)?.trim()
		return token?.takeIf { it.isNotEmpty() }?.let { sessionStore.find(it) }
			?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
