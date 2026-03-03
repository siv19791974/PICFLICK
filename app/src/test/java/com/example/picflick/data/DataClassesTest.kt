package com.example.picflick.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for data classes
 */
class DataClassesTest {

    @Test
    fun `UserProfile should have correct default values`() {
        // When
        val profile = UserProfile()

        // Then
        assertEquals("", profile.uid)
        assertEquals("", profile.email)
        assertEquals("", profile.displayName)
        assertEquals("", profile.photoUrl)
        assertEquals("", profile.bio)
        assertTrue(profile.followers.isEmpty())
        assertTrue(profile.following.isEmpty())
    }

    @Test
    fun `UserProfile should accept all parameters`() {
        // Given
        val followers = listOf("user1", "user2")
        val following = listOf("user3")

        // When
        val profile = UserProfile(
            uid = "test_uid",
            email = "test@example.com",
            displayName = "Test User",
            photoUrl = "https://example.com/photo.jpg",
            bio = "Hello world",
            followers = followers,
            following = following
        )

        // Then
        assertEquals("test_uid", profile.uid)
        assertEquals("test@example.com", profile.email)
        assertEquals("Test User", profile.displayName)
        assertEquals("https://example.com/photo.jpg", profile.photoUrl)
        assertEquals("Hello world", profile.bio)
        assertEquals(followers, profile.followers)
        assertEquals(following, profile.following)
    }

    @Test
    fun `Flick should have correct default values`() {
        // When
        val flick = Flick()

        // Then
        assertEquals("", flick.id)
        assertEquals("", flick.userId)
        assertEquals("", flick.userName)
        assertEquals("", flick.imageUrl)
        assertEquals("", flick.description)
        assertEquals(0, flick.timestamp)
        assertTrue(flick.likes.isEmpty())
    }

    @Test
    fun `Flick should accept all parameters`() {
        // Given
        val likes = listOf("user1", "user2", "user3")

        // When
        val flick = Flick(
            id = "flick_123",
            userId = "user_456",
            userName = "John Doe",
            imageUrl = "https://example.com/image.jpg",
            description = "Beautiful sunset",
            timestamp = 1234567890L,
            likes = likes
        )

        // Then
        assertEquals("flick_123", flick.id)
        assertEquals("user_456", flick.userId)
        assertEquals("John Doe", flick.userName)
        assertEquals("https://example.com/image.jpg", flick.imageUrl)
        assertEquals("Beautiful sunset", flick.description)
        assertEquals(1234567890L, flick.timestamp)
        assertEquals(likes, flick.likes)
    }

    @Test
    fun `ChatSession should have correct default values`() {
        // When
        val session = ChatSession()

        // Then
        assertEquals("", session.id)
        assertTrue(session.participants.isEmpty())
        assertEquals("", session.lastMessage)
        assertEquals(0, session.lastTimestamp)
    }

    @Test
    fun `ChatSession should accept all parameters`() {
        // Given
        val participants = listOf("user1", "user2")

        // When
        val session = ChatSession(
            id = "chat_123",
            participants = participants,
            lastMessage = "Hello!",
            lastTimestamp = 1234567890L
        )

        // Then
        assertEquals("chat_123", session.id)
        assertEquals(participants, session.participants)
        assertEquals("Hello!", session.lastMessage)
        assertEquals(1234567890L, session.lastTimestamp)
    }

    @Test
    fun `ChatMessage should have correct default values`() {
        // When
        val message = ChatMessage()

        // Then
        assertEquals("", message.id)
        assertEquals("", message.senderId)
        assertEquals("", message.text)
        assertEquals(0, message.timestamp)
    }

    @Test
    fun `ChatMessage should accept all parameters`() {
        // When
        val message = ChatMessage(
            id = "msg_123",
            senderId = "user_456",
            text = "Hello, how are you?",
            timestamp = 1234567890L
        )

        // Then
        assertEquals("msg_123", message.id)
        assertEquals("user_456", message.senderId)
        assertEquals("Hello, how are you?", message.text)
        assertEquals(1234567890L, message.timestamp)
    }

    @Test
    fun `data classes should support equality`() {
        // Given
        val profile1 = UserProfile(uid = "user1", displayName = "User One")
        val profile2 = UserProfile(uid = "user1", displayName = "User One")
        val profile3 = UserProfile(uid = "user2", displayName = "User Two")

        // Then
        assertEquals(profile1, profile2)
        assertNotEquals(profile1, profile3)
    }

    @Test
    fun `data classes should support copy`() {
        // Given
        val original = UserProfile(uid = "user1", displayName = "Original")

        // When
        val copy = original.copy(displayName = "Copy")

        // Then
        assertEquals("user1", copy.uid) // Same uid
        assertEquals("Copy", copy.displayName) // Changed name
        assertEquals("Original", original.displayName) // Original unchanged
    }
}
