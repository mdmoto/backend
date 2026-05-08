package cn.lili.controller.openapi;

import cn.lili.common.enums.ResultUtil;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.openapi.entity.OpenApiKey;
import cn.lili.modules.openapi.service.OpenApiKeyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "买家端,OpenAPI密钥接口")
@RequestMapping("/buyer/openapi/key")
public class OpenApiKeyBuyerController {

    @Autowired
    private OpenApiKeyService openApiKeyService;

    @GetMapping
    @ApiOperation(value = "获取当前用户的API密钥")
    public ResultMessage<cn.lili.modules.openapi.entity.vo.OpenApiKeyVO> getMyKey() {
        String memberId = UserContext.getCurrentUser().getId();
        OpenApiKey key = openApiKeyService.getOne(new LambdaQueryWrapper<OpenApiKey>()
                .eq(OpenApiKey::getMemberId, memberId)
                .last("LIMIT 1"));
        
        if (key == null) {
            return ResultUtil.data(null);
        }
        cn.lili.modules.openapi.entity.vo.OpenApiKeyVO vo = new cn.lili.modules.openapi.entity.vo.OpenApiKeyVO();
        vo.setApiKey(key.getApiKey());
        vo.setMemberId(key.getMemberId());
        vo.setPermissions(key.getPermissions());
        vo.setStatus(key.getStatus());
        
        return ResultUtil.data(vo);
    }

    @PostMapping("/generate")
    @ApiOperation(value = "生成/重置当前用户的API密钥")
    public ResultMessage<OpenApiKey> generateKey() {
        String memberId = UserContext.getCurrentUser().getId();
        // Remove existing key if any
        openApiKeyService.remove(new LambdaQueryWrapper<OpenApiKey>()
                .eq(OpenApiKey::getMemberId, memberId));
        
        OpenApiKey newKey = openApiKeyService.generateKeyForMember(memberId);
        return ResultUtil.data(newKey);
    }
}
