package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import kotlinx.html.*
import kotlinx.serialization.*

val applicationHttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

fun main(){
    embeddedServer(factory = Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(httpClient: HttpClient = applicationHttpClient) {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }
    val redirects = mutableMapOf<String, String>()
    install(Authentication) {
        oauth("auth-oauth-google") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Get,
                    clientId = "27867488691-l2qf4snrp3avvrrdupmtr22bvfachmhs.apps.googleusercontent.com",
                    clientSecret = "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",
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile"),
                    extraAuthParameters = listOf("access_type" to "offline"),
                    onStateCreated = { call, state ->
                        redirects[state] = call.request.queryParameters["redirectUrl"]!!
                    }
                )
            }
            client = httpClient
        }
    }
    routing {
        authenticate("auth-oauth-google") {
            get("/login") {
                // Redirects to 'authorizeUrl' automatically
            }

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                call.sessions.set(UserSession(principal!!.state!!, principal.accessToken))
                val redirect = redirects[principal.state!!]
                call.respondRedirect(redirect!!)
            }
        }
        get("/") {
            call.respondHtml {
                body {
                    p {
                        a("/login?redirectUrl=0.0.0.0/callback") { +"Login with Google" }
                    }
                }
            }
        }
        get("/{path}") {
            val userSession: UserSession? = call.sessions.get()
            if (userSession != null) {
                val userInfo: UserInfo = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${userSession.token}")
                    }
                }.body()
                call.respondText("Hello, ${userInfo.name}!")
            } else {
                val redirectUrl = URLBuilder("http://0.0.0.0:8080/login").run {
                    parameters.append("redirectUrl", call.request.uri)
                    build()
                }
                call.respondRedirect(redirectUrl)
            }
        }
    }
}

data class UserSession(val state: String, val token: String)
@Serializable
data class UserInfo(
    val id: String,
    val name: String,
    @SerialName("given_name") val givenName: String,
    @SerialName("family_name") val familyName: String,
    val picture: String,
    val locale: String
)