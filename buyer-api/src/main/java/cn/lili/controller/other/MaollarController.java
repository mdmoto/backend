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
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.service.MemberService;

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

    @Autowired
    private MemberService memberService;

    @ApiOperation(value = "获取当前档位状态")
    @GetMapping({"/tier-status", "/stats", "/price"})
    public ResultMessage<Map<String, Object>> getTierStatus() {
        // 基于 Stripe 真实收款数据计算当前档位
        double totalSalesUSD = stripePaymentSnapshotService.getCompletedTotalSalesUSD();
        Map<String, Object> status = maollarTierService.getTierStatus(totalSalesUSD);
        
        // 为 maollar-app 补充前端视图所需字段
        double fundPrice = maollarTierService.getCurrentRate(totalSalesUSD);
        status.put("fundPrice", fundPrice);
        status.put("marketPrice", fundPrice * 1.5); // 模拟二级市场溢价
        status.put("fundPool", totalSalesUSD * maollarTierService.getFundRate(totalSalesUSD));
        status.put("totalSalesUSD", totalSalesUSD);
        status.put("totalSupply", 1000000000.0); // 示例总额
        status.put("circulatingSupply", 500000000.0);
        status.put("totalBurned", 0.0);
        status.put("fundRate", maollarTierService.getFundRate(totalSalesUSD) * 100);
        status.put("currentMilestone", status.get("tier"));
        status.put("maoPriceUSD", fundPrice);
        
        return ResultUtil.data(status);
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
    @GetMapping({"/exchange-list", "/history"})
    public ResultMessage<List<Map<String, Object>>> exchangeList(PageVO page) {
        String memberId = UserContext.getCurrentUser().getId();
        QueryWrapper<MaoWithdrawalLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("member_id", memberId);
        queryWrapper.orderByDesc("create_time");
        
        // 转换字段以匹配 maollar-app 前端
        IPage<MaoWithdrawalLog> dataPage = maoWithdrawalService.page(PageUtil.initPage(page), queryWrapper);
        List<Map<String, Object>> resultList = new java.util.ArrayList<>();
        for (MaoWithdrawalLog logRecord : dataPage.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", logRecord.getId());
            map.put("createdAt", logRecord.getCreateTime());
            map.put("pointsUsed", logRecord.getPoints());
            map.put("meoReceived", logRecord.getMaoIssuedAmount());
            map.put("txHash", logRecord.getMaoTxHash());
            map.put("status", logRecord.getMaoIssueStatus().toLowerCase());
            resultList.add(map);
        }
        return ResultUtil.data(resultList);
    }

    @ApiOperation(value = "绑定提现钱包地址")
    @PostMapping("/wallet/bind")
    public ResultMessage<Object> bindWallet(@RequestBody Map<String, String> params) {
        String address = params.get("address");
        if (cn.hutool.core.util.StrUtil.isBlank(address)) {
            return ResultUtil.error(ResultCode.PARAMS_ERROR);
        }
        String memberId = UserContext.getCurrentUser().getId();
        Member member = memberService.getById(memberId);
        // 这里暂时借用 nickName 或在未来扩展字段，目前为了演示先存入 log 或简单返回成功
        // 实际上建议在 li_member 表增加 solana_wallet 字段
        log.info("用户 {} 尝试绑定钱包地址: {}", memberId, address);
        return ResultUtil.success();
    }

    @ApiOperation(value = "获取绑定的钱包信息")
    @GetMapping("/wallet")
    public ResultMessage<Map<String, Object>> getWallet() {
        Map<String, Object> result = new HashMap<>();
        String memberId = UserContext.getCurrentUser().getId();
        Member member = memberService.getById(memberId);
        // 模拟返回
        result.put("address", ""); 
        return ResultUtil.data(result);
    }

    @ApiOperation(value = "发起积分兑换 $MAO 申请 (JSON版)")
    @PostMapping(value = "/exchange", consumes = "application/json")
    public ResultMessage<MaoWithdrawalLog> exchangeJson(@RequestBody Map<String, Object> params) {
        Long points = Long.valueOf(params.get("amount").toString());
        String solanaWalletAddress = params.get("walletAddress").toString();
        return exchange(points, solanaWalletAddress);
    }
}
