package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.GameNotFoundException
import dev.adriangrzebyk.trader.game.InvalidBidException
import dev.adriangrzebyk.trader.game.InvalidCargoMoveException
import dev.adriangrzebyk.trader.game.InvalidCollectException
import dev.adriangrzebyk.trader.game.InvalidFittingException
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.InvalidInvestmentException
import dev.adriangrzebyk.trader.game.InvalidJumpException
import dev.adriangrzebyk.trader.game.InvalidLandingException
import dev.adriangrzebyk.trader.game.InvalidOfferException
import dev.adriangrzebyk.trader.game.InvalidPlaceException
import dev.adriangrzebyk.trader.game.InvalidTradeException
import dev.adriangrzebyk.trader.game.NotAGameMemberException
import dev.adriangrzebyk.trader.game.NotTheCreatorException
import dev.adriangrzebyk.trader.game.NotYourTurnException
import dev.adriangrzebyk.trader.game.PhaseClosedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * The one place a refusal from the game becomes an HTTP status for [GameController] (BACKWARD_COMPATIBILITY.md §6), so
 * every endpoint answers the same way and a new one can't forget a case. The body is the refusal's own message, which
 * the screens show as is.
 *
 * - `404`: no such game.
 * - `403`: the caller isn't in the game, or isn't its creator. Neither says anything about the game (A05).
 * - `400`: what was sent can't be applied as it is, and the player can correct it (an amount, a price, a ship, a place).
 * - `409`: it can't be applied now: out of turn (R04), after the phase closed (A04), or in the wrong state of the game.
 */
@RestControllerAdvice(assignableTypes = [GameController::class])
class GameExceptionAdvice {

	@ExceptionHandler(GameNotFoundException::class)
	fun notFound(ex: RuntimeException): ResponseEntity<String> = answer(HttpStatus.NOT_FOUND, ex)

	@ExceptionHandler(NotAGameMemberException::class, NotTheCreatorException::class)
	fun forbidden(ex: RuntimeException): ResponseEntity<String> = answer(HttpStatus.FORBIDDEN, ex)

	@ExceptionHandler(
		InvalidBidException::class,
		InvalidCargoMoveException::class,
		InvalidCollectException::class,
		InvalidFittingException::class,
		InvalidInvestmentException::class,
		InvalidJumpException::class,
		InvalidLandingException::class,
		InvalidOfferException::class,
		InvalidPlaceException::class,
		InvalidTradeException::class
	)
	fun badRequest(ex: RuntimeException): ResponseEntity<String> = answer(HttpStatus.BAD_REQUEST, ex)

	@ExceptionHandler(InvalidGameOperationException::class, NotYourTurnException::class, PhaseClosedException::class)
	fun conflict(ex: RuntimeException): ResponseEntity<String> = answer(HttpStatus.CONFLICT, ex)

	private fun answer(status: HttpStatus, ex: RuntimeException): ResponseEntity<String> = ResponseEntity.status(status).body(ex.message)
}
