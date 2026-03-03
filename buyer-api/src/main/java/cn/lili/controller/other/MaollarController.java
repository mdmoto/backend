package cn.lili.controller.other;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.member.service.MemberPointsHistoryService;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.system.service.MaollarTierService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import cn.lili.modules.system.service.ExchangeRateProvider;
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
@RestController
@Api(tags = "Maollar DApp 集成接口")
@RequestMapping("/api/v1/maollar")
public class MaollarController {

    @Autowired
    private MaollarTierService maollarTierService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberPointsHistoryService memberPointsHistoryService;

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    @Autowired
    private MaoWithdrawalService maoWithdrawalService;

    @ApiOperation(value = "获取当前档位状态")
    @GetMapping("/tier-status")
    public ResultMessage<Map<String, Object>> getTierStatus() {
        double totalSalesCNY = orderService.getCompletedTotalSales();
        double totalSalesUSD = maollarTierService.convertToUSD(totalSalesCNY, "CNY");
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
            @ApiImplicitParam(name = "points", value = "兑换扣除积分", required = true, paramType = "query")
    })
    @PostMapping("/exchange-log")
    public ResultMessage<Object> exchangeLog(String memberId, Long points) {
        if (memberPointsHistoryService.pointExchangeCallback(memberId, points)) {
            return ResultUtil.success();
        } else {
            return ResultUtil.error(ResultCode.POINT_NOT_ENOUGH);
        }
    }

    @ApiOperation(value = "获取当前汇率列表")
    @GetMapping("/rates")
    public ResultMessage<Map<String, Object>> getRates() {
        Map<String, Object> rates = new HashMap<>();
        rates.put("CNY", exchangeRateProvider.getRateToUsd("CNY"));
        rates.put("JPY", exchangeRateProvider.getRateToUsd("JPY"));
        rates.put("USD", 1.0);
        return ResultUtil.data(rates);
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
