-- queue_enter.lua
-- 큐 크기 상한 체크 + 진입을 원자적으로 처리
--
-- KEYS[1] = queue:{scheduleId}  (SortedSet)
-- ARGV[1] = maxQueueSize
-- ARGV[2] = userId
-- ARGV[3] = score (timestamp ms)
--
-- return: 1 = 성공, 0 = 큐 가득 참

local queueKey = KEYS[1]
local maxQueueSize = tonumber(ARGV[1])
local userId = ARGV[2]
local score = tonumber(ARGV[3])

if redis.call('ZCARD', queueKey) >= maxQueueSize then
    return 0
end

redis.call('ZADD', queueKey, score, userId)
return 1
