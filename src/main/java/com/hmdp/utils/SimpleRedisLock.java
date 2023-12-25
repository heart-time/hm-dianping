package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements  ILock{

   private static  final String key_prefix = "lock:";
   private String name ;
   private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeOut) {
        String val = Thread.currentThread().getId()+"";
        //尝试获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key_prefix + name, val, timeOut, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
   stringRedisTemplate.delete(key_prefix+name);
    }
}
