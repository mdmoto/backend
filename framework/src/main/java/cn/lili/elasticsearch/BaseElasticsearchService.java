package cn.lili.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

/**
 * BaseElasticsearchService (Restored for Spring Boot 3 / ES 8.x)
 *
 * @author paulG
 * @since 2020/10/14
 **/
@Slf4j
@Service
public class BaseElasticsearchService {

    @Autowired(required = false)
    protected ElasticsearchOperations elasticsearchOperations;

    /**
     * 判断索引是否存在
     *
     * @param indexName 索引名称
     * @return 是否存在
     */
    public boolean indexExist(String indexName) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        return indexOps.exists();
    }

    /**
     * 创建索引
     *
     * @param indexName 索引名称
     */
    public void createIndex(String indexName) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        if (!indexOps.exists()) {
            indexOps.create();
            log.info("创建索引成功: {}", indexName);
        }
    }

    /**
     * 删除索引
     *
     * @param indexName 索引名称
     */
    public void deleteIndex(String indexName) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        indexOps.delete();
        log.info("删除索引成功: {}", indexName);
    }
}
