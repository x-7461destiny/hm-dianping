package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Component
public class RedisIdWorker {

    private static StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1697155200L;
    public static final int  MOVE_BITS = 32;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static long nextId(String kePrefix) {
        // 生成时间戳
        long now_second = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeStamp =  now_second - BEGIN_TIMESTAMP ;
        // 2.生成序列号
        // 2.1 获取当前日期，精确到日
//        now.format(DateTimeFormatter.ofPattern("yy:mm:dd")).
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:mm:dd"));
        Long increment = stringRedisTemplate.opsForValue().increment("inc:" + kePrefix + ":" + date);
        //拼接返回
        return increment | timeStamp << MOVE_BITS;
    }

//    public static void main(String[] args) {
//        System.out.println(LocalDateTime.of(2023,10,13,0,0,0).toEpochSecond(ZoneOffset.UTC));
//    }
}
