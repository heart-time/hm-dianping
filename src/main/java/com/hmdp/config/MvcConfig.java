package com.hmdp.config;

import com.hmdp.interceptor.MvcInterceptor;
import com.hmdp.interceptor.RefreshRedisInterceptor;
import com.sun.org.apache.xpath.internal.operations.String;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MvcInterceptor())
                .excludePathPatterns("/user/login"
                        , "/user/code",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**",
                        "/blog/**").order(1);
        registry.addInterceptor(new RefreshRedisInterceptor(stringRedisTemplate)).order(0);
    }
}
