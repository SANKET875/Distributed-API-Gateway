local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate_per_second = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local current_tokens = tonumber(bucket[1])
local last_refill_time = tonumber(bucket[2])

if current_tokens == nil then
    current_tokens = capacity
    last_refill_time = now
else
    local elapsed_time = now - last_refill_time
    if elapsed_time > 0 then
        local tokens_to_add = (elapsed_time / 1000.0) * refill_rate_per_second
        current_tokens = math.min(capacity, current_tokens + tokens_to_add)
        last_refill_time = now
    end
end

local ttl_seconds = 600

if current_tokens >= 1.0 then
    current_tokens = current_tokens - 1.0
    redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
    redis.call('EXPIRE', key, ttl_seconds)
    return 1
else
    redis.call('HMSET', key, 'last_refill_time', last_refill_time)
    redis.call('EXPIRE', key, ttl_seconds)
    return 0
end