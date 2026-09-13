package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** One of the two signup pre-check callables, by name: the seam for [DefaultAuthRepository]. */
internal interface AuthCallable {
    suspend fun call(name: String, args: AuthCheckArgs): AuthCheckResult?
}

internal class GitLiveAuthCallable(
    private val functions: FirebaseFunctions,
) : AuthCallable {
    override suspend fun call(name: String, args: AuthCheckArgs): AuthCheckResult? =
        functions.httpsCallable(name)
            // encodeDefaults off, so each check sends only the key it sets -- `{username}` or
            // `{email}` -- as the Android SDK's hashMapOf did.
            .invoke(AuthCheckArgs.serializer(), args) { encodeDefaults = false }
            .data(AuthCheckResult.serializer().nullable)
}

@Serializable
internal class AuthCheckArgs(
    val username: String? = null,
    val email: String? = null,
)

/**
 * Both checks' replies. A missing, null or malformed flag reads as `false`: signup is gated on these,
 * so an absent or reshaped backend has to read as "taken"/"not registered" rather than let a user
 * through to a profile creation that then rejects them.
 */
@Serializable
internal class AuthCheckResult(
    @Serializable(with = LenientBooleanSerializer::class) val available: Boolean = false,
    @Serializable(with = LenientBooleanSerializer::class) val registered: Boolean = false,
)

/** A flag that reads as `false`, rather than failing the call, when the value is not a boolean. */
internal object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean =
        runCatching { decoder.decodeBoolean() }.getOrDefault(false)

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}