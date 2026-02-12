-- batch_admit.lua
-- 대기열 → 활성 유저 배치 입장 (원자적 처리)
--
-- KEYS[1] = active:{scheduleId}  (SortedSet: score=heartbeat timestamp, member=userId)
-- KEYS[2] = queue:{scheduleId}   (SortedSet: score=진입 timestamp, member=userId)
-- ARGV[1] = maxActiveUsers (500)
-- ARGV[2] = batchSize (100)
-- ARGV[3] = now (timestamp ms)
-- ARGV[4] = heartbeatTimeout (60000 ms)

local activeKey = KEYS[1]
local queueKey = KEYS[2]
local maxActive = tonumber(ARGV[1])
local batchSize = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local timeout = tonumber(ARGV[4])

-- 1. 비활성 유저 정리 (하트비트 타임아웃 초과)
redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now - timeout)

-- 2. 현재 active 수
local currentActive = redis.call('ZCARD', activeKey)

-- 3. 여유 슬롯 계산
local available = maxActive - currentActive
if available <= 0 then
    return cjson.encode({admitted = {}, activeCount = currentActive, queueSize = redis.call('ZCARD', queueKey)})
end

-- 4. 입장 대상 추출 (큐 앞쪽에서)
local toAdmit = math.min(available, batchSize)
local candidates = redis.call('ZRANGE', queueKey, 0, toAdmit - 1)

if #candidates == 0 then
    return cjson.encode({admitted = {}, activeCount = currentActive, queueSize = redis.call('ZCARD', queueKey)})
end

-- 5. 큐 → active 이동 (원자적)
for _, userId in ipairs(candidates) do
    redis.call('ZADD', activeKey, now, userId)
end
redis.call('ZREMRANGEBYRANK', queueKey, 0, #candidates - 1)

return cjson.encode({
    admitted = candidates,
    activeCount = currentActive + #candidates,
    queueSize = redis.call('ZCARD', queueKey)
})
