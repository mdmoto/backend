package cn.lili.modules.openapi.serviceimpl;

import cn.lili.modules.openapi.entity.OpenApiKey;
import cn.lili.modules.openapi.mapper.OpenApiKeyMapper;
import cn.lili.modules.openapi.service.OpenApiKeyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OpenApiKeyServiceImpl extends ServiceImpl<OpenApiKeyMapper, OpenApiKey> implements OpenApiKeyService {

    @Override
    public OpenApiKey getByApiKey(String apiKey) {
        return this.baseMapper.findByApiKey(apiKey);
    }

    @Override
    public OpenApiKey generateKeyForMember(String memberId) {
        OpenApiKey key = new OpenApiKey();
        key.setMemberId(memberId);
        key.setApiKey(UUID.randomUUID().toString().replace("-", ""));
        key.setApiSecret(UUID.randomUUID().toString().replace("-", ""));
        key.setPermissions("Read:Product,Write:Order");
        key.setStatus("OPEN");
        this.save(key);
        return key;
    }
}
