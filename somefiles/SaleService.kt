package com.musicbible.service

import com.boostfield.spring.payload.PageQuery
import com.boostfield.spring.service.DraftableService
import com.boostfield.spring.service.SearchService
import com.boostfield.spring.service.TermsAggregationResult
import com.musicbible.es.model.EsSale
import com.musicbible.es.repository.SaleEsRepository
import com.musicbible.mapper.sale.Key
import com.musicbible.mapper.sale.SaleFilterInput
import com.musicbible.mapper.sale.SaleInput
import com.musicbible.mapper.sale.SaleMapper
import com.musicbible.model.DocumentStatus
import com.musicbible.model.PackageCondition
import com.musicbible.model.QSale
import com.musicbible.model.Release
import com.musicbible.model.ReleaseCondition
import com.musicbible.model.Sale
import com.musicbible.model.SalePlatformEnum
import com.musicbible.repository.SaleRepository
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID


interface SaleService : DraftableService<Sale>, SaleRepository, SearchService<EsSale, UUID> {
    override val modelName: String
        get() = "在售唱片"

    fun countByRelease(release: Release) =
        countByDeletedFalseAndRelease(release)

    fun onSale(sale: Sale)
    fun unSale(sale: Sale)
    fun updateImages(sale: Sale, images: Array<String>): Sale
    fun updateRelease(id: UUID, releaseId: UUID): Sale
    fun updateFields(sale: Sale, saleInput: SaleInput): Sale
    fun aggregationList(input: SaleFilterInput): List<TermsAggregationResult>
    fun frontendListSearch(input: SaleFilterInput): Page<EsSale>
    fun backendList(
        pageQuery: PageQuery,
        releaseCondition: ReleaseCondition?,
        packageCondition: PackageCondition?,
        platformEnum: SalePlatformEnum?,
        q: String?,
        key: Key?
    ): Page<Sale>

    fun newSalesList(input: PageQuery): Page<Sale>
    fun refreshCommentCount(sale: Sale)
    fun indexToEs(sale: Sale)
}

@Service
@Transactional
class SaleServiceImpl(
    @Autowired val saleMapper: SaleMapper,
    @Autowired val saleEsRepository: SaleEsRepository,
    @Autowired val saleRepository: SaleRepository,
    @Autowired val releaseService: ReleaseService,
    @Autowired val elasticsearchTemplate: ElasticsearchTemplate,
    @Autowired val esIndexService: EsIndexService<Sale, EsSale>
) : SaleService, SaleRepository by saleRepository {

    private val logger = LoggerFactory.getLogger(SaleService::class.java)

    override fun <S : Sale> save(entity: S): S {
        return saleRepository.save(entity).also { indexToEs(it) }
    }


    override fun onSale(sale: Sale) {
        sale.onSale = true
        sale.publish()
        save(sale)
    }


    override fun unSale(sale: Sale) {
        sale.onSale = false
        sale.suppress()
        save(sale)
    }


    override fun softDelete(entity: Sale) {
        entity.softDelete()
        saleRepository.save(entity)
        saleEsRepository.deleteById(entity.id)
    }


    override fun updateImages(sale: Sale, images: Array<String>): Sale {
        sale.images = images
        return save(sale)
    }


    override fun updateRelease(id: UUID, releaseId: UUID): Sale {
        val sale = findExistsOrThrow(id)
        val release = releaseService.findExistsOrThrow(releaseId)
        sale.release = release
        releaseService.freshSaleCount(release, saleService = this)
        return save(sale)
    }


    override fun updateFields(sale: Sale, saleInput: SaleInput): Sale {
        sale.platform = saleInput.platform
        sale.url = saleInput.url
        sale.price = saleInput.price
        sale.releaseCondition = saleInput.releaseCondition
        sale.packageCondition = saleInput.packageCondition
        sale.description = saleInput.description
        return save(sale)
    }

    override fun indexToEs(sale: Sale) {
        logger.debug("Index Sale[${sale.id}] to elasticsearch")
        esIndexService.indexToEs(sale, saleMapper, saleEsRepository)
    }

    override fun aggregationList(input: SaleFilterInput): List<TermsAggregationResult> {
        val fields = listOf(
            "genreIds",
            "styleIds",
            "mediaId",
            "regionId",
            "issueYear").map { "release.$it" }.toMutableList()
        fields.addAll(listOf("releaseCondition", "packageCondition"))

        return aggregationListTemplate("sale", fields, elasticsearchTemplate) {
            buildQueryBuilder(input)
        }
    }

    override fun frontendListSearch(input: SaleFilterInput): Page<EsSale> {
        return frontendListSearchTemplate("sale", input, saleEsRepository) {
            buildQueryBuilder(input)
        }
    }

    private fun buildQueryBuilder(input: SaleFilterInput): QueryBuilder {
        val queryBuilder = BoolQueryBuilder()

        input.releaseId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("release.id", it))
        }

        input.genreIds?.also {
            queryBuilder.filter(QueryBuilders.termsQuery("release.genreIds", it))

        }
        input.styleIds?.also {
            queryBuilder.filter(QueryBuilders.termsQuery("release.styleIds", it))
        }
        input.mediaId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("release.mediaId", it))
        }

        input.regionId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("release.regionId", it))
        }

        val issueYearRange = input.getYearRange()
        if (issueYearRange != null) {
            queryBuilder.filter(QueryBuilders.rangeQuery("release.issueYear")
                .from(issueYearRange.first, true)
                .to(issueYearRange.second, true)
            )
        }

        input.packageCondition?.also {
            queryBuilder.filter(QueryBuilders.termQuery("packageCondition", it.name))
        }
        input.releaseCondition?.also {
            queryBuilder.filter(QueryBuilders.termQuery("releaseCondition", it.name))
        }

        input.creatorId?.also {
            queryBuilder.filter(QueryBuilders.termQuery("createdBy.id", it))
        }
        queryBuilder.filter(QueryBuilders.termsQuery("status", DocumentStatus.PUBLISHED.name))
        return queryBuilder
    }

    override fun newSalesList(input: PageQuery): Page<Sale> {
        return findPublished(input.pageable())
    }

    override fun refreshCommentCount(sale: Sale) {
        sale.commentCount += 1
        save(sale)
    }

    override fun backendList(
        pageQuery: PageQuery,
        releaseCondition: ReleaseCondition?,
        packageCondition: PackageCondition?,
        platformEnum: SalePlatformEnum?,
        q: String?, key: Key?
    ): Page<Sale> {
        val qSale: QSale = QSale.sale
        var criteria = QSale.sale.deleted.isFalse
        releaseCondition?.also { criteria = criteria.and(qSale.releaseCondition.eq(releaseCondition)) }
        packageCondition?.also { criteria = criteria.and(qSale.packageCondition.eq(packageCondition)) }
        platformEnum?.also { criteria = criteria.and(qSale.platform.eq(platformEnum)) }
        key?.also {
            when (key) {
                Key.NAME -> criteria = criteria.and(qSale.release.titleCN.contains(q)
                    .or(qSale.release.title1.contains(q))
                    .or(qSale.release.title2.contains(q)))
                Key.NUMBER -> criteria = criteria.and(
                    qSale.release.catalogs.any().number.contains(q)
                )
                Key.CREATOR -> criteria = criteria.and(
                    qSale.createdBy.nickName.contains(q)
                        .or(qSale.createdBy.userName.contains(q))
                )
                Key.LABEL -> criteria = criteria.and(
                    qSale.release.catalogs.any().label.name.contains(q)
                        .or(qSale.release.catalogs.any().label.nameCN.contains(q))
                )
            }
        }
        return findAll(criteria, pageQuery.pageable())
    }
}
