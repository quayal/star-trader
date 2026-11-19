package dev.adriangrzebyk.trader.login.adapter.web

import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Gives an endpoint the caller's [Session] from its `Authorization: Bearer <token>` header ("bearer" in any case, the
 * token trimmed), so no endpoint parses the header itself. A missing, malformed or unknown token is `401`
 * (BACKWARD_COMPATIBILITY.md §3). Spring resolves arguments in order, so an endpoint declares its [Session] last: a body
 * it can't read is still `400` before the session is checked, as it was when each endpoint checked it.
 */
class SessionArgumentResolver(private val sessionStore: SessionStore) : HandlerMethodArgumentResolver {

	override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == Session::class.java

	override fun resolveArgument(
		parameter: MethodParameter,
		mavContainer: ModelAndViewContainer?,
		webRequest: NativeWebRequest,
		binderFactory: WebDataBinderFactory?
	): Session {
		val authorization = webRequest.getHeader(HttpHeaders.AUTHORIZATION)
		val token = authorization?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
			?.substring(BEARER_PREFIX.length)?.trim()
		return token?.takeIf { it.isNotEmpty() }?.let { sessionStore.find(it) }
			?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}

/** Registers [SessionArgumentResolver] with Spring MVC. */
@Configuration
class SessionArgumentResolverConfiguration(private val sessionStore: SessionStore) : WebMvcConfigurer {

	override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
		resolvers.add(SessionArgumentResolver(sessionStore))
	}
}
