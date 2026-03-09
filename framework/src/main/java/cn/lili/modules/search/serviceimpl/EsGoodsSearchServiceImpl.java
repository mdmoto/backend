package cn.lili.modules.search.serviceimpl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.common.context.LanguageContextHolder;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.vo.PageVO;
import cn.lili.elasticsearch.BaseElasticsearchService;
import cn.lili.modules.search.entity.dos.EsGoodsIndex;
import cn.lili.modules.search.entity.dos.EsGoodsRelatedInfo;
import cn.lili.modules.search.entity.dto.EsGoodsSearchDTO;
import cn.lili.modules.search.entity.dto.ParamOptions;
import cn.lili.modules.search.entity.dto.SelectorOptions;
import cn.lili.modules.search.service.EsGoodsSearchService;
import cn.lili.mybatis.util.PageUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.json.JsonData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ES商品搜索业务层实现 (Restored for Spring Boot 3 / ES 8.x)
 *
 * @author paulG
 * @since 2020/10/16
 **/
@Slf4j
@Service
public class EsGoodsSearchServiceImpl extends BaseElasticsearchService implements EsGoodsSearchService {

    private static final String MINIMUM_SHOULD_MATCH = "20%";
    private static final String ATTR_PATH = "attrList";
    private static final String ATTR_VALUE = "attrList.value";
    private static final String ATTR_NAME = "attrList.name";
    private static final String ATTR_SORT = "attrList.sort";
    private static final String ATTR_BRAND_ID = "brandId";
    private static final String ATTR_BRAND_NAME = "brandName";
    private static final String ATTR_BRAND_URL = "brandUrl";
    private static final String ATTR_NAME_KEY = "nameList";
    private static final String ATTR_VALUE_KEY = "valueList";

    @Override
    public SearchPage<EsGoodsIndex> searchGoods(EsGoodsSearchDTO searchDTO, PageVO pageVo) {
        Query searchQuery = createSearchQueryBuilder(searchDTO, pageVo);
        SearchHits<EsGoodsIndex> searchHits = elasticsearchOperations.search(searchQuery, EsGoodsIndex.class);
        return SearchHitSupport.searchPageFor(searchHits, searchQuery.getPageable());
    }

    @Override
    public <T> SearchPage<T> searchGoods(Query searchQuery, Class<T> clazz) {
        SearchHits<T> searchHits = elasticsearchOperations.search(searchQuery, clazz);
        return SearchHitSupport.searchPageFor(searchHits, searchQuery.getPageable());
    }

    @Override
    public Page<EsGoodsIndex> searchGoodsByPage(EsGoodsSearchDTO searchDTO, PageVO pageVo) {
        SearchPage<EsGoodsIndex> searchPage = this.searchGoods(searchDTO, pageVo);
        Page<EsGoodsIndex> page = PageUtil.initPage(pageVo);
        page.setTotal(searchPage.getTotalElements());
        page.setRecords(searchPage.getContent().stream().map(SearchHit::getContent).collect(Collectors.toList()));
        return page;
    }

    @Override
    public EsGoodsRelatedInfo getSelector(EsGoodsSearchDTO goodsSearch, PageVO pageVo) {
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    buildBoolQuery(b, goodsSearch);
                    return b;
                }))
                .withAggregation("category_path", Aggregation.of(a -> a.terms(t -> t.field("categoryPath").size(100))))
                .withAggregation("brand_id", Aggregation.of(a -> a.terms(t -> t.field("brandId").size(100))))
                .withPageable(Pageable.ofSize(1)) // We only need aggregations
                .build();

        SearchHits<EsGoodsIndex> searchHits = elasticsearchOperations.search(searchQuery, EsGoodsIndex.class);

        EsGoodsRelatedInfo relatedInfo = new EsGoodsRelatedInfo();

        // TODO: Restore ES aggregation parsing using the correct Spring Data ES 5.x /
        // ES 8 client classes.
        // Temporarily disabled due to API changes in Spring Boot 3 / Spring Data ES 5.x
        relatedInfo.setCategories(new ArrayList<>());
        relatedInfo.setBrands(new ArrayList<>());
        relatedInfo.setParamOptions(new ArrayList<>());

        return relatedInfo;
    }

    @Override
    public List<EsGoodsIndex> getEsGoodsBySkuIds(List<String> skuIds, PageVO pageVo) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.terms(t -> t.field("id")
                        .terms(v -> v.value(skuIds.stream().map(FieldValue::of).collect(Collectors.toList())))))
                .withPageable(PageUtil.initPageable(pageVo))
                .build();
        return elasticsearchOperations.search(query, EsGoodsIndex.class)
                .stream().map(SearchHit::getContent).collect(Collectors.toList());
    }

    @Override
    public EsGoodsIndex getEsGoodsById(String id) {
        return elasticsearchOperations.get(id, EsGoodsIndex.class);
    }

    @Override
    public Query createSearchQueryBuilder(EsGoodsSearchDTO searchDTO, PageVO pageVo) {
        return NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    buildBoolQuery(b, searchDTO);
                    return b;
                }))
                .withPageable(PageUtil.initPageable(pageVo))
                .build();
    }

    private void buildBoolQuery(BoolQuery.Builder builder, EsGoodsSearchDTO searchDTO) {
        // Keyword Search
        if (CharSequenceUtil.isNotEmpty(searchDTO.getKeyword())) {
            this.keywordSearch(builder, searchDTO.getKeyword());
        }

        // Common Filters
        this.commonSearch(builder, searchDTO);
    }

    private void commonSearch(BoolQuery.Builder builder, EsGoodsSearchDTO searchDTO) {
        // Brand Filter
        if (CharSequenceUtil.isNotEmpty(searchDTO.getBrandId())) {
            String[] brands = searchDTO.getBrandId().split("@");
            builder.filter(f -> f.terms(t -> t.field(ATTR_BRAND_ID)
                    .terms(v -> v.value(Arrays.stream(brands).map(FieldValue::of).collect(Collectors.toList())))));
        }

        // Category Filter
        if (CharSequenceUtil.isNotEmpty(searchDTO.getCategoryId())) {
            builder.filter(f -> f.wildcard(w -> w.field("categoryPath").value("*" + searchDTO.getCategoryId() + "*")));
        }

        // Recommend
        if (searchDTO.getRecommend() != null) {
            builder.filter(f -> f.term(t -> t.field("recommend").value(searchDTO.getRecommend())));
        }

        // Price Filter
        if (CharSequenceUtil.isNotEmpty(searchDTO.getPrice())) {
            String[] prices = searchDTO.getPrice().split("_");
            if (prices.length > 0) {
                double min = Convert.toDouble(prices[0], 0.0);
                double max = prices.length == 2 ? Convert.toDouble(prices[1], Double.MAX_VALUE) : Double.MAX_VALUE;
                if (min > max)
                    throw new ServiceException("价格区间错误");
                builder.filter(f -> f.range(r -> r.field("price").gte(JsonData.of(min)).lte(JsonData.of(max))));
            }
        }

        // Add more filters as needed...
    }

    private void keywordSearch(BoolQuery.Builder builder, String keyword) {
        String lang = LanguageContextHolder.get();
        String searchField = "zh".equals(lang) ? "goodsName" : "title_" + lang;

        builder.must(m -> m.match(ma -> ma.field(searchField).query(keyword).operator(Operator.Or)
                .minimumShouldMatch(MINIMUM_SHOULD_MATCH)));

        // Boost phrase matching
        builder.should(s -> s.matchPhrase(mp -> mp.field(searchField).query(keyword).boost(10.0f)));
    }
}
