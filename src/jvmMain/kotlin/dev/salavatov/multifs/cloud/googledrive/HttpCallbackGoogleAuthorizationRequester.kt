package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.cloud.googledrive.GoogleAuthorizationRequester.Companion.exchangeAuthCodeOnTokens
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import java.awt.Desktop
import java.net.URI
import java.net.URL
import java.util.*

const val DEFAULT_SUCCESS_PAGE_HTML = """
<html>
<body>
    <h2 style="text-align:center">
    Success!<br>You may now close this page and return to the application.
    </h2>
</body>
</html>"""

class HttpCallbackGoogleAuthorizationRequester(
    private val appCredentials: GoogleAppCredentials,
    val scope: GoogleDriveAPI.Companion.DriveScope,
    val successPageHtml: String = DEFAULT_SUCCESS_PAGE_HTML
) : GoogleAuthorizationRequester {
    override suspend fun requestAuthorization(): GoogleAuthTokens {
        val tokensFuture = CompletableDeferred<GoogleAuthTokens>()

        lateinit var baseRedirectUri: String
        val callbackServer = embeddedServer(CIO, 0) {
            routing {
                get("/") {
                    val code = context.request.queryParameters["code"]
                        ?: throw GoogleDriveAPIException("authenticator: unexpected callback result: params: ${context.request.queryParameters}")
                    tokensFuture.complete(exchangeAuthCodeOnTokens(appCredentials, baseRedirectUri, code))
                    call.respondText(successPageHtml, ContentType.Text.Html)
                }
            }
        }.start(wait = false)
        baseRedirectUri = "http://127.0.0.1:${callbackServer.resolvedConnectors()[0].port}"

        openInBrowser(
            makeOAuthURI(redirectUri = baseRedirectUri)
        )

        val tokens = tokensFuture.await()
        callbackServer.stop(100, 200)
        return tokens
    }

    override suspend fun refreshAuthorization(expired: GoogleAuthTokens): GoogleAuthTokens =
        GoogleAuthorizationRequester.tryRefreshAuthorization(appCredentials, expired) { requestAuthorization() }

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
            "scope=${scope.value}",
            "access_type=offline",
            // "state=${state}"
        ).joinToString("&")
    ).toURI()
}