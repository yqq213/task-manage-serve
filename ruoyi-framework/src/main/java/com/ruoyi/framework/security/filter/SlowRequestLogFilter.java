package com.ruoyi.framework.security.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import com.alibaba.druid.pool.DruidDataSource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.sql.DataSource;

/** 在认证过滤器外记录慢请求，区分认证等待与后续处理；不记录请求参数或凭证。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlowRequestLogFilter extends OncePerRequestFilter
{
    private static final Logger log = LoggerFactory.getLogger(SlowRequestLogFilter.class);
    static final String AUTH_LOOKUP_NANOS = SlowRequestLogFilter.class.getName() + ".lookup";
    static final String AUTH_COMPLETE_NANOS = SlowRequestLogFilter.class.getName() + ".complete";

    private final DruidDataSource dataSource;

    @Value("${ruoyi.slow-request-threshold-millis:2000}")
    private long thresholdMillis;

    public SlowRequestLogFilter(@Qualifier("masterDataSource") DataSource dataSource)
    {
        this.dataSource = (DruidDataSource) dataSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        Instant enteredAt = Instant.now();
        long started = System.nanoTime();
        try
        {
            chain.doFilter(request, response);
        }
        finally
        {
            long completed = System.nanoTime();
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(completed - started);
            if (totalMillis >= thresholdMillis)
            {
                Long lookup = (Long) request.getAttribute(AUTH_LOOKUP_NANOS);
                Long authenticated = (Long) request.getAttribute(AUTH_COMPLETE_NANOS);
                // -1 表示请求没有经过 JWT 阶段，例如注销、预检或认证阶段提前异常。
                // 项目文件日志只接收 INFO/ERROR，使用 INFO 确保诊断记录落盘。
                log.info("慢请求 method={} path={} enteredAt={} status={} totalMs={} authLookupMs={} "
                        + "authCompleteMs={} afterAuthMs={} dbActive={} dbIdle={} dbWaiters={}",
                        request.getMethod(), request.getRequestURI(), enteredAt, response.getStatus(), totalMillis,
                        elapsed(started, lookup), elapsed(started, authenticated), elapsed(authenticated, completed),
                        dataSource.getActiveCount(), dataSource.getPoolingCount(), dataSource.getWaitThreadCount());
            }
        }
    }

    /** 使用单调时钟，避免系统校时导致耗时失真。 */
    private long elapsed(Long start, Long end)
    {
        return start == null || end == null ? -1 : TimeUnit.NANOSECONDS.toMillis(end - start);
    }
}
