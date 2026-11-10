package com.nuvio.tv.core.profile

/**
 * Regla pura del PIN de la decisión 4: solo se pide el PIN del perfil principal
 * cuando ese perfil tiene PIN y se cambia un límite o se sale de un perfil limitado.
 */
fun requiresPrimaryPin(
    primaryHasPin: Boolean,
    changingAgeLimit: Boolean,
    leavingLimitedProfile: Boolean
): Boolean = primaryHasPin && (changingAgeLimit || leavingLimitedProfile)
