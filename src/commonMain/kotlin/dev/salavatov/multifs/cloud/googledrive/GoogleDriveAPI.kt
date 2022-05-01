package dev.salavatov.multifs.cloud.googledrive

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

open class GoogleDriveAPIException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

open class GoogleDriveAPI(
    protected val authorizer: GoogleAuthorizationRequester
) {
    protected val apiClient = HttpClient {
        expectSuccess = false
        install(Auth) {
            lateinit var tokenInfo: GoogleAuthTokens

            bearer {
                loadTokens {
                    tokenInfo = authorizer.requestAuthorization()
                    BearerTokens(
                        accessToken = tokenInfo.accessToken, refreshToken = tokenInfo.refreshToken ?: ""
                    )
                }

                refreshTokens {
                    val refreshTokenInfo = authorizer.refreshAuthorization(tokenInfo)
                    tokenInfo = GoogleAuthTokens(
                        refreshTokenInfo.accessToken,
                        refreshTokenInfo.refreshToken ?: tokenInfo.refreshToken
                    )
                    BearerTokens(
                        accessToken = tokenInfo.accessToken, refreshToken = tokenInfo.refreshToken ?: ""
                    )
                }
            }
        }
    }
    protected val jsonParser = Json { ignoreUnknownKeys = true }
    protected val defaultFieldsQuery = "id,name,size,mimeType,parents,md5Checksum"

    suspend fun list(folderId: String): List<GDriveNativeNodeData> {
        val endpoint = "https://www.googleapis.com/drive/v3/files"
        val q = "'$folderId' in parents"
        val pageSize = 1000 // max permitted value
        var pageToken: String? = null

        val result = mutableListOf<GDriveNativeNodeData>()
        while (true) {
            val response = apiClient.get(endpoint) {
                parameter("q", q)
                parameter("fields", "files($defaultFieldsQuery)")
                parameter("pageSize", pageSize)
                if (pageToken != null) parameter("pageToken", pageToken)
            }
            if (response.status.value != 200) throw GoogleDriveAPIException("failed list request: ${response.status}")

            val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // documentation states that 'kind' is present in response, but in fact it isn't...
            // val kind = data["kind"]?.jsonPrimitive?.content
            // if (kind != "drive#fileList") throw GoogleDriveAPIException("unexpected 'kind' field value: $kind")

            val nextPageToken = data["nextPageToken"]?.jsonPrimitive

            val entries =
                data["files"]?.jsonArray ?: throw GoogleDriveAPIException("list response doesn't contain 'files' field")

            for (entry in entries.map { it.jsonObject }) {
                result += parseNode(entry)
            }

            pageToken = (nextPageToken ?: break).toString()
        }
        return result
    }

    private fun parseNode(
        entry: JsonObject,
    ): GDriveNativeNodeData {
        val mimeType = entry["mimeType"]?.jsonPrimitive?.content
            ?: throw GoogleDriveAPIException("response file object doesn't contain 'mimeType' field")
        return try {
            if (mimeType == FOLDER_MIMETYPE) {
                jsonParser.decodeFromJsonElement<GDriveNativeFolderData>(entry)
            } else {
                jsonParser.decodeFromJsonElement<GDriveNativeFileData>(entry)
            }
        } catch (e: Throwable) {
            throw GoogleDriveAPIException("couldn't parse node from json: $e", e)
        }
    }

    suspend fun createFolder(name: String, parentId: String): GDriveNativeFolderData {
        val endpoint = "https://www.googleapis.com/drive/v3/files"
        val response = apiClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            parameter("uploadType", "multipart")
            parameter("fields", defaultFieldsQuery)
            @Serializable
            data class Req(val mimeType: String, val name: String, val parents: List<String>)
            setBody(Json.encodeToString(Req(FOLDER_MIMETYPE, name, listOf(parentId))))
        }
        if (response.status.value != 200) {
            // TODO: handle it more accurately ?
            throw GoogleDriveAPIException("failed to create a folder ${name} with parent ${parentId}: ${response.status}")
        }
        val entryRaw = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return parseNode(entryRaw) as GDriveNativeFolderData
    }

    suspend fun createFile(
        name: String,
        parentId: String,
        mimeType: ContentType? = null
    ): GDriveNativeFileData {
        val endpoint = "https://www.googleapis.com/drive/v3/files"
        val response = apiClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            parameter("uploadType", "multipart")
            parameter("fields", defaultFieldsQuery)
            @Serializable
            data class Req(val mimeType: String? = null, val name: String, val parents: List<String>)
            setBody(Json { encodeDefaults = false }.encodeToString(Req(mimeType?.toString(), name, listOf(parentId))))
        }
        if (response.status.value != 200) {
            // TODO: handle it more accurately ?
            throw GoogleDriveAPIException("failed to create a file ${name} with parent ${parentId}: ${response.status}")
        }
        val entryRaw = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return parseNode(entryRaw) as GDriveNativeFileData
    }

    suspend fun download(id: String): ByteArray {
        val endpoint = "https://www.googleapis.com/drive/v3/files/$id"
        val response = apiClient.get(endpoint) {
            parameter("alt", "media")
        }
        if (response.status.value != 200) {
            throw GoogleDriveAPIException("failed to download file $id: ${response.status}")
        }
        return response.body()
    }

    suspend fun upload(id: String, data: ByteArray) {
        val endpoint = "https://www.googleapis.com/upload/drive/v3/files/$id"
        val response = apiClient.patch(endpoint) {
            parameter("uploadType", "media")
            contentType(ContentType.Application.OctetStream)
            setBody(data)
        }
        if (response.status.value != 200) {
            throw GoogleDriveAPIException("failed to upload file $id: ${response.status}")
        }
    }

    suspend fun delete(id: String) {
        val endpoint = "https://www.googleapis.com/drive/v3/files/$id"
        val response = apiClient.delete(endpoint)
        if (response.status.value != 204 && response.status.value != 200) {
            throw GoogleDriveAPIException("failed to delete file $id: ${response.status}")
        }
    }

    suspend fun copyFile(id: String, newParentId: String, newName: String): GDriveNativeFileData {
        val endpoint = "https://www.googleapis.com/drive/v3/files/$id/copy"
        val response = apiClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            parameter("fields", defaultFieldsQuery)
            @Serializable
            data class Req(val name: String, val parents: List<String>)
            setBody(Json { encodeDefaults = false }.encodeToString(Req(newName, listOf(newParentId))))
        }
        if (response.status.value != 200) {
            // TODO: handle it more accurately ?
            throw GoogleDriveAPIException("failed to copy file ${id} to ${newParentId}: ${response.status}")
        }
        val entryRaw = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return parseNode(entryRaw) as GDriveNativeFileData
    }

    suspend fun moveFile(id: String, oldParentId: String, newParentId: String, newName: String): GDriveNativeFileData {
        val endpoint = "https://www.googleapis.com/drive/v3/files/$id"
        val response = apiClient.patch(endpoint) {
            contentType(ContentType.Application.Json)
            parameter("uploadType", "multipart")
            parameter("fields", defaultFieldsQuery)
            parameter("addParents", newParentId)
            parameter("removeParents", oldParentId)
            @Serializable
            data class Req(val name: String)
            setBody(Json { encodeDefaults = false }.encodeToString(Req(newName)))
        }
        if (response.status.value != 200) {
            // TODO: handle it more accurately ?
            throw GoogleDriveAPIException("failed to move a file $newName from $oldParentId to $newParentId: ${response.status}")
        }
        val entryRaw = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return parseNode(entryRaw) as GDriveNativeFileData
    }

    protected val FOLDER_MIMETYPE = "application/vnd.google-apps.folder"

    companion object {
        enum class DriveScope(val value: String) {
            General("https://www.googleapis.com/auth/drive"),
            AppSpecific("https://www.googleapis.com/auth/drive.appdata")
        }
    }
}