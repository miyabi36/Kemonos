package su.afk.kemonos.creatorProfile.data.similar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SimilarByTagsTest {

    private val self = CreatorKey("patreon", "self")

    @Test
    fun `sharing more tags ranks higher`() {
        val ranked = SimilarByTags.rank(
            self = self,
            creatorsByTag = mapOf(
                "onepiece" to listOf(self, a, b),
                "naruto" to listOf(self, a),
            ),
            limit = 10,
        )

        assertEquals(listOf(a, b), ranked.map { it.key })
        assertEquals(2, ranked.first().sharedTags)
    }

    @Test
    fun `a focused tag outweighs a crowded one`() {
        /** Под общим тегом ходит полсайта, под узким — единицы. */
        val crowd = (1..40).map { CreatorKey("patreon", "crowd-$it") }

        val ranked = SimilarByTags.rank(
            self = self,
            creatorsByTag = mapOf(
                "nsfw" to crowd + a,
                "obscure-series" to listOf(b, a),
            ),
            limit = 5,
        )

        /** a есть в обоих, b — только в узком, и это ценнее толпы. */
        assertEquals(a, ranked[0].key)
        assertEquals(b, ranked[1].key)
        assertTrue(ranked[1].score > ranked.first { it.key in crowd }.score)
    }

    @Test
    fun `the creator themselves is never similar to themselves`() {
        val ranked = SimilarByTags.rank(
            self = self,
            creatorsByTag = mapOf("onepiece" to listOf(self, a)),
            limit = 10,
        )

        assertEquals(listOf(a), ranked.map { it.key })
    }

    @Test
    fun `one creator appearing twice under a tag counts once`() {
        val ranked = SimilarByTags.rank(
            self = self,
            creatorsByTag = mapOf("onepiece" to listOf(a, a, a)),
            limit = 10,
        )

        assertEquals(1, ranked.single().sharedTags)
        assertEquals(1.0, ranked.single().score, 0.0001)
    }

    @Test
    fun `nothing to rank when no tags came back`() {
        assertTrue(SimilarByTags.rank(self, emptyMap(), 10).isEmpty())
        assertTrue(SimilarByTags.rank(self, mapOf("t" to listOf(self)), 10).isEmpty())
    }

    private companion object {
        val a = CreatorKey("patreon", "a")
        val b = CreatorKey("patreon", "b")
    }
}
