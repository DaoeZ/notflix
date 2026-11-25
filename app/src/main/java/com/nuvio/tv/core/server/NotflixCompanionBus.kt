package com.nuvio.tv.core.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CompanionCommand {
    data class Cast(
        val itemId: String,
        val itemType: String,
        val title: String?,
        val season: Int?,
        val episode: Int?,
        val positionMs: Long?,
        val autoPlay: Boolean
    ) : CompanionCommand

    data class Search(val query: String) : CompanionCommand

    data class RemoteKey(val action: String) : CompanionCommand
}

@Singleton
class NotflixCompanionBus @Inject constructor() {
    private val _commands = MutableSharedFlow<CompanionCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<CompanionCommand> = _commands.asSharedFlow()

    private val _pendingSearchQuery = MutableStateFlow<String?>(null)
    val pendingSearchQuery: StateFlow<String?> = _pendingSearchQuery.asStateFlow()

    @Volatile
    var activeUrl: String? = null

    @Volatile
    var activePairCode: String? = null

    fun emit(command: CompanionCommand): Boolean {
        if (command is CompanionCommand.Search) {
            _pendingSearchQuery.value = command.query
        }
        return _commands.tryEmit(command)
    }

    fun consumePendingSearchQuery(): String? {
        val current = _pendingSearchQuery.value
        if (current != null) _pendingSearchQuery.value = null
        return current
    }
}
