package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec
     * @return
     */
    boolean tryLook(long timeoutSec);
    /**
     * 释放锁
     */
    void unlock();
}
