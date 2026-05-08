package cn.lili.modules.openapi.aop;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;

@Aspect
@Component
public class OpenApiRateLimitAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Token Bucket Lua Script
    // KEYS[1]: bucket key
    // ARGV[1]: capacity (max tokens)
    // ARGV[2]: rate (tokens added per second)
    // ARGV[3]: current timestamp
    // ARGV[4]: tokens to request (1)
    private static final String LUA_SCRIPT = 
        "local key = KEYS[1] " +
        "local capacity = tonumber(ARGV[1]) " +
        "local rate = tonumber(ARGV[2]) " +
        "local now = tonumber(ARGV[3]) " +
        "local requested = tonumber(ARGV[4]) " +
        "local fill_time = capacity / rate " +
        "local ttl = math.floor(fill_time * 2) " +
        "local last_tokens = tonumber(redis.call('hget', key, 'tokens')) " +
        "if last_tokens == nil then last_tokens = capacity end " +
        "local last_refreshed = tonumber(redis.call('hget', key, 'timestamp')) " +
        "if last_refreshed == nil then last_refreshed = 0 end " +
        "local delta = math.max(0, now - last_refreshed) " +
        "local filled_tokens = math.min(capacity, last_tokens + (delta * rate)) " +
        "local allowed = filled_tokens >= requested " +
        "local new_tokens = filled_tokens " +
        "if allowed then new_tokens = filled_tokens - requested end " +
        "redis.call('hset', key, 'tokens', new_tokens) " +
        "redis.call('hset', key, 'timestamp', now) " +
        "redis.call('expire', key, ttl) " +
        "return allowed and 1 or 0";

    @Around("@annotation(cn.lili.modules.openapi.aop.OpenApiRateLimit)")
    public Object interceptor(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OpenApiRateLimit rateLimit = method.getAnnotation(OpenApiRateLimit.class);

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String apiKey = request.getHeader("API-KEY");
        if (apiKey == null) {
            apiKey = "unknown";
        }

        String apiName = rateLimit.name();
        
        // 1. Min Limit (Rate per second = minLimit / 60)
        boolean minAllowed = checkTokenBucket(apiKey + ":" + apiName + ":min", rateLimit.minLimit(), (double) rateLimit.minLimit() / 60.0);
        if (!minAllowed) {
            throw new ServiceException(ResultCode.LIMIT_ERROR, "API rate limit exceeded (Per Minute)");
        }

        // 2. Day Limit (Rate per second = dayLimit / 86400)
        boolean dayAllowed = checkTokenBucket(apiKey + ":" + apiName + ":day", rateLimit.dayLimit(), (double) rateLimit.dayLimit() / 86400.0);
        if (!dayAllowed) {
            throw new ServiceException(ResultCode.LIMIT_ERROR, "API rate limit exceeded (Per Day)");
        }

        return joinPoint.proceed();
    }

    private boolean checkTokenBucket(String key, long capacity, double ratePerSecond) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(LUA_SCRIPT);
        redisScript.setResultType(Long.class);

        long now = System.currentTimeMillis() / 1000;
        
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList("openapi_rate_limit:" + key),
                String.valueOf(capacity),
                String.valueOf(ratePerSecond),
                String.valueOf(now),
                "1"
        );

        return result != null && result == 1L;
    }
}
