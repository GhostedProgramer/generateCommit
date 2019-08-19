package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.event.EntityEvent
import com.musicbible.model.Catalog
import com.musicbible.repository.CatalogRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface CatalogService : BaseService<Catalog>, CatalogRepository {
    override val modelName: String
        get() = "唱片编号"

    fun create(catalog: Catalog): Catalog

    fun remove(catalog: Catalog)
}

@Service
@Transactional
class CatalogServiceImpl(
    @Autowired val catalogRepository: CatalogRepository,
    @Autowired val labelService: LabelService,
    @Autowired val eventPublisher: ApplicationEventPublisher
) : CatalogService, CatalogRepository by catalogRepository {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun create(catalog: Catalog): Catalog {
        val saved = save(catalog)
        eventPublisher.publishEvent(EntityEvent.created(saved))
        logger.debug("Create a new Catalog[{}]", saved.id)

        return saved
    }

    override fun remove(catalog: Catalog) {
        delete(catalog)
        eventPublisher.publishEvent(
            EntityEvent.deleted(catalog, mapOf("labelId" to catalog.labelId))
        )
        logger.debug("Catalog[{}] deleted", catalog.id)
    }
}
