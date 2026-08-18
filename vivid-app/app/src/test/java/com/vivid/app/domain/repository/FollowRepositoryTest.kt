package com.vivid.app.domain.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [FollowActionResult] enum and [FollowRelationshipState]
 * data class behaviour. Actual Firestore interactions are tested through
 * integration tests.
 */
class FollowRepositoryTest {

    // ── FollowRelationshipState defaults ──

    @Test
    fun `default FollowRelationshipState has no flags set`() {
        val state = FollowRelationshipState()
        assertFalse(state.isFollowing)
        assertFalse(state.hasPendingRequest)
        assertFalse(state.isTargetPrivate)
        assertFalse(state.isBlocked)
    }

    @Test
    fun `FollowRelationshipState with blocked flag`() {
        val state = FollowRelationshipState(isBlocked = true)
        assertTrue(state.isBlocked)
        assertFalse(state.isFollowing)
    }

    // ── FollowActionResult ──

    @Test
    fun `FollowActionResult has four values`() {
        val values = FollowActionResult.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(FollowActionResult.FOLLOWED))
        assertTrue(values.contains(FollowActionResult.UNFOLLOWED))
        assertTrue(values.contains(FollowActionResult.REQUESTED))
        assertTrue(values.contains(FollowActionResult.REQUEST_CANCELLED))
    }

    // ── SocialUserPreview ──

    @Test
    fun `SocialUserPreview default avatar fields are empty`() {
        val preview = SocialUserPreview(
            uid = "uid1",
            username = "alice",
            displayName = "Alice"
        )
        assertEquals("", preview.avatarUrl)
        assertEquals("", preview.avatarBase64)
    }

    @Test
    fun `SocialUserPreview equality`() {
        val a = SocialUserPreview("1", "u", "U", "url", "b64")
        val b = SocialUserPreview("1", "u", "U", "url", "b64")
        assertEquals(a, b)
    }
}
