package cn.lili.modules.openapi.service;

import cn.lili.modules.openapi.entity.OpenApiKey;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OpenApiKeyService extends IService<OpenApiKey> {
    
    OpenApiKey getByApiKey(String apiKey);

    OpenApiKey generateKeyForMember(String memberId);
}
