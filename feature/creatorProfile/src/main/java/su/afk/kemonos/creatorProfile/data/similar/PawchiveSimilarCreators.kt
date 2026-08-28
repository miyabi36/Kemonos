package su.afk.kemonos.creatorProfile.data.similar

import su.afk.kemonos.creatorProfile.api.domain.models.profileSimilar.SimilarCreator
import su.afk.kemonos.creatorProfile.data.api.CreatorProfileApi
import su.afk.kemonos.domain.SelectedSite
import su.afk.kemonos.network.util.safeCallOrNull
import su.afk.kemonos.storage.api.repository.creators.IStoreCreatorsRepository
import javax.inject.Inject

/**
 * Похожие авторы для pawchive.
 *
 * Источник не отдаёт ни `/recommended`, ни `/similar` — оба отвечают 404. Зато
 * у него есть теги автора и поиск постов по тегу, из чего список и собирается:
 * берём самые частые теги автора, смотрим, кто ещё под ними публикует, и
 * ранжируем по тому, насколько узкие теги их роднят.
 */
internal class PawchiveSimilarCreators @Inject constructor(
    private val api: CreatorProfileApi,
    private val storeCreatorsRepository: IStoreCreatorsRepository,
) {

    suspend fun find(service: String, id: String): List<SimilarCreator> {
        val tags = safeCallOrNull(
            api = { api.getProfileTags(service, id) },
            mapper = { dto -> dto },
        ).orEmpty()

        if (tags.isEmpty()) return emptyList()

        val probeTags = tags
            .sortedByDescending { it.postCount }
            .take(MAX_TAGS_PROBED)
            .map { it.tag }

        val creatorsByTag = probeTags.associateWith { tag -> creatorsForTag(tag) }
            .filterValues { it.isNotEmpty() }

        if (creatorsByTag.isEmpty()) return emptyList()

        val ranked = SimilarByTags.rank(
            self = CreatorKey(service = service, id = id),
            creatorsByTag = creatorsByTag,
            limit = MAX_RESULTS,
        )

        if (ranked.isEmpty()) return emptyList()

        /** Поиск по тегу отдаёт посты, а не авторов: имена берём из локального кэша. */
        val names = storeCreatorsRepository.getNamesByCompositeKeys(
            site = SelectedSite.P,
            compositeKeys = ranked.map { it.key.compositeKey }.toSet(),
        )

        return ranked.mapNotNull { scored ->
            val name = names[scored.key.compositeKey] ?: return@mapNotNull null

            SimilarCreator(
                id = scored.key.id,
                service = scored.key.service,
                name = name,
                indexed = "",
                publicId = null,
                relationId = null,
                updated = null,
                score = scored.score,
            )
        }
    }

    private suspend fun creatorsForTag(tag: String): List<CreatorKey> = safeCallOrNull(
        api = { api.getPostsByTag(tag = tag) },
        mapper = { posts ->
            posts.map { post -> CreatorKey(service = post.service, id = post.user) }
        },
    ).orEmpty()

    private companion object {
        /** Больше тегов — точнее список, но и запросов на каждый профиль больше. */
        const val MAX_TAGS_PROBED = 5
        const val MAX_RESULTS = 20
    }
}
