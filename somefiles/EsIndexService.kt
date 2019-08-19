package com.musicbible.service

import com.boostfield.spring.mapper.EsMapper
import com.boostfield.spring.persistence.Base
import com.boostfield.spring.persistence.BaseRepository
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CompletableFuture

interface EsIndexService<T : Base, ES> {
    fun indexToEs(entity: T, mapper: EsMapper<T, ES>, esRepository: ElasticsearchRepository<ES, UUID>)

    /**
     * 不能在同步环境下调用，因为会使用 dbRepository 重新读取实体，此时 Service 中对实体的修改可能还未提交
     */
    fun indexToEs(
        ids: List<UUID>, mapper: EsMapper<T, ES>,
        dbRepository: BaseRepository<T>, esRepository: ElasticsearchRepository<ES, UUID>
    )

    fun asyncIndexToEs(
        ids: List<UUID>, mapper: EsMapper<T, ES>,
        dbRepository: BaseRepository<T>, esRepository: ElasticsearchRepository<ES, UUID>
    ): CompletableFuture<Unit>
}

@Service
class EsIndexServiceImpl<T : Base, ES> : EsIndexService<T, ES> {

    override fun indexToEs(entity: T, mapper: EsMapper<T, ES>, esRepository: ElasticsearchRepository<ES, UUID>) {
        esRepository.save(mapper.toEs(entity))
    }

    override fun indexToEs(
        ids: List<UUID>, mapper: EsMapper<T, ES>,
        dbRepository: BaseRepository<T>, esRepository: ElasticsearchRepository<ES, UUID>
    ) {
        val chunk = dbRepository.findAllById(ids)
            .map(mapper::toEs)
        esRepository.saveAll(chunk)
    }

    @Async
    override fun asyncIndexToEs(
        ids: List<UUID>, mapper: EsMapper<T, ES>, dbRepository: BaseRepository<T>, esRepository: ElasticsearchRepository<ES, UUID>
    ): CompletableFuture<Unit> {
        indexToEs(ids, mapper, dbRepository, esRepository)
        return CompletableFuture.completedFuture(Unit)
    }
}
