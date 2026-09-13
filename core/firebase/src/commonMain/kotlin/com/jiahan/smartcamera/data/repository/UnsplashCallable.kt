package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** One Unsplash callable, by name: the seam between [DefaultPhotoRepository] and Firebase. */
internal interface UnsplashCallable {
    suspend fun call(name: String, args: UnsplashArgs): UnsplashPayload?
}

internal class GitLiveUnsplashCallable(
    private val functions: FirebaseFunctions,
) : UnsplashCallable {
    override suspend fun call(name: String, args: UnsplashArgs): UnsplashPayload? =
        functions.httpsCallable(name)
            // encodeDefaults off, so a list call sends no `query` key at all -- the wire shape the
            // Android SDK's hashMapOf sent, rather than an explicit null.
            .invoke(UnsplashArgs.serializer(), args) { encodeDefaults = false }
            .data(UnsplashPayload.serializer().nullable)
}

@Serializable
internal class UnsplashArgs(
    val query: String? = null,
    val page: Int,
    val perPage: Int,
)

@Serializable
internal class UnsplashPayload(
    val photos: List<UnsplashRow?> = emptyList(),
)

@Serializable
internal class UnsplashRow(
    val id: String? = null,
    val description: String? = null,
    @SerialName("alt_description") val altDescription: String? = null,
    val urls: UnsplashUrls? = null,
    @Serializable(with = LenientIntSerializer::class) val width: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val height: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val likes: Int = 0,
    val color: String? = null,
    val user: UnsplashUser? = null,
)

@Serializable
internal class UnsplashUrls(
    val regular: String? = null,
    val full: String? = null,
    val raw: String? = null,
    val small: String? = null,
    val thumb: String? = null,
)

@Serializable
internal class UnsplashUser(
    val name: String? = null,
    val username: String? = null,
    @SerialName("profile_image") val profileImage: UnsplashProfileImage? = null,
)

@Serializable
internal class UnsplashProfileImage(
    val small: String? = null,
)

/**
 * A count that arrives as an `Int` or a `Double` -- a callable payload is JSON -- and reads as 0
 * when it is absent or malformed, rather than failing the whole page over one field.
 */
internal object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int =
        runCatching { decoder.decodeDouble().toInt() }.getOrDefault(0)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}