local voucherId=ARGV[1]
local userId=ARGV[2]
local orderId=ARGV[3]

local stockKey='seckill:stock:' .. voucherId
local orderKey='seckill:order:' .. voucherId


if(tonumber(redis.call('get',stockKey))<=0) then
    -- 库存不足，返回1
    return 1
end

if(redis.call('sismember',orderKey,userId)==1) then
    -- 用户已购买，返回2
    return 2
end

-- 扣减库存
redis.call('incrby',stockKey,-1)
-- 记录用户
redis.call('sadd',orderKey,userId)
-- 发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0