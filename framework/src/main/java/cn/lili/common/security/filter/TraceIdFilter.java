package cn.lili.common.security.filter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * P2 Observability: TraceId Filter
 * Adds a unique traceId to each request for distributed logging.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Generate or capture trace-id
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }

            // Add to MDC for logging
            MDC.put(TRACE_ID, traceId);

            // Add to response header for troubleshooting
            response.setHeader("X-Trace-Id", traceId);

            filterChain.doFilter(request, response);
        } finally {
            // Clean up to avoid memory leak in thread pool
            MDC.remove(TRACE_ID);
        }
    }
}
