package com.picflick.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Constants
 */
class ConstantsTest {

    @Test
    fun `pagination constants have correct values`() {
        assertEquals(120, Constants.Pagination.FLICKS_PER_PAGE)
        assertEquals(100, Constants.Pagination.EXPLORE_FLICKS_LIMIT)
        assertEquals(100, Constants.Pagination.USERS_PER_PAGE)
        assertEquals(20, Constants.Pagination.SUGGESTED_USERS_LIMIT)
        assertEquals(10, Constants.Pagination.MAX_FRIENDS_BATCH)
        assertEquals(50, Constants.Pagination.COMMENTS_PER_PAGE)
        assertEquals(50, Constants.Pagination.NOTIFICATIONS_PER_PAGE)
    }

    @Test
    fun `firebase collection names are correct`() {
        assertEquals("users", Constants.FirebaseCollections.USERS)
        assertEquals("flicks", Constants.FirebaseCollections.FLICKS)
        assertEquals("chatSessions", Constants.FirebaseCollections.CHAT_SESSIONS)
        assertEquals("notifications", Constants.FirebaseCollections.NOTIFICATIONS)
        assertEquals("messages", Constants.FirebaseCollections.MESSAGES)
        assertEquals("achievements", Constants.FirebaseCollections.ACHIEVEMENTS)
    }

    @Test
    fun `privacy constants are correct`() {
        assertEquals("friends", Constants.Privacy.PRIVACY_FRIENDS)
        assertEquals("public", Constants.Privacy.PRIVACY_PUBLIC)
    }

    @Test
    fun `time windows are correct`() {
        assertEquals(24 * 60 * 60 * 1000L, Constants.TimeWindows.ONE_DAY_MS)
        assertEquals(7 * 24 * 60 * 60 * 1000L, Constants.TimeWindows.ONE_WEEK_MS)
    }

    @Test
    fun `achievement milestones are correct`() {
        assertEquals(1, Constants.Achievements.FIRST_PHOTO_COUNT)
        assertEquals(5, Constants.Achievements.ACTIVE_USER_PHOTO_COUNT)
        assertEquals(50, Constants.Achievements.POWER_USER_PHOTO_COUNT)
        assertEquals(1000, Constants.Achievements.INFLUENCER_FOLLOWER_COUNT)
    }
}
