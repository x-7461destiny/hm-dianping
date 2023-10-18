package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://129.154.200.53:6550").setPassword("9*NJU:^v,p*,1si),Djm");
        //创建redissonClient对象
        return Redisson.create(config);
    }
//    @Bean
//    public RedissonClient redissonClient2() {
//        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://localhost:6379");
//        //创建redissonClient对象
//        return Redisson.create(config);
//    }
//    @Bean
//    public RedissonClient redissonClient3() {
//        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://47.107.53.47:6550").setPassword("www@7251.comiu");
//        //创建redissonClient对象
//        return Redisson.create(config);
//    }


}
