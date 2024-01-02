package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig1 {
    @Bean
    public RedissonClient redissonConfig(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.111.111:6379");
        return Redisson.create(config);
    }
}
