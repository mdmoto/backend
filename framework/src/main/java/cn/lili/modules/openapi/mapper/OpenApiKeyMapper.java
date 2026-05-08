package cn.lili.modules.openapi.mapper;

import cn.lili.modules.openapi.entity.OpenApiKey;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface OpenApiKeyMapper extends BaseMapper<OpenApiKey> {

    @Select("SELECT * FROM li_open_api_key WHERE api_key = #{apiKey} AND delete_flag = 0 limit 1")
    OpenApiKey findByApiKey(String apiKey);
}
