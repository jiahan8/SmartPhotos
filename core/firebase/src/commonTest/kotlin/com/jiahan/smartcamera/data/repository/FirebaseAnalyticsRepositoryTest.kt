package com.jiahan.smartcamera.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [FirebaseAnalyticsRepository]'s event and parameter names, over a recording [AnalyticsSink].
 *
 * Those names are a wire contract -- the Analytics console groups and reports by them -- and nothing
 * else in the build would notice one changing, so the expected values here are literals rather than
 * the repository's constants.
 *
 * `logText` is deliberately not pinned: what it sends is a known open question, and a test would lock
 * today's payload in as intended behaviour.
 */
class FirebaseAnalyticsRepositoryTest {

    private class RecordingAnalyticsSink : AnalyticsSink {
        val userIds = mutableListOf<String?>()
        val events = mutableListOf<Pair<String, Map<String, String>>>()

        override fun setUserId(userId: String?) {
            userIds += userId
        }

        override fun logEvent(name: String, parameters: Map<String, String>) {
            events += name to parameters
        }
    }

    private val sink = RecordingAnalyticsSink()
    private val repository = FirebaseAnalyticsRepository(sink)

    @Test
    fun `setUserId passes the id through including the null a sign-out sends`() {
        repository.setUserId("uid-1")
        repository.setUserId(null)

        assertEquals(listOf("uid-1", null), sink.userIds)
    }

    /** Firebase's recommended search event, which the console reports as a search. */
    @Test
    fun `logSearch uses the recommended search event and parameter`() {
        repository.logSearch("cats")

        assertEquals(listOf("search" to mapOf("search_term" to "cats")), sink.events)
    }

    @Test
    fun `each screen's search has its own event with the search term`() {
        repository.logNoteSearch("groceries")
        repository.logFavoriteSearch("errands")
        repository.logExploreSearch("mountains")

        assertEquals(
            listOf(
                "note_search" to mapOf("search_term" to "groceries"),
                "favorite_search" to mapOf("search_term" to "errands"),
                "explore_search" to mapOf("search_term" to "mountains"),
            ),
            sink.events,
        )
    }

    @Test
    fun `note create and edit carry the note text`() {
        repository.logNoteCreate("first draft")
        repository.logNoteEdit("second draft")

        assertEquals(
            listOf(
                "note_create" to mapOf("note_text" to "first draft"),
                "note_edit" to mapOf("note_text" to "second draft"),
            ),
            sink.events,
        )
    }

    @Test
    fun `display name and username are logged under their own names`() {
        repository.logDisplayName("Ansel")
        repository.logUsername("ansel_a")

        assertEquals(
            listOf(
                "display_name" to mapOf("display_name" to "Ansel"),
                "username" to mapOf("username" to "ansel_a"),
            ),
            sink.events,
        )
    }
}