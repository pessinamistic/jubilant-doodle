package com.dbdeployer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class ApiRequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "api.request.startNanos";

    @Value("${dbdeployer.logging.api.verbose:false}")
    private boolean verbose;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();

        if (verbose) {
            log.info(
                    "[api:req] method={} path={} query={} remote={} userAgent={}",
                    method,
                    uri,
                    query != null ? query : "",
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        } else {
            log.info("[api:req] {} {}", method, uri);
        }

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long durationMs = resolveDurationMs(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        if (ex != null) {
            log.warn(
                    "[api:res] {} {} status={} durationMs={} errorType={} message={}",
                    method,
                    uri,
                    status,
                    durationMs,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return;
        }

        if (verbose) {
            log.info("[api:res] method={} path={} status={} durationMs={}", method, uri, status, durationMs);
        } else {
            log.info("[api:res] {} {} -> {} ({} ms)", method, uri, status, durationMs);
        }
    }

    private long resolveDurationMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_TIME_ATTR);
        if (!(start instanceof Long startNanos)) {
            return -1L;
        }
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
