package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String keyPrefix = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        // 初始化lua脚本
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLook(long timeoutSec) {
        // 获取线程id
        String threadId = ID_PREFIX+ Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(keyPrefix+name,threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    // 使用lua脚本释放锁

    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(keyPrefix+name)
                ,ID_PREFIX + Thread.currentThread().getId()
                );
    }
    /*
    @Override
    public void unlock() {
        // 获取线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(keyPrefix + name);
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(keyPrefix+name);
        }
    }

     */
}
