package cn.lili.modules.system.entity.dto;

import lombok.Data;

/**
 * Maollar 里程碑增发配置
 */
@Data
public class MaollarMilestoneSetting {

    /**
     * 首单完成后，合约是否已铸造 1 亿枚到兑换池。
     */
    private Boolean initialMinted = false;
    
    /**
     * 当前已触发增发的销售额里程碑阶段 (0=未触发销售里程碑，1=100K，2=200K等)
     */
    private Integer currentMilestoneMinted = 0;
}
