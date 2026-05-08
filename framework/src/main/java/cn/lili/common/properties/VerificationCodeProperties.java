package cn.lili.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 线程配置
 *
 * @author Chopper
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lili.verification-code")
public class VerificationCodeProperties {

    /**
     * 是否启用滑块验证码。
     * <p>
     * 关闭后：不再要求通过滑块校验（VerificationService#check 直接放行），
     * 但建议配合接口限流/风控来防止爆破与垃圾请求。
     */
    private boolean enabled = true;


    /**
     * 过期时间
     * 包含滑块验证码有效时间， 以及验证通过之后，缓存中存储的验证结果有效时间
     */
    private Long effectiveTime = 600L;

    /**
     * 水印
     */
    private String watermark = "";
    /**
     * 干扰数量 最大数量
     */
    private Integer interfereNum = 0;

    /**
     * 容错像素
     */
    private Integer faultTolerant = 3;


    public String getWatermark() {
        return watermark;
    }

    public Integer getInterfereNum() {
        if (interfereNum > 2) {
            return 2;
        }
        return interfereNum;
    }
}
