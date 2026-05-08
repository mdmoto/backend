package cn.lili.modules.system.entity.dto;

import lombok.Data;

/**
 * Maollar 里程碑增发配置
 */
@Data
public class MaollarMilestoneSetting {
    
    /**
     * 当前已触发增发的里程碑阶段 (0=初始1亿，1=100K，2=200K等)
     */
    private Integer currentMilestoneMinted = 0;
}
