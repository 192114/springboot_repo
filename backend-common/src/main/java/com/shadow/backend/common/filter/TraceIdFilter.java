package com.shadow.backend.common.filter;

import com.shadow.backend.common.constant.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(AppConstant.TRACE_ID_HEADER);
        if (traceId == null || !traceId.matches("[A-Za-z0-9_-]{1,32}")) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        MDC.put(AppConstant.TRACE_ID, traceId);
        response.setHeader(AppConstant.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(AppConstant.TRACE_ID);
        }
    }
}
