package dev.salavatov.multifs.cloud.googledrive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GDriveNativeNodeData

@Serializable
data class GDriveNativeFolderData(
    val id: String,
    val name: String,
    @SerialName("parents") val parentIds: List<String>,
) : GDriveNativeNodeData()

@Serializable
data class GDriveNativeFileData(
    val id: String,
    val name: String,
    val mimeType: String,
    @SerialName("parents") val parentIds: List<String>,
    val md5Checksum: String? = null,
    val size: Long = 0
) : GDriveNativeNodeData()
