package com.novabank;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final int CLIENT_LIMIT = 5;
    static final int EDGE_LIMIT = 20;
    static final long WINDOW_MS = 60_000;
    static final int MAX_TRACKED_KEYS = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean limited = "POST".equals(request.getMethod())
                && (path.equals("/auth/login") || path.equals("/auth/signup"));

        if (limited) {
            String[] keys = clientKeys(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
            long now = System.currentTimeMillis();
            evictIfNeeded(now);

            boolean allowed = allow("edge|" + path + "|" + keys[0], EDGE_LIMIT, now)
                    && (keys[1] == null || allow("client|" + path + "|" + keys[1], CLIENT_LIMIT, now));

            if (!allowed) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests, please try again later\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    static String[] clientKeys(String forwardedFor, String remoteAddr) {
        List<String> addresses = new ArrayList<>();
        if (forwardedFor != null) {
            for (String part : forwardedFor.split(",")) {
                String trimmed = part.trim();
                if (trimmed.length() > 64) {
                    trimmed = trimmed.substring(0, 64);
                }
                if (!trimmed.isEmpty()) {
                    addresses.add(trimmed);
                }
            }
        }
        if (addresses.isEmpty()) {
            return new String[] {remoteAddr, null};
        }
        String edge = addresses.get(addresses.size() - 1);
        String client = addresses.size() >= 2 ? addresses.get(addresses.size() - 2) : null;
        return new String[] {edge, client};
    }

    private boolean allow(String key, int limit, long now) {
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private void evictIfNeeded(long now) {
        if (requestLog.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        requestLog.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
        if (requestLog.size() > MAX_TRACKED_KEYS) {
            requestLog.clear();
        }
    }

    private static boolean isStale(Deque<Long> timestamps, long now) {
        Long newest = timestamps.peekLast();
        return newest == null || now - newest > WINDOW_MS;
    }
}