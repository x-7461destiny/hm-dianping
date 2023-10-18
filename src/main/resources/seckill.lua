-- 参数列表
-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 数据key
-- 1. 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2. 订单key
local orderKey = 'seckill:order:' .. voucherId
-- 3. 脚本业务
-- 3.1 判断库存是否充足 get stock_key
if (tonumber(redis.call('get',stockKey)) <= 0) then
    -- 库存不足
    return 1
end
-- 3.2 判断用户是否下单  SISMEMBER order_key 判断
if (redis.call('SISMEMBER',orderKey,userId) == 1) then-- 存在 说明重复下单
    return 2
end
-- 3.4扣库存
redis.call('incrby',stockKey,-1)
-- 3.5下单，保存用户
redis.call('sadd',orderKey,userId)

return 0




