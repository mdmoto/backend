package cn.lili.modules.permission.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.SearchVO;
import cn.lili.modules.permission.entity.vo.SystemLogVO;
import cn.lili.modules.permission.repository.SystemLogRepository;
import cn.lili.modules.permission.service.SystemLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.json.JsonData;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统日志 (Restored for Spring Boot 3 / ES 8.x)
 *
 * @author Chopper
 * @since 2020/11/17 3:45 下午
 */
@Service
public class SystemLogServiceImpl implements SystemLogService {

    @Autowired(required = false)
    private SystemLogRepository systemLogRepository;

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void saveLog(SystemLogVO systemLogVO) {
        if (systemLogRepository != null) {
            systemLogRepository.save(systemLogVO);
        }
    }

    @Override
    public void deleteLog(List<String> id) {
        if (systemLogRepository != null) {
            for (String s : id) {
                systemLogRepository.deleteById(s);
            }
        }
    }

    @Override
    public void flushAll() {
        if (systemLogRepository != null) {
            systemLogRepository.deleteAll();
        }
    }

    @Override
    public IPage<SystemLogVO> queryLog(String storeId, String operatorName, String key, SearchVO searchVo,
            PageVO pageVO) {
        pageVO.setNotConvert(true);
        IPage<SystemLogVO> iPage = new Page<>();
        if (elasticsearchOperations == null) {
            return iPage;
        }

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (CharSequenceUtil.isNotEmpty(storeId)) {
            boolBuilder.filter(f -> f.match(m -> m.field("storeId").query(storeId)));
        }

        if (CharSequenceUtil.isNotEmpty(operatorName)) {
            boolBuilder.must(m -> m.match(mm -> mm.field("username").query(operatorName)));
        }

        if (CharSequenceUtil.isNotEmpty(key)) {
            boolBuilder.filter(f -> f.multiMatch(mm -> mm
                    .fields("requestUrl", "requestParam", "responseBody", "name")
                    .query(key)
                    .fuzziness("AUTO")));
        }

        // 时间有效性判定
        if (searchVo.getConvertStartDate() != null && searchVo.getConvertEndDate() != null) {
            boolBuilder.filter(f -> f.range(r -> r
                    .field("createTime")
                    .gte(JsonData.of(searchVo.getConvertStartDate().getTime()))
                    .lte(JsonData.of(searchVo.getConvertEndDate().getTime()))));
        }

        Pageable pageable = Pageable.unpaged();
        if (pageVO.getPageNumber() != null && pageVO.getPageSize() != null) {
            int pageNumber = pageVO.getPageNumber() - 1;
            if (pageNumber < 0) {
                pageNumber = 0;
            }

            Sort sort;
            if (CharSequenceUtil.isNotEmpty(pageVO.getOrder()) && CharSequenceUtil.isNotEmpty(pageVO.getSort())) {
                sort = Sort.by(Sort.Direction.valueOf(pageVO.getOrder().toUpperCase()), pageVO.getSort());
            } else {
                sort = Sort.by(Sort.Direction.DESC, "createTime");
            }

            pageable = PageRequest.of(pageNumber, pageVO.getPageSize(), sort);
            iPage.setCurrent(pageVO.getPageNumber());
            iPage.setSize(pageVO.getPageSize());
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(pageable)
                .build();

        SearchHits<SystemLogVO> searchResult = elasticsearchOperations.search(query, SystemLogVO.class);

        iPage.setTotal(searchResult.getTotalHits());
        iPage.setRecords(searchResult.getSearchHits().stream().map(SearchHit::getContent).collect(Collectors.toList()));
        return iPage;
    }

}
