package cn.lili.cache.limit.service;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 运行时限流工具（复用 limit.lua）。
 * <p>
 * 用于需要“按账号/邮箱”等动态维度限流的场景（非纯 IP 维度）。
 */
@Slf4j
@Component
public class RateLimitService {

    private static final String DEFAULT_PREFIX = "limit:";

    private RedisTemplate<String, Serializable> redisTemplate;
    private DefaultRedisScript<Long> limitScript;

    @Autowired
    public void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    public void setLimitScript(DefaultRedisScript<Long> limitScript) {
        this.limitScript = limitScript;
    }

    public void check(String key, int limitCount, int periodSeconds) {
        check(DEFAULT_PREFIX, key, limitCount, periodSeconds);
    }

    public void check(String prefix, String key, int limitCount, int periodSeconds) {
        if (StringUtils.isBlank(key)) {
            throw new ServiceException(ResultCode.ILLEGAL_REQUEST_ERROR);
        }
        String redisKey = StringUtils.join(StringUtils.defaultString(prefix), key);
        ImmutableList<String> keys = ImmutableList.of(redisKey);
        try {
            Number count = redisTemplate.execute(limitScript, keys, limitCount, periodSeconds);
            if (count == null) {
                return;
            }
            if (count.intValue() > limitCount) {
                throw new ServiceException(ResultCode.LIMIT_ERROR);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("动态限流异常, key={}", redisKey, e);
            throw new ServiceException(ResultCode.ERROR);
        }
    }

    /**
     * 生成适合放入 Redis key 的稳定标识，避免直接存明文用户名/邮箱。
     */
    public static String sha256Hex(String value) {
        if (value == null) {
            return "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // 极端情况下退化为长度受限的明文
            return StringUtils.left(value, 64);
        }
    }
}

