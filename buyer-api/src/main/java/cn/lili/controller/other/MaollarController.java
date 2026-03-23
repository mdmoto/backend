package cn.lili.controller.other;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.member.service.MemberPointsHistoryService;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.modules.system.service.FxRateSnapshotService;
import cn.lili.modules.system.service.MaollarTierService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import cn.lili.modules.member.service.MaoWithdrawalService;
import cn.lili.modules.member.entity.dos.MaoWithdrawalLog;
import cn.lili.common.vo.PageVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.lili.common.security.context.UserContext;
import cn.lili.mybatis.util.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

import java.util.Map;

/**
 * Maollar DApp 集成接口
 */
@Slf4j
@RestController
@Api(tags = "Maollar DApp 集成接口")
@RequestMapping({ "/api/v1/maomall", "/buyer/maomall", "/api/v1/maollar", "/buyer/maollar", "/api/v1/mao-proxy", "/buyer/mao-proxy" })
public class MaollarController {

    @org.springframework.beans.factory.annotation.Value("${lili.maollar.solana.gateway-secret:}")
    private String gatewaySecret;

    @Autowired
    private MaollarTierService maollarTierService;

    @Autowired
    private MemberPointsHistoryService memberPointsHistoryService;

    @Autowired
    private MaoWithdrawalService maoWithdrawalService;

    @Autowired
    private StripePaymentSnapshotService stripePaymentSnapshotService;

    @Autowired
    private FxRateSnapshotService fxRateSnapshotService;

    @ApiOperation(value = "获取当前档位状态")
    @GetMapping("/tier-status")
    public ResultMessage<Map<String, Object>> getTierStatus() {
        // 基于 Stripe 真实收款数据计算当前档位
        double totalSalesUSD = stripePaymentSnapshotService.getCompletedTotalSalesUSD();
        return ResultUtil.data(maollarTierService.getTierStatus(totalSalesUSD));
    }

    @ApiOperation(value = "获取全站积分默克尔根")
    @GetMapping("/merkle-root")
    public ResultMessage<String> getMerkleRoot() {
        return ResultUtil.data(memberPointsHistoryService.getLatestMerkleRoot());
    }

    @ApiOperation(value = "DApp 兑换回调信号")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "memberId", value = "会员ID", required = true, paramType = "query"),
            @ApiImplicitParam(name = "points", value = "兑换扣除积分", required = true, paramType = "query"),
            @ApiImplicitParam(name = "txHash", value = "链上交易哈希", required = true, paramType = "query")
    })
    @PostMapping("/exchange-log")
    public ResultMessage<Object> exchangeLog(
            @RequestHeader(value = "X-MAO-Timestamp", required = false) String timestampStr,
            @RequestHeader(value = "X-MAO-Signature", required = false) String signature,
            String memberId, Long points, String txHash) {

        // P0 Fix: Verify HMAC signature and Freshness
        if (cn.hutool.core.util.StrUtil.isBlank(signature) || cn.hutool.core.util.StrUtil.isBlank(timestampStr)
                || cn.hutool.core.util.StrUtil.isBlank(txHash)) {
            return ResultUtil.error(ResultCode.USER_AUTHORITY_ERROR);
        }

        // 1. Freshness check: 防重放时间窗口 (5分钟)
        try {
            long requestTs = Long.parseLong(timestampStr);
            if (Math.abs(System.currentTimeMillis() - requestTs) > 300000) {
                log.warn("【安全拦截】DApp 回调请求已失效 (Expired): memberId={}, txHash={}", memberId, txHash);
                return ResultUtil.error(ResultCode.USER_AUTHORITY_ERROR);
            }
        } catch (Exception e) {
            return ResultUtil.error(ResultCode.USER_AUTHORITY_ERROR);
        }

        // 2. Validate signature: timestamp + memberId + points + txHash
        String bodyText = memberId + points + txHash;
        String calcSignature = cn.hutool.crypto.SecureUtil.hmacSha256(gatewaySecret).digestHex(timestampStr + bodyText);

        if (!calcSignature.equalsIgnoreCase(signature)) {
            log.error("【安全预警】DApp 回调签名校验失败! memberId={}, txHash={}", memberId, txHash);
            return ResultUtil.error(ResultCode.USER_AUTHORITY_ERROR);
        }

        if (memberPointsHistoryService.pointExchangeCallback(memberId, points, txHash)) {
            return ResultUtil.success();
        } else {
            return ResultUtil.error(ResultCode.POINT_NOT_ENOUGH);
        }
    }

    @ApiOperation(value = "获取当前汇率列表")
    @GetMapping("/rates")
    public ResultMessage<Map<String, Object>> getRates() {
        // 重要：此接口仅供前端展示换算使用，不参与发币计价
        Map<String, Double> rates = fxRateSnapshotService.getRates("USD");
        if (rates == null) {
            return ResultUtil.error(ResultCode.ERROR.code(), "Exchange rates unavailable");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("base", "USD");
        result.put("rates", rates);
        result.put("asOf", System.currentTimeMillis() / 1000); // 简化处理
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "获取支持的展示币种列表")
    @GetMapping("/supported-currencies")
    public ResultMessage<List<String>> getSupportedCurrencies() {
        return ResultUtil.data(fxRateSnapshotService.getSupportedCurrencies());
    }

    @ApiOperation(value = "发起积分兑换 $MAO 申请")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "points", value = "兑换扣除积分", required = true, paramType = "query"),
            @ApiImplicitParam(name = "solanaWalletAddress", value = "Solana 钱包地址", required = true, paramType = "query")
    })
    @PostMapping("/exchange")
    public ResultMessage<MaoWithdrawalLog> exchange(Long points, String solanaWalletAddress) {
        // 1. 创建申请记录并校验
        MaoWithdrawalLog logRecord = maoWithdrawalService.applyWithdrawal(points, solanaWalletAddress);
        // 2. 尝试执行扣分及网关发币 (这里的 executeWithdrawal 内部包含积分扣减逻辑)
        maoWithdrawalService.executeWithdrawal(logRecord.getId());
        return ResultUtil.data(logRecord);
    }

    @ApiOperation(value = "获取个人兑换记录")
    @GetMapping("/exchange-list")
    public ResultMessage<IPage<MaoWithdrawalLog>> exchangeList(PageVO page) {
        String memberId = UserContext.getCurrentUser().getId();
        QueryWrapper<MaoWithdrawalLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("member_id", memberId);
        queryWrapper.orderByDesc("create_time");
        return ResultUtil.data(maoWithdrawalService.page(PageUtil.initPage(page), queryWrapper));
    }
}
