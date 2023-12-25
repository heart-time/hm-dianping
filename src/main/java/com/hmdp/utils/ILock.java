package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     */
    public boolean tryLock(long timeOut);

    /**
     * 释放锁
     */
    public void unlock();
}
