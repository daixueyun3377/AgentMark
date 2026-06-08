package io.github.daixueyun3377.agentmark.core.agent;

import io.github.daixueyun3377.agentmark.core.model.ChatResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 sessionId 管理多轮对话会话。
 *
 * <p>该实现为进程内内存管理，适合单实例应用或快速集成。多实例部署时应由应用层
 * 使用 Redis、数据库或粘性会话等方案持久化并路由会话。</p>
 */
public class AgentMarkSessionManager {

    public static final long DEFAULT_TTL_MILLIS = 30 * 60 * 1000L;
    public static final int DEFAULT_MAX_SESSIONS = 1000;

    private final AgentMarkAgent agent;
    private final long ttlMillis;
    private final int maxSessions;
    private final ConcurrentMap<String, SessionHolder> sessions = new ConcurrentHashMap<>();

    public AgentMarkSessionManager(AgentMarkAgent agent) {
        this(agent, DEFAULT_TTL_MILLIS, DEFAULT_MAX_SESSIONS);
    }

    public AgentMarkSessionManager(AgentMarkAgent agent, long ttlMillis, int maxSessions) {
        if (agent == null) {
            throw new IllegalArgumentException("agent must not be null");
        }
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("maxSessions must be greater than 0");
        }
        this.agent = agent;
        this.ttlMillis = ttlMillis;
        this.maxSessions = maxSessions;
    }

    /**
     * 创建新的会话并返回 sessionId。
     */
    public String createSession() {
        cleanupExpiredSessions();
        ensureCapacity();

        String sessionId;
        do {
            sessionId = generateSessionId();
        } while (sessions.putIfAbsent(sessionId, new SessionHolder(agent.newSession())) != null);
        return sessionId;
    }

    /**
     * 使用指定 sessionId 发送消息。sessionId 不存在或已过期时会创建新会话。
     */
    public ChatResult chat(String sessionId, String userMessage) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be empty");
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("userMessage must not be empty");
        }

        SessionHolder holder = getOrCreateSession(sessionId.trim());
        synchronized (holder) {
            holder.active = true;
            try {
                holder.touch();
                return holder.session.chat(userMessage);
            } finally {
                holder.touch();
                holder.active = false;
            }
        }
    }

    /**
     * 删除指定会话。
     */
    public boolean clear(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        return sessions.remove(sessionId.trim()) != null;
    }

    /**
     * 删除所有会话。
     */
    public void clearAll() {
        sessions.clear();
    }

    /**
     * 清理已过期会话。
     */
    public void cleanupExpiredSessions() {
        if (ttlMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, SessionHolder> entry : sessions.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                sessions.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 当前内存中的会话数量。
     */
    public int size() {
        return sessions.size();
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    private SessionHolder getOrCreateSession(String sessionId) {
        while (true) {
            long now = System.currentTimeMillis();
            SessionHolder existing = sessions.get(sessionId);
            if (existing != null && !isExpired(existing, now)) {
                return existing;
            }

            cleanupExpiredSessions();
            SessionHolder created = new SessionHolder(agent.newSession());
            if (existing == null) {
                ensureCapacity();
                SessionHolder previous = sessions.putIfAbsent(sessionId, created);
                if (previous == null) {
                    return created;
                }
            } else if (sessions.replace(sessionId, existing, created)) {
                return created;
            }
        }
    }

    private void ensureCapacity() {
        if (sessions.size() >= maxSessions) {
            cleanupExpiredSessions();
        }
        if (sessions.size() >= maxSessions) {
            throw new IllegalStateException("AgentMark session limit exceeded: " + maxSessions);
        }
    }

    private boolean isExpired(SessionHolder holder, long now) {
        return ttlMillis > 0 && !holder.active && now - holder.lastAccessAt > ttlMillis;
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    private static class SessionHolder {
        private final AgentMarkSession session;
        private volatile long lastAccessAt;
        private volatile boolean active;

        SessionHolder(AgentMarkSession session) {
            this.session = session;
            touch();
        }

        void touch() {
            this.lastAccessAt = System.currentTimeMillis();
        }
    }
}
