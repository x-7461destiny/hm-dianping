package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //  互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }
/*
    public Shop queryWithMutex(Long id) {
        // 1. 从商铺查询redis缓存
        String key = CACHE_SHOP_KEY + id;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(cacheShop)) {
            // 3. 存在，返回
            return JSONUtil.toBean(cacheShop, Shop.class);
        }
        if (cacheShop != null) {
            // 由于防止缓存击穿所在redis写的一个空值
            return null;
        }
        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lock_key = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean lock = tryLock(lock_key);
            if (!lock) {
                Thread.sleep(100);
                return queryWithMutex(id);
            }
            // 5. 根据id 查询数据库
            shop = getById(id);
            Thread.sleep(300);
            if (shop == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 6. 不存在，返回404
                return null;
            }
            // 7. 存在，写入redis，
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 8. 返回
            unLock(lock_key);
        }
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从商铺查询redis缓存
        String key =  CACHE_SHOP_KEY + id;
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
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
//        5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回数据
            return shop;
        }
        //5.2 过期，需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //6 判断是否获取锁
        if (lock) {
            // 7. 获取到锁，开启独立线程缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        return shop;
    }

        public Shop queryWithPassThrough(Long id) {
        // 1. 从商铺查询redis缓存
        String key =  CACHE_SHOP_KEY + id;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(cacheShop)) {
            // 3. 存在，返回
            return JSONUtil.toBean(cacheShop,Shop.class);
        }
        if (cacheShop != null) {
            // 由于防止缓存击穿所在redis写的一个空值
            return null;
        }
        // 5. 根据id 查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 6. 不存在，返回404
            return null;
        }
        // 7. 存在，写入redis，
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 8. 返回
        return shop;
    }
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    public void saveShop2Redis(Long id, Long expireSecond) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        // 3. 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

 */
    @Override
    @Transactional
    public Result update_shop(Shop shop) {
        // 1.更新数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不为空");
        }
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
