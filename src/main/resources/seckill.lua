--1、获取需要的参数
--1.1优惠券id
local seckillId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]

--2 获取需要的key
--2.1 优惠券库存的key
local seckillKey = 'seckill:stock:' .. seckillId
-- 2.2 优惠券下单用户集合的key
local userKey = 'seckill:user:' .. seckillId

--3 判断用户是否有下单优惠券的资格
--3.1 当优惠券的库存小于0时，返回1
if (tonumber(redis.call('get',seckillKey))  <= 0) then
    return 1
end

--3.2 查询该用户是否下单
if (redis.call("sismember", userKey,userId) == 1) then
    return 2;
end

--3.3 扣减库存
redis.call("incrby",seckillKey,-1)
--3.4将用户添加到已经下单的用户集合中
redis.call("sadd",userKey,userId)
return 0