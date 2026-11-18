package dev.adriangrzebyk.trader.web

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.ShallowEtagHeaderFilter

/**
 * Game polls answer "nothing changed" cheaply: every `GET /api/games/{id}` carries an ETag of its body, and a poll that
 * sends it back as `If-None-Match` gets an empty `304` while the caller's view is unchanged. A page polls every 2 s, so
 * an idle game costs a few hundred bytes a poll instead of the whole state (creator, 2026-11-18: a metered tunnel).
 * The ETag is of the caller's own view, so it never tells one player anything about another's. It's weak (`W/"…"`):
 * Tomcat won't compress a response with a strong ETag, and these are compressed (`server.compression`).
 */
@Configuration
class GamePollEtagConfiguration {

	@Bean
	fun gamePollEtagFilter(): FilterRegistrationBean<ShallowEtagHeaderFilter> {
		val filter = ShallowEtagHeaderFilter()
		filter.isWriteWeakETag = true
		val registration = FilterRegistrationBean(filter)
		registration.addUrlPatterns("/api/games/*")
		return registration
	}
}
