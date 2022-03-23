package dev.salavatov.multifs.cloud.googledrive

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.json

/**
 * add this to the web app's head section
```html
<script src="https://apis.google.com/js/platform.js" async defer></script>
<script>
    function googlePlatformInit() {
        gapi.load('auth2', function() {
            gapi.auth2.init({
                "client_id": "YOUR CLIENT ID"
            })
        })
    }
</script>
```
 */
class PopupGoogleAuthorizationRequester(private val appCredentials: GoogleAppCredentials) : GoogleAuthorizationRequester {
    private val gapi = window.asDynamic().gapi
    private val auth = gapi.auth2.getAuthInstance()
    private var googleUser: dynamic = null

    override suspend fun requestAuthorization(): GoogleAuthTokens {
        googleUser = auth.signIn(
            json("scope" to "https://www.googleapis.com/auth/drive")
        ).unsafeCast<Promise<dynamic>>().await()
        val authData = googleUser.getAuthResponse(true)
        return GoogleAuthTokens(authData.access_token as String, authData.expires_in as Int, "")
    }

    override suspend fun refreshAuthorization(expired: GoogleAuthTokens): GoogleAuthTokens {
        if (googleUser == null) return requestAuthorization()
        val authData = googleUser.reloadAuthResponse().unsafeCast<Promise<dynamic>>().await()
        return GoogleAuthTokens(authData.access_token as String, authData.expires_in as Int, "")
    }
}