package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static  final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeOut) {
        //为了防止业务执行时间过长，导致锁超时被释放，而其他线程又获取到锁，此时当前线程就会把其他线程获取到的锁误删
        //所以就会出现并发安全问题，所以每次删除锁之前需要获取到锁，然后比较锁的标识是否与当前线程的一致，一致才能删除锁
        String val = ID_PREFIX + Thread.currentThread().getId();
        //尝试获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, val, timeOut, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        //获取到锁标识，与当前线程的锁标识是否一致，一直才能删除
      stringRedisTemplate.execute(UNLOCK_SCRIPT,
              Collections.singletonList(KEY_PREFIX + name),
              ID_PREFIX + Thread.currentThread().getId());
    }
//    @Override
//    public void unlock() {
//        //获取到锁标识，与当前线程的锁标识是否一致，一直才能删除
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (id.equals(ID_PREFIX + Thread.currentThread().getId())) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
