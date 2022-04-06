package dev.salavatov.multifs.cloud.googledrive

import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.network.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.net.URL
import java.util.*

class HttpCallbackGoogleAuthorizationRequester(private val appCredentials: GoogleAppCredentials) : GoogleAuthorizationRequester {
    override suspend fun requestAuthorization(): GoogleAuthTokens {
        val tokensFuture = CompletableDeferred<GoogleAuthTokens>()

        lateinit var baseRedirectUri: String
        val callbackServer = embeddedServer(CIO, 0) {
            routing {
                get("/") {
                    val code = context.request.queryParameters["code"]
                        ?: throw GoogleDriveAPIException("authenticator: unexpected callback result: params: ${context.request.queryParameters}")
                    tokensFuture.complete(exchangeAuthCodeOnTokens(baseRedirectUri, code))
                    call.respondText(
                        """
                        <html>
                        <body>
                            <h2 style="text-align:center">
                            Success!<br>You may now close this page and return to the application.
                            </h2>
                        </body>
                        </html>""".trimIndent(), ContentType.Text.Html
                    )
                }
            }
        }.start(wait = false)
        baseRedirectUri = "http://127.0.0.1:${callbackServer.networkAddresses()[0].port}"

        openInBrowser(
            makeOAuthURI(redirectUri = baseRedirectUri)
        )

        val tokens = tokensFuture.await()
        callbackServer.stop(100, 200)
        return tokens
    }

    // https://stackoverflow.com/a/68426773
    // TODO: there should be a better way to handle this
    private fun openInBrowser(uri: URI) {
        val osName by lazy(LazyThreadSafetyMode.NONE) { System.getProperty("os.name").lowercase(Locale.getDefault()) }
        val desktop = Desktop.getDesktop()
        when {
            Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(uri)
            "mac" in osName -> Runtime.getRuntime().exec("open $uri")
            "nix" in osName || "nux" in osName -> Runtime.getRuntime().exec("xdg-open $uri")
            else -> throw RuntimeException("cannot open $uri")
        }
    }

    private fun makeOAuthURI(redirectUri: String) = URL(
        "https://accounts.google.com/o/oauth2/v2/auth?" + listOf(
            "client_id=${appCredentials.clientId}",
            "redirect_uri=$redirectUri",
            "response_type=code",
            "scope=https://www.googleapis.com/auth/drive",
            "access_type=offline",
            // "state=${state}"
        ).joinToString("&")
    ).toURI()

    private suspend fun exchangeAuthCodeOnTokens(redirectUri: String, code: String): GoogleAuthTokens {
        val tokenClient = HttpClient() { expectSuccess = false }
        val response =
            tokenClient.submitForm(url = "https://oauth2.googleapis.com/token", formParameters = Parameters.build {
                append("code", code)
                append("client_id", appCredentials.clientId)
                append("client_secret", appCredentials.secret)
                append("grant_type", "authorization_code")
                append("redirect_uri", redirectUri)
            })
        if (response.status.value != 200) throw GoogleDriveAPIException("authenticator: failed to get tokens: ${response.status}")
        val rawData = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(rawData)
    }

    override suspend fun refreshAuthorization(expired: GoogleAuthTokens): GoogleAuthTokens {
        val tokenClient = HttpClient() { expectSuccess = false }
        val response =
            tokenClient.submitForm(url = "https://oauth2.googleapis.com/token", formParameters = Parameters.build {
                append("refresh_token", expired.refreshToken!!)
                append("client_id", appCredentials.clientId)
                append("client_secret", appCredentials.secret)
                append("grant_type", "refresh_token")
            })
        if (response.status.value == 400) { // TODO: && response.bodyAsText().contains("expired") doesn't work, can also contain "invalid_grant"
            return requestAuthorization()
        }
        if (response.status.value != 200) throw GoogleDriveAPIException("authenticator: failed to refresh tokens: ${response.status}")
        val rawData = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(rawData)
    }
}