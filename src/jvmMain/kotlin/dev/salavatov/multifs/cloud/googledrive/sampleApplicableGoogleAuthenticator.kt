package dev.salavatov.multifs.cloud.googledrive

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.util.network.*
import kotlinx.coroutines.CompletableDeferred
import java.awt.Desktop
import java.net.URI
import java.net.URL
import java.util.*

class CallbackGoogleAuthenticator(private val appCredentials: GoogleAppCredentials) : GoogleAuthenticator {
    override suspend fun authenticate(): GoogleAuthTokens {
        val codeFuture = CompletableDeferred<String>()
        val callbackServer = embeddedServer(CIO, 0) {
            routing {
                get("/") {
                    val code = context.request.queryParameters["code"] ?: error("unexpected callback result: params: ${context.request.queryParameters}") // TODO: extract error
                    codeFuture.complete(code)
                }
            }
        }.start(wait = false)

        openInBrowser(callbackServer.makeOAuthURI())

        val code = codeFuture.await()
        callbackServer.stop(10, 10)

        return exchangeAuthCodeOnTokens(code)
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

    private suspend fun ApplicationEngine.makeOAuthURI() = URL(
        "https://accounts.google.com/o/oauth2/v2/auth?" + listOf(
            "client_id=${appCredentials.clientId}",
            "redirect_uri=http://0.0.0.0:${networkAddresses()[0].port}/",
            "response_type=code",
            "scope=https://www.googleapis.com/auth/drive",
            "access_type=offline",
            // "state=${state}"
        ).joinToString("&")
    ).toURI()

    private suspend fun exchangeAuthCodeOnTokens(code: String): GoogleAuthTokens {
        return TODO()
    }

    override suspend fun refresh(expired: GoogleAuthTokens): GoogleAuthTokens {
        TODO("Not yet implemented")
    }
}

actual fun sampleApplicableGoogleAuthenticator(appCredentials: GoogleAppCredentials): GoogleAuthenticator {
    TODO("Not yet implemented")
}