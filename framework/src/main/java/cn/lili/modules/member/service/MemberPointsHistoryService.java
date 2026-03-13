package cn.lili.modules.member.service;

import cn.lili.common.vo.PageVO;
import cn.lili.modules.member.entity.dos.MemberPointsHistory;
import cn.lili.modules.member.entity.vo.MemberPointsHistoryVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 会员积分历史业务层
 *
 * @author Bulbasaur
 * @since 2020-02-25 14:10:16
 */
public interface MemberPointsHistoryService extends IService<MemberPointsHistory> {

    /**
     * 获取会员积分VO
     *
     * @param memberId 会员ID
     * @return 会员积分VO
     */
    MemberPointsHistoryVO getMemberPointsHistoryVO(String memberId);

    /**
     * 会员积分历史
     *
     * @param page       分页
     * @param memberId   会员ID
     * @param memberName 会员名称
     * @return 积分历史分页
     */
    IPage<MemberPointsHistory> MemberPointsHistoryList(PageVO page, String memberId, String memberName);

    /**
     * 获取当前未结算的基金会应拨备金总额 (USD)
     * 
     * @return 总额
     */
    double getUnsettledLiability();

    /**
     * 获取最新的全站积分默克尔根
     * 
     * @return 默克尔根哈希
     */
    String getLatestMerkleRoot();

    /**
     * 处理 DApp 兑换回调
     * 
     * @param memberId 会员 ID
     * @param points   扣除积分
     * @param txHash   链上交易哈希 (必填，作为业务幂等键)
     * @return 是否成功
     */
    boolean pointExchangeCallback(String memberId, Long points, String txHash);

}