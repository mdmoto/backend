package cn.lili.controller.member;

import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.member.entity.dos.MaoWithdrawalLog;
import cn.lili.modules.member.service.MaoWithdrawalService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家端, $MAO 提现接口
 */
@RestController
@Api(tags = "买家端, $MAO 提现接口")
@RequestMapping("/buyer/member/maollar")
public class MaoWithdrawalBuyerController {

    @Autowired
    private MaoWithdrawalService maoWithdrawalService;

    @ApiOperation(value = "$MAO 提现申请并执行")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "points", value = "提现扣除喵币", required = true, paramType = "query", dataType = "Long"),
            @ApiImplicitParam(name = "solanaWalletAddress", value = "Solana 接收地址", required = true, paramType = "query", dataType = "String")
    })
    @PostMapping("/withdraw")
    public ResultMessage<MaoWithdrawalLog> withdraw(Long points, String solanaWalletAddress) {
        // 1. 提交提现申请 (校验 KYC 与 余额)
        MaoWithdrawalLog withdrawalLog = maoWithdrawalService.applyWithdrawal(points, solanaWalletAddress);

        // 2. 执行发币逻辑 (异步或同步，此处演示同步执行)
        // 在生产环境下，通常建议放入 MQ 队列异步处理，防止区块链网络延迟影响前端
        maoWithdrawalService.executeWithdrawal(withdrawalLog.getId());

        return ResultUtil.data(maoWithdrawalService.getById(withdrawalLog.getId()));
    }
}
