-- 锁的id
-- local key = KEYS[1]  --"lock:order:5"
-- 当前线程标识
-- local threadId = ARGV[1]  --"iojfsoidfjsdf-32"
-- 获取锁中线程标识
local id = redis.call('get',KEYS[1])
-- 比较是否一致
if (id == ARGV[1]) then
    -- 释放锁
    return redis.call('del',KEYS[1])
end
return 0