package com.app.picflick.repository

import com.picflick.app.data.Flick
import com.picflick.app.data.UserProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for critical production fixes:
 * 1. Flick stores Storage paths for cleanup on delete
 * 2. schemaVersion is present on data classes
 * 3. UserProfile schemaVersion is present
 */
class FlickRepositoryCriticalFixesTest {

    @Test
    fun `Flick data class includes schemaVersion and storagePaths`() {
        val flick = Flick(
            id = "test-id",
            userId = "user-1",
            imageUrl = "https://example.com/image.jpg",
            storagePath = "photos/user-1/abc.jpg",
            thumbnailPath512 = "photos/user-1/thumbnails_512/abc.jpg",
            thumbnailPath1080 = "photos/user-1/thumbnails_1080/abc.jpg",
            schemaVersion = 2
        )

        assertEquals("photos/user-1/abc.jpg", flick.storagePath)
        assertEquals("photos/user-1/thumbnails_512/abc.jpg", flick.thumbnailPath512)
        assertEquals("photos/user-1/thumbnails_1080/abc.jpg", flick.thumbnailPath1080)
        assertEquals(2, flick.schemaVersion)
    }

    @Test
    fun `UserProfile data class includes schemaVersion`() {
        val profile = UserProfile(
            uid = "user-1",
            displayName = "Test User",
            schemaVersion = 2
        )

        assertEquals(2, profile.schemaVersion)
    }

    @Test
    fun `Flick defaults schemaVersion to 2 for backward compatibility`() {
        val flick = Flick()
        assertEquals(2, flick.schemaVersion)
    }

    @Test
    fun `UserProfile defaults schemaVersion to 2 for backward compatibility`() {
        val profile = UserProfile()
        assertEquals(2, profile.schemaVersion)
    }
}
