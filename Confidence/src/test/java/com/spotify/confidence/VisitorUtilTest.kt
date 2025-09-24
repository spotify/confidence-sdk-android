package com.spotify.confidence

import android.content.Context
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class VisitorUtilTest {

    private val mockContext: Context = mock()
    private lateinit var sharedPreferences: InMemorySharedPreferences

    @Before
    fun setup() {
        sharedPreferences = InMemorySharedPreferences()
        whenever(mockContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
    }

    @Test
    fun getId_creates_new_visitor_id_when_none_exists() {
        val visitorId = VisitorUtil.getId(mockContext)

        Assert.assertNotNull(visitorId)
        Assert.assertNotEquals(DEFAULT_VALUE, visitorId)

        // Verify it's a valid UUID
        Assert.assertNotNull(UUID.fromString(visitorId))

        // Verify it was stored in SharedPreferences
        Assert.assertTrue(sharedPreferences.contains(VISITOR_ID_SHARED_PREFS_KEY))
        Assert.assertEquals(visitorId, sharedPreferences.getString(VISITOR_ID_SHARED_PREFS_KEY, null))
    }

    @Test
    fun getId_returns_existing_visitor_id_when_available() {
        val existingVisitorId = "existing-visitor-id"
        sharedPreferences.edit().putString(VISITOR_ID_SHARED_PREFS_KEY, existingVisitorId).apply()

        val visitorId = VisitorUtil.getId(mockContext)

        Assert.assertEquals(existingVisitorId, visitorId)
    }

    @Test
    fun resetId_generates_new_visitor_id() {
        // First set an existing visitor ID
        val existingVisitorId = "existing-visitor-id"
        sharedPreferences.edit().putString(VISITOR_ID_SHARED_PREFS_KEY, existingVisitorId).apply()

        val newVisitorId = VisitorUtil.resetId(mockContext)

        Assert.assertNotNull(newVisitorId)
        Assert.assertNotEquals(existingVisitorId, newVisitorId)

        // Verify it's a valid UUID
        Assert.assertNotNull(UUID.fromString(newVisitorId))

        // Verify it was stored in SharedPreferences
        Assert.assertEquals(newVisitorId, sharedPreferences.getString(VISITOR_ID_SHARED_PREFS_KEY, null))
    }

    @Test
    fun resetId_replaces_existing_visitor_id() {
        // Setup existing visitor ID
        val existingVisitorId = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(VISITOR_ID_SHARED_PREFS_KEY, existingVisitorId).apply()

        // Reset to new ID
        val newVisitorId = VisitorUtil.resetId(mockContext)

        Assert.assertNotEquals(existingVisitorId, newVisitorId)

        // Verify the new ID is now stored
        Assert.assertEquals(newVisitorId, sharedPreferences.getString(VISITOR_ID_SHARED_PREFS_KEY, null))

        // Verify subsequent calls to getId return the new ID
        Assert.assertEquals(newVisitorId, VisitorUtil.getId(mockContext))
    }

    @Test
    fun resetId_works_when_no_existing_visitor_id() {
        // Ensure no existing visitor ID
        Assert.assertFalse(sharedPreferences.contains(VISITOR_ID_SHARED_PREFS_KEY))

        val newVisitorId = VisitorUtil.resetId(mockContext)

        Assert.assertNotNull(newVisitorId)

        // Verify it's a valid UUID
        Assert.assertNotNull(UUID.fromString(newVisitorId))

        // Verify it was stored
        Assert.assertEquals(newVisitorId, sharedPreferences.getString(VISITOR_ID_SHARED_PREFS_KEY, null))
    }
}