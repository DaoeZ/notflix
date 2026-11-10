package com.nuvio.tv.core.profile

import com.nuvio.tv.domain.model.MetaPreview
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** Puerta de edad apagada para pruebas que no ejercitan el filtro. */
internal fun disabledAgeGate(): ActiveProfileAgeGate = mockk(relaxed = true) {
    every { limit } returns MutableStateFlow<AgeLevel?>(null)
    every { ratings } returns MutableStateFlow<Map<String, AgeLevel?>>(emptyMap())
    every { snapshot } returns MutableStateFlow(AgeGateSnapshot(null, emptyMap()))
    every { currentLimit() } returns null
    every { knownRating(any<MetaPreview>()) } returns null
    every { knownRating(any<String>(), any()) } returns null
    every { prefetch(any()) } just Runs
    coEvery { filter(any()) } returns emptyList()
    coEvery { filterRow(any()) } coAnswers { firstArg() }
    coEvery { ratingFor(any(), any(), any()) } returns null
}
