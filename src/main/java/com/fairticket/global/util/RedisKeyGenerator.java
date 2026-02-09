package com.fairticket.global.util;

public final class RedisKeyGenerator {

    private RedisKeyGenerator() {}

    private static final String QUEUE_PREFIX = "queue:";
    private static final String QUEUE_TOKEN_PREFIX = "queue-token:";
    private static final String HEARTBEAT_PREFIX = "heartbeat:";
    private static final String ACTIVE_PREFIX = "active:";

    public static String queue(Long scheduleId) {
        return QUEUE_PREFIX + scheduleId;
    }

    public static String queueToken(Long userId, Long scheduleId) {
        return QUEUE_TOKEN_PREFIX + userId + ":" + scheduleId;
    }

    public static String heartbeat(Long scheduleId, Long userId) {
        return HEARTBEAT_PREFIX + scheduleId + ":" + userId;
    }

    public static String active(Long scheduleId) {
        return ACTIVE_PREFIX + scheduleId;
    }
}
