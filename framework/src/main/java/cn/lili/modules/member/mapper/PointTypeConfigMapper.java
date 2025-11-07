package cn.lili.modules.member.mapper;

import cn.lili.modules.member.entity.dos.PointTypeConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 积分类型配置Mapper
 *
 * @author Chopper
 * @since 2025-11-05
 */
public interface PointTypeConfigMapper extends BaseMapper<PointTypeConfig> {

    /**
     * 根据类型代码查询
     *
     * @param typeCode 类型代码
     * @return 积分类型配置
     */
    @Select("SELECT * FROM li_point_type_config WHERE type_code = #{typeCode} AND is_active = 1 LIMIT 1")
    PointTypeConfig selectByTypeCode(@Param("typeCode") String typeCode);
}

