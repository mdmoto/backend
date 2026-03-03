package cn.lili.modules.member.service;

import cn.lili.modules.member.entity.dos.MaoWithdrawalLog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * $MAO 提现业务层
 */
public interface MaoWithdrawalService extends IService<MaoWithdrawalLog> {

    /**
     * 提现申请
     *
     * @param points              扣除喵币
     * @param solanaWalletAddress 用户钱包地址
     * @return 提现记录
     */
    MaoWithdrawalLog applyWithdrawal(Long points, String solanaWalletAddress);

    /**
     * 执行提现扣款与发币
     *
     * @param withdrawalLogId 提现记录ID
     */
    void executeWithdrawal(String withdrawalLogId);

    /**
     * 获取当日已发放总量
     * 
     * @return 当日累计金额
     */
    /**
     * 获取当日已发放总量
     * 
     * @return 当日累计金额
     */
    double getDailyTotalIssued();

    /**
     * 获取历史发放总量 (用于对账)
     * 
     * @return 总发放金额
     */
    double getTotalIssuedAmount();
}
