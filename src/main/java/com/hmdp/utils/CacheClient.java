package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
//    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 将封装好的data写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }


    public <R,ID> R queryWithPassThrough(String keyPrefix,
                                         ID id, Class<R> type, Function<ID,R> dbFallBack,Long time, TimeUnit unit) {
        // 1. 从商铺查询redis缓存
        String key =  keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3. 存在，返回
            return JSONUtil.toBean(json,type);
        }
        if (json != null) {
            // 由于防止缓存击穿所在redis写的一个空值
            return null;
        }
        // 5. 根据id 查询数据库
        R r = dbFallBack.apply(id);
        if (r == null) {
            // 将空值写入redis
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            set(key,"",time,unit);
            // 6. 不存在，返回404
            return null;
        }
        // 7. 存在，写入redis，
        this.set(key,r,time,unit);
        // 8. 返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R,ID>R queryWithLogicalExpire(String keyPrefix, ID id,
                                          Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit) {
        // 1. 从对象查询redis缓存
        String key =  keyPrefix + id;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isBlank(cacheShop)) {
            // 3. 不存在，返回空
            return null;
        }
//        if (cacheShop != null) {
//            // 由于防止缓存击穿所在redis写的一个空值
//            return null;
//        }
        // 4. 命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(cacheShop, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
//        5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回数据
            return r;
        }
        // TODO 5.2 过期，需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //6 判断是否获取锁
        if (lock) {
            // 7. 获取到锁，开启独立线程缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查数据库
                    R r1 = dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        return r;
    }

    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
