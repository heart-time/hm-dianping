package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * redis工具类
 */
@Component
public class RedisUtils {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将任意Java对象转换成Json，缓存在redis中，并且设置过期时间
     *
     * @param key
     * @param pojo
     * @param expireTime
     */
    public void set(String key, Object pojo, Long expireTime, TimeUnit unit) {
        String jsonStr = JSONUtil.toJsonStr(pojo);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
        stringRedisTemplate.expire(key, expireTime, unit);
    }

    /**
     * 将任意Java对象转换成Json，缓存在redis中，并且设置逻辑过期时间
     *
     * @param key
     * @param obj
     * @param expireTime
     */
    public void setLogicalExpireTime(String key, Object obj, Long expireTime, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(obj);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime)));
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
    }

    /**
     * 根据指定的key查询缓存，并且反序列化为指定类型，缓存空值解决缓存穿透
     */
    public <R, ID> R queryPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> callBack, Long expireTime, TimeUnit unit) {
        //查询缓存
        String key = prefix + id;
        String s = stringRedisTemplate.opsForValue().get(key);
        //如果查询到非空的数据直接返回
        if (StrUtil.isNotBlank(s)) {
            R r = JSONUtil.toBean(s, type);
            return r;
        }
        //如果查询到空串
        if (s != null) {
            return null;
        }
        //如果没有查询到，则查询数据库，将查询到的结果，加入缓存中，
        R apply = callBack.apply(id);
        if (apply == null) {
            //如果为空，将空串缓存起来，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "");
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(apply), expireTime, unit);
        return apply;
    }

    /**
     * 根据指定的key查询缓存，并且反序列化为指定类型，利用逻辑过期解决缓存击穿
     */
    public <R, ID> R queryWithLogicalExpireTime(String prefix, ID id, Class<R> type,Function<ID,R> callBack,Long expireTime,TimeUnit unit) {
        //查询缓存，未命中直接返回
        String key = prefix + id;
        String s = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(s)) {
            return null;
        }
        //命中，查看是否过期，没有过期直接返回值
        RedisData bean = JSONUtil.toBean(s, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) bean.getData(), type);;
        if (LocalDateTime.now().isBefore(bean.getExpireTime())) {

            return r;
        }
        //过期，则尝试获取锁
        boolean flag = tryLock(id);
        //获取锁失败返回旧值
        if (!flag) {
            return r;
        }
        //获取锁成功，则开启新的线程，查询数据库，将查询到的值加入缓存，返回旧值
        getExecutors().submit(()->{
            try {
                R apply = callBack.apply(id);
                Thread.sleep(200);
                this.setLogicalExpireTime(key,apply,expireTime,unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unlock(id);
            }
        });
       return r;
    }

    private <ID> void unlock(ID id) {
        stringRedisTemplate.delete(LOCK_SHOP_KEY+id);
    }


    private ExecutorService getExecutors(){
        return Executors.newFixedThreadPool(10);
    }
    private <ID> boolean tryLock(ID id) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }


}
