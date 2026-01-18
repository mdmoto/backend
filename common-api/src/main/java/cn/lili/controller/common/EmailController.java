package cn.lili.controller.common;

import cn.lili.cache.limit.annotation.LimitPoint;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.email.EmailUtil;
import cn.lili.modules.verification.entity.enums.VerificationEnums;
import cn.lili.modules.verification.service.VerificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 邮箱验证码接口
 *
 * @author Maollar
 * @since 2025/01/XX
 */
@RestController
@Api(tags = "邮箱验证码接口")
@RequestMapping("/common/common/email")
public class EmailController {

    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private VerificationService verificationService;

    @LimitPoint(name = "email_send", key = "email")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "path", dataType = "String", name = "email", value = "邮箱地址"),
            @ApiImplicitParam(paramType = "header", dataType = "String", name = "uuid", value = "uuid"),
    })
    @GetMapping("/{verificationEnums}/{email}")
    @ApiOperation(value = "发送邮箱验证码,一分钟同一个ip请求1次")
    public ResultMessage getEmailCode(
            @RequestHeader String uuid,
            @PathVariable String email,
            @PathVariable VerificationEnums verificationEnums) {
        verificationService.check(uuid, verificationEnums);
        emailUtil.sendEmailCode(email, verificationEnums, uuid);
        return ResultUtil.success(ResultCode.VERIFICATION_SEND_SUCCESS);
    }

    @PostMapping("/test")
    @ApiOperation(value = "测试发送邮件（用于测试邮箱配置）")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "String", name = "toEmail", value = "接收邮箱", required = true),
    })
    public ResultMessage testEmail(@RequestParam String toEmail) {
        String subject = "Maollar 邮箱配置测试";
        String content = "这是一封测试邮件，如果您收到此邮件，说明邮箱配置成功！\n\n" +
                "发送时间：" + new java.util.Date() + "\n" +
                "接收邮箱：" + toEmail;
        emailUtil.sendTestEmail(toEmail, subject, content);
        return ResultUtil.success(ResultCode.SUCCESS);
    }
}

