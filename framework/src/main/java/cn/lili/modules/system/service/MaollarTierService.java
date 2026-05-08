package cn.lili.modules.system.service;

import java.util.Map;

/**
 * Maollar 档位积分服务
 * 基于销售额梯度的积分率计算
 */
public interface MaollarTierService {

    /**
     * 获取当前积分赠送比例
     * 
     * @param totalSalesUSD 当前累计销售额 (单位: 美金)
     * @return 1美元对应赠送的喵币数量
     */
    double getCurrentRate(double totalSalesUSD);

    /**
     * 将不同币种金额转为美金
     * 
     * @param amount   金额
     * @param currency 币种 (CNY, JPY, USD等)
     * @return 美金金额
     */
    double convertToUSD(double amount, String currency);

    /**
     * 获取当前基金拨备比例 (基于销售额里程碑逐步降低)
     * 规则: 每达到一个斐波那契里程碑，拨备率降低 0.2%
     * 
     * @param totalSalesUSD 当前累计销售额 (USD)
     * @return 拨备比例 (例如 0.10 表示 10%)
     */
    double getFundRate(double totalSalesUSD);

    /**
     * 计算基金会应拨备金
     * 
     * @param orderAmountUSD 订单金额 (USD)
     * @param totalSalesUSD  当前累计销售额 (USD)，用于确定动态拨备率
     * @return 拨备金额
     */
    double calculateFund(double orderAmountUSD, double totalSalesUSD);

    /**
     * 检查是否达到结算提醒阈值 ($100,000)
     * 
     * @param unsettledLiabilityUSD 当前未结算的总拨备金
     * @return 是否需要提醒管理员
     */
    boolean shouldRemindSettlement(double unsettledLiabilityUSD);

    /**
     * 获取档位状态信息
     * 
     * @param totalSalesUSD 当前累计销售额 (USD)
     * @return 包含当前销售额、所属档位、所属档位剩余额度的 Map
     */
    Map<String, Object> getTierStatus(double totalSalesUSD);

    /**
     * 检查并触发自动增发
     * @param totalSalesUSD 当前平台总销售额
     */
    void checkAndTriggerMilestone(double totalSalesUSD);
}
