package cn.lili.controller.payment;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.payment.kit.CashierSupport;
import cn.lili.modules.payment.kit.dto.PayParam;
import cn.lili.modules.payment.entity.enums.PaymentClientEnum;
import cn.lili.modules.payment.entity.enums.PaymentMethodEnum;
import cn.lili.modules.payment.kit.params.dto.CashierParam;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 买家端,收银台接口
 *
 * @author Chopper
 * @since 2020-12-18 16:59
 */
@Slf4j
@RestController
@Api(tags = "买家端,收银台接口")
@RequestMapping("/buyer/payment/cashier")
public class CashierController {

    @Autowired
    private CashierSupport cashierSupport;

    @ApiImplicitParams({
            @ApiImplicitParam(name = "client", value = "客户端类型", paramType = "path", allowableValues = "PC,H5,WECHAT_MP,APP")
    })
    @GetMapping(value = "/tradeDetail")
    @ApiOperation(value = "获取支付详情")
    public ResultMessage paymentParams(@Validated PayParam payParam) {
        CashierParam cashierParam = cashierSupport.cashierParam(payParam);
        return ResultUtil.data(cashierParam);
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "paymentMethod", value = "支付方式", paramType = "path", allowableValues = "WECHAT,ALIPAY"),
            @ApiImplicitParam(name = "paymentClient", value = "调起方式", paramType = "path", allowableValues = "APP,NATIVE,JSAPI,H5,MP")
    })
    @GetMapping(value = "/pay/{paymentMethod}/{paymentClient}")
    @ApiOperation(value = "支付")
    public ResultMessage payment(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable String paymentMethod,
            @PathVariable String paymentClient,
            @Validated PayParam payParam) {
        PaymentMethodEnum paymentMethodEnum = PaymentMethodEnum.valueOf(paymentMethod);
        PaymentClientEnum paymentClientEnum = PaymentClientEnum.valueOf(paymentClient);

        log.error("========== Controller.payment 开始 ==========");
        log.error("PaymentMethod: {}, PaymentClient: {}", paymentMethod, paymentClient);

        try {
            ResultMessage<Object> result = cashierSupport.payment(paymentMethodEnum, paymentClientEnum, request,
                    response, payParam);

            log.error("========== payment() 返回结果 ==========");
            log.error("Result is null: {}", result == null);
            if (result != null) {
                log.error("Result.success: {}", result.isSuccess());
                log.error("Result.code: {}", result.getCode());
                log.error("Result.message: {}", result.getMessage());
                log.error("Result.result is null: {}", result.getResult() == null);
                if (result.getResult() != null) {
                    log.error("Result.result type: {}", result.getResult().getClass().getName());
                    if (result.getResult() instanceof String) {
                        String content = (String) result.getResult();
                        log.error("Result.result length: {}", content.length());
                        log.error("Result.result first 200 chars: {}",
                                content.substring(0, Math.min(200, content.length())));
                    }
                }
            }

            // 对于H5支付，如果返回的是HTML字符串，需要特殊处理
            if (paymentClientEnum == PaymentClientEnum.H5 && result != null && result.getResult() != null) {
                log.error("========== H5支付，检查HTML ==========");
                Object resultData = result.getResult();
                // 检查是否是HTML字符串
                if (resultData instanceof String) {
                    String htmlContent = (String) resultData;
                    log.error("Result data is String, length: {}", htmlContent.length());
                    boolean hasForm = htmlContent.contains("<form");
                    boolean hasAlipay = htmlContent.contains("alipay");
                    boolean hasAction = htmlContent.contains("action=");
                    log.error("Contains <form: {}, alipay: {}, action=: {}", hasForm, hasAlipay, hasAction);

                    if (hasForm || hasAlipay || hasAction) {
                        log.error("========== 检测到HTML，直接写入response ==========");
                        // 直接写入HTML到response
                        try {
                            response.setContentType("text/html;charset=UTF-8");
                            response.getWriter().write(htmlContent);
                            response.getWriter().flush();
                            response.getWriter().close();
                            log.error("========== HTML写入成功，返回null ==========");
                            return null; // 已经写入response，返回null
                        } catch (Exception e) {
                            log.error("========== 写入HTML响应失败 ==========", e);
                            // 如果写入失败，返回原始结果
                        }
                    } else {
                        log.error("========== 未检测到HTML标记 ==========");
                    }
                } else {
                    log.error("Result data不是String，类型: {}", resultData.getClass().getName());
                }
            }

            log.error("========== 返回result对象 ==========");
            return result;
        } catch (ServiceException se) {
            log.error("========== 捕获ServiceException ==========");
            log.error("支付异常 - ServiceException: {}", se.getMessage(), se);
            throw se;
        } catch (Exception e) {
            log.error("========== 捕获Exception ==========");
            log.error("收银台支付错误 - Exception: {}", e.getMessage(), e);
            throw new ServiceException(ResultCode.PAY_ERROR);
        }
    }

    @ApiOperation(value = "支付回调")
    @RequestMapping(value = "/callback/{paymentMethod}", method = { RequestMethod.GET, RequestMethod.POST })
    public ResultMessage<Object> callback(HttpServletRequest request, @PathVariable String paymentMethod) {

        PaymentMethodEnum paymentMethodEnum = PaymentMethodEnum.valueOf(paymentMethod);

        cashierSupport.callback(paymentMethodEnum, request);

        return ResultUtil.success(ResultCode.PAY_SUCCESS);
    }

    @ApiOperation(value = "支付异步通知")
    @RequestMapping(value = "/notify/{paymentMethod}", method = { RequestMethod.GET, RequestMethod.POST })
    public void notify(HttpServletRequest request, @PathVariable String paymentMethod) {

        PaymentMethodEnum paymentMethodEnum = PaymentMethodEnum.valueOf(paymentMethod);

        cashierSupport.notify(paymentMethodEnum, request);

    }

    @ApiOperation(value = "查询支付结果")
    @GetMapping(value = "/result")
    public ResultMessage<Boolean> paymentResult(PayParam payParam) {
        return ResultUtil.data(cashierSupport.paymentResult(payParam));
    }
}
