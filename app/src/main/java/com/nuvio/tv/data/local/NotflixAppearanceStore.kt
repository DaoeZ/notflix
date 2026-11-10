package com.nuvio.tv.data.local

import com.nuvio.tv.domain.model.AppAppearance
import com.nuvio.tv.domain.model.AppearanceContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import java.io.IOException

data class AppearanceRead(val requested: AppAppearance, val failed: Boolean = false, val reduceHomeMotion: Boolean = false,
    val introEnabled: Boolean = true, val introSoundEnabled: Boolean = true, val introOptionsValid: Boolean = true,
    val previewEnabled: Boolean = true, val previewSoundEnabled: Boolean = false, val previewOptionsValid: Boolean = true,
    val uiSoundsEnabled: Boolean = true, val uiSoundsOptionsValid: Boolean = true,
    val wikipediaEnabled: Boolean = true, val wikipediaOptionsValid: Boolean = true)

@Singleton
class NotflixAppearanceStore @Inject constructor(private val factory: ProfileDataStoreFactory) {
    companion object {
        const val FEATURE = "notflix_presentation_local"
        const val REDUCE_HOME_MOTION = "reduce_home_motion"
        const val INTRO_ENABLED = "intro_enabled"
        const val INTRO_SOUND_ENABLED = "intro_sound_enabled"
        const val PREVIEW_ENABLED = "preview_enabled"
        const val PREVIEW_SOUND_ENABLED = "preview_sound_enabled"
        const val UI_SOUNDS_ENABLED = "ui_sounds_enabled"
        const val WIKIPEDIA_ENABLED = "wikipedia_enabled"
    }

    val revision = factory.appearanceRevision

    fun contextFor(profileId: Int, confirmed: Boolean, knownIds: Set<Int>): AppearanceContext? =
        if (confirmed && profileId in knownIds) factory.appearanceContext(profileId) else null

    fun isCurrent(context: AppearanceContext): Boolean = factory.isAppearanceContextCurrent(context)

    fun observe(context: AppearanceContext): Flow<AppearanceRead> = kotlinx.coroutines.flow.flow {
        val store = factory.withAppearanceContext(context) { factory.get(context.profileId, FEATURE) }
        emitAll(store.data.map { prefs ->
            factory.withAppearanceContext(context) {
                val values = prefs.asMap().entries.associate { it.key.name to it.value }
                val schema = values["schema_version"]
                val introValid = (schema == null || schema == 1) &&
                    listOf(INTRO_ENABLED, INTRO_SOUND_ENABLED).all { values[it] == null || values[it] is Boolean }
                val previewValid = (schema == null || schema == 1) &&
                    listOf(PREVIEW_ENABLED, PREVIEW_SOUND_ENABLED).all { values[it] == null || values[it] is Boolean }
                val wikipediaValid = (schema == null || schema == 1) &&
                    (values[WIKIPEDIA_ENABLED] == null || values[WIKIPEDIA_ENABLED] is Boolean)
                // M44: a corrupt/unknown value fails safe to "on" and never poisons the other flags.
                val uiSoundsValid = (schema == null || schema == 1) &&
                    (values[UI_SOUNDS_ENABLED] == null || values[UI_SOUNDS_ENABLED] is Boolean)
                AppearanceRead(AppAppearance.decode(values["appearance"], schema),
                    reduceHomeMotion = (schema == null || schema == 1) && (values[REDUCE_HOME_MOTION] as? Boolean ?: false),
                    introEnabled = introValid && (values[INTRO_ENABLED] as? Boolean ?: true),
                    introSoundEnabled = introValid && (values[INTRO_SOUND_ENABLED] as? Boolean ?: true),
                    introOptionsValid = introValid,
                    previewEnabled = previewValid && (values[PREVIEW_ENABLED] as? Boolean ?: true),
                    previewSoundEnabled = previewValid && (values[PREVIEW_SOUND_ENABLED] as? Boolean ?: false),
                    previewOptionsValid = previewValid,
                    uiSoundsEnabled = values[UI_SOUNDS_ENABLED] as? Boolean ?: true,
                    uiSoundsOptionsValid = uiSoundsValid,
                    wikipediaEnabled = wikipediaValid && (values[WIKIPEDIA_ENABLED] as? Boolean ?: true),
                    wikipediaOptionsValid = wikipediaValid)
            }
        })
    }.catch { error ->
        if (error is CancellationException || error !is Exception) throw error
        emit(AppearanceRead(AppAppearance.NUVIO, failed = true, introEnabled = false, introSoundEnabled = false, introOptionsValid = false, previewEnabled = false, previewSoundEnabled = false, previewOptionsValid = false, uiSoundsEnabled = false, uiSoundsOptionsValid = false, wikipediaEnabled = false, wikipediaOptionsValid = false))
    }

    suspend fun select(context: AppearanceContext, appearance: AppAppearance): Result<Unit> = try {
        factory.withAppearanceContext(context) {
            factory.get(context.profileId, FEATURE).edit {
                it[stringPreferencesKey("appearance")] = appearance.storedValue
                it[intPreferencesKey("schema_version")] = 1
            }
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun selectHomeMotion(
        context: AppearanceContext,
        reduced: Boolean,
        authorized: () -> Boolean = { true }
    ): Result<Unit> = try {
        factory.withAppearanceContext(context) {
            factory.get(context.profileId, FEATURE).edit {
                // Recheck the captured session at the mutation, after any queue wait.
                if (!authorized()) throw IOException("Appearance session changed")
                it[booleanPreferencesKey(REDUCE_HOME_MOTION)] = reduced
            }
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun selectIntroEnabled(context: AppearanceContext, enabled: Boolean, authorized: () -> Boolean = { true }): Result<Unit> =
        selectIntroOption(context, INTRO_ENABLED, enabled, authorized)

    suspend fun selectIntroSoundEnabled(context: AppearanceContext, enabled: Boolean, authorized: () -> Boolean = { true }): Result<Unit> =
        selectIntroOption(context, INTRO_SOUND_ENABLED, enabled, authorized)

    suspend fun selectPreviewEnabled(context: AppearanceContext, enabled: Boolean, authorized: () -> Boolean = { true }): Result<Unit> =
        selectIntroOption(context, PREVIEW_ENABLED, enabled, authorized)

    suspend fun selectPreviewSoundEnabled(context: AppearanceContext, enabled: Boolean, authorized: () -> Boolean = { true }): Result<Unit> =
        selectIntroOption(context, PREVIEW_SOUND_ENABLED, enabled, authorized)

    suspend fun selectWikipediaEnabled(context: AppearanceContext, enabled: Boolean, authorized: () -> Boolean = { true }): Result<Unit> =
        selectIntroOption(context, WIKIPEDIA_ENABLED, enabled, authorized)

    suspend fun selectUiSoundsEnabled(context: AppearanceContext, enabled: Boolean, authorized: () -> Boolean = { true }): Result<Unit> =
        selectIntroOption(context, UI_SOUNDS_ENABLED, enabled, authorized)

    /** Bounded feature-cache writes share M1's retirement barrier, never a network request. */
    suspend fun withWikipediaWriteContext(context: AppearanceContext, authorized: () -> Boolean, write: () -> Unit) {
        factory.withAppearanceContext(context) {
            if (!authorized()) throw IOException("Wikipedia appearance session changed")
            write()
        }
    }

    private suspend fun selectIntroOption(context: AppearanceContext, key: String, value: Boolean, authorized: () -> Boolean): Result<Unit> = try {
        factory.withAppearanceContext(context) {
            factory.get(context.profileId, FEATURE).edit {
                if (!authorized()) throw IOException("Appearance session changed")
                it[booleanPreferencesKey(key)] = value
            }
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

}
