package com.example.sortmate.domain

import com.example.sortmate.domain.model.PresenceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceResolverTest {

    @Test
    fun `entering home marks home`() {
        assertEquals(
            PresenceStatus.HOME,
            PresenceResolver.resolve(
                current = PresenceStatus.AWAY,
                entered = setOf(GeofenceArea.HOME),
            ),
        )
    }

    @Test
    fun `entering campus marks campus`() {
        assertEquals(
            PresenceStatus.CAMPUS,
            PresenceResolver.resolve(
                current = PresenceStatus.AWAY,
                entered = setOf(GeofenceArea.CAMPUS),
            ),
        )
    }

    @Test
    fun `leaving home while away marks away`() {
        assertEquals(
            PresenceStatus.AWAY,
            PresenceResolver.resolve(
                current = PresenceStatus.HOME,
                exited = setOf(GeofenceArea.HOME),
            ),
        )
    }

    @Test
    fun `leaving campus while on campus marks away`() {
        assertEquals(
            PresenceStatus.AWAY,
            PresenceResolver.resolve(
                current = PresenceStatus.CAMPUS,
                exited = setOf(GeofenceArea.CAMPUS),
            ),
        )
    }

    @Test
    fun `leaving campus keeps home when already home`() {
        assertEquals(
            PresenceStatus.HOME,
            PresenceResolver.resolve(
                current = PresenceStatus.HOME,
                exited = setOf(GeofenceArea.CAMPUS),
            ),
        )
    }

    @Test
    fun `leaving home keeps campus when already on campus`() {
        assertEquals(
            PresenceStatus.CAMPUS,
            PresenceResolver.resolve(
                current = PresenceStatus.CAMPUS,
                exited = setOf(GeofenceArea.HOME),
            ),
        )
    }

    @Test
    fun `entering both areas prefers home`() {
        assertEquals(
            PresenceStatus.HOME,
            PresenceResolver.resolve(
                current = PresenceStatus.AWAY,
                entered = setOf(GeofenceArea.HOME, GeofenceArea.CAMPUS),
            ),
        )
    }

    @Test
    fun `no transitions keeps current status`() {
        assertEquals(
            PresenceStatus.CAMPUS,
            PresenceResolver.resolve(current = PresenceStatus.CAMPUS),
        )
    }
}
