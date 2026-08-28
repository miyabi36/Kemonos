package su.afk.kemonos.creatorProfile.data.similar

/** Автор в выдаче поиска по тегу. */
internal data class CreatorKey(val service: String, val id: String) {
    val compositeKey: String get() = "$service:$id"
}

internal data class ScoredCreator(
    val key: CreatorKey,
    val score: Double,
    val sharedTags: Int,
)

/**
 * Ранжирование похожих авторов по общим тегам.
 *
 * Вес тега — обратное числу авторов, которые под ним нашлись: «NSFW» приводит
 * пол-сайта и почти ничего не говорит, а узкий тег — говорит много. Так общие
 * теги перестают перебивать редкие, и денежного списка стоп-тегов не нужно.
 */
internal object SimilarByTags {

    fun rank(
        self: CreatorKey,
        creatorsByTag: Map<String, List<CreatorKey>>,
        limit: Int,
    ): List<ScoredCreator> {
        val scores = mutableMapOf<CreatorKey, Double>()
        val shared = mutableMapOf<CreatorKey, Int>()

        creatorsByTag.forEach { (_, creators) ->
            val candidates = creators.toSet() - self
            if (candidates.isEmpty()) return@forEach

            val weight = 1.0 / candidates.size
            candidates.forEach { candidate ->
                scores[candidate] = (scores[candidate] ?: 0.0) + weight
                shared[candidate] = (shared[candidate] ?: 0) + 1
            }
        }

        return scores.entries
            .map { (key, score) ->
                ScoredCreator(key = key, score = score, sharedTags = shared[key] ?: 0)
            }
            .sortedWith(
                compareByDescending<ScoredCreator> { it.sharedTags }
                    .thenByDescending { it.score }
                    .thenBy { it.key.compositeKey }
            )
            .take(limit)
    }
}
