package com.whaleup.gameshub.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BiomeStateTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { mockContext.applicationContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        
        BiomeState.init(mockContext)
        BiomeState.reset()
    }

    @Test
    fun testSetUserProfileWithFtueCompleted() {
        val profileData = mapOf(
            "userId" to "johndoe",
            "userName" to "John Doe",
            "avatarUrl" to "https://avatar.url",
            "ftueCompleted" to true
        )

        BiomeState.setUserProfile(profileData, "server")

        val profile = BiomeState.getUserProfile()
        assertNotNull(profile)
        assertEquals("johndoe", profile?.basic?.userId)
        assertEquals("John Doe", profile?.basic?.userName)
        assertEquals("https://avatar.url", profile?.basic?.avatarUrl)
        assertEquals(true, profile?.basic?.ftueCompleted)
    }

    @Test
    fun testGetProfileAsMapIncludesFtueCompleted() {
        val profileData = mapOf(
            "userId" to "johndoe",
            "ftueCompleted" to true
        )

        BiomeState.setUserProfile(profileData, "server")
        val map = BiomeState.getProfileAsMap()

        val basic = map["basic"] as Map<*, *>
        assertEquals(true, basic["ftueCompleted"])
    }

    @Test
    fun testPersistenceAndHydration() {
        val profileData = mapOf(
            "userId" to "johndoe",
            "ftueCompleted" to true
        )

        // Capture the JSON string passed to SharedPreferences
        var capturedJson: String? = null
        every { mockEditor.putString(any(), any()) } answers {
            capturedJson = secondArg()
            mockEditor
        }

        BiomeState.setUserProfile(profileData, "server")
        
        assertNotNull(capturedJson)
        assertTrue(capturedJson!!.contains("\"ftueCompleted\":true"))

        // Reset state and hydrate from the captured JSON
        BiomeState.reset()
        every { mockPrefs.getString(any(), any()) } returns capturedJson
        
        BiomeState.hydrateUserProfile("johndoe")
        
        val hydratedProfile = BiomeState.getUserProfile()
        assertNotNull(hydratedProfile)
        assertEquals("johndoe", hydratedProfile?.basic?.userId)
        assertEquals(true, hydratedProfile?.basic?.ftueCompleted)
    }
}
