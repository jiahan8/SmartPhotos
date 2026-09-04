package com.jiahan.smartcamera.note

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingShare(val text: String?, val uris: List<Uri>)

/**
 * Holds a share the OS handed `MainViewModel` until [NoteViewModel] can take it.
 *
 * The scope is load-bearing: `:app` posts and `:feature:note` consumes, so both have to resolve
 * the same instance. It sits on the class rather than in a `@Provides` in `:app`'s `AppModule`,
 * where it used to live beside an `@Inject constructor` that the explicit binding made dead --
 * one binding, declared in the module that owns the type, and `:app` no longer imports a
 * `:feature:note` type to hand-build one. **Ask where a binding is injected, not where it is
 * convenient to declare** (AGENTS.md, Dependency injection).
 *
 * A `StateFlow` plus an explicit [consume], not a `SharedFlow`: NoteViewModel is not collecting
 * when the share arrives, and a default `MutableSharedFlow` has no replay to catch it.
 */
@Singleton
class IncomingShareHandler @Inject constructor() {
    private val _incomingShare = MutableStateFlow<IncomingShare?>(null)
    val incomingShare = _incomingShare.asStateFlow()

    fun postShare(share: IncomingShare) {
        _incomingShare.value = share
    }

    fun consume(): IncomingShare? = _incomingShare.getAndUpdate { null }
}