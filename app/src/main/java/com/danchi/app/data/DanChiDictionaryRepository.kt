package com.danchi.app.data

import com.danchi.app.domain.DictionaryEntry
import com.danchi.app.domain.DictionaryRepository
import com.danchi.app.domain.WordPatch
import com.danchi.app.domain.toWordPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DanChiDictionaryRepository(
    private val cacheDao: DictionaryCacheDao,
    private val local: LocalEcdictDataSource,
    private val remote: RemoteEcdictDataSource = RemoteEcdictDataSource()
) : DictionaryRepository {
    override suspend fun lookup(word: String): DictionaryEntry? {
        val key = DictionaryJson.normalizeWordKey(word)
        if (key.isBlank()) return null

        cacheDao.getFresh(key, System.currentTimeMillis())?.toDomain()?.let { return it }

        val localEntry = withContext(Dispatchers.IO) { local.lookup(key) }
        if (localEntry != null) {
            cacheDao.upsert(DictionaryCacheEntity.from(localEntry, ttlMillis = LocalCacheTtlMillis))
            return localEntry
        }

        val remoteEntry = remote.lookup(key)
        if (remoteEntry != null) {
            cacheDao.upsert(DictionaryCacheEntity.from(remoteEntry, ttlMillis = RemoteCacheTtlMillis))
            return remoteEntry
        }
        return null
    }

    override suspend fun searchPrefix(query: String, limit: Int): List<DictionaryEntry> {
        val prefix = DictionaryJson.normalizeWordKey(query)
        if (prefix.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) { local.searchPrefix(prefix, limit) }
    }

    override suspend fun buildWordPatch(word: String): WordPatch? {
        return lookup(word)?.toWordPatch(reason = "ecdict dictionary layer")
    }

    private companion object {
        const val LocalCacheTtlMillis = 1000L * 60 * 60 * 24 * 365
        const val RemoteCacheTtlMillis = 1000L * 60 * 60 * 24 * 30
    }
}
