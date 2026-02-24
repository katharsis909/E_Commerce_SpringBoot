package com.microservices.Monolith_example.DatabaseClearer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DowntimeFilter extends OncePerRequestFilter {

    private final DowntimeLock downtimeLock;

    public DowntimeFilter(DowntimeLock downtimeLock) {
        this.downtimeLock = downtimeLock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (downtimeLock.isInDowntime()) {
            // Block all requests
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                  "error": "SERVICE_UNAVAILABLE",
                  "message": "System is in scheduled downtime. Please try later."
                }
                """);
            return; // ⛔ Do NOT continue to controller
        }

        // Otherwise, continue normally
        filterChain.doFilter(request, response);
    }
}