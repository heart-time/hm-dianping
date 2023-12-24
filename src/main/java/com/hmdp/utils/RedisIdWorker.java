package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 生成全局唯一Id
 */
@Component
public class RedisIdWorker {
    private  static  final  long BEGIN_TIMESTAMP = 1672531200L;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
   public long generateId(String prefix){
       //生成时间戳
       LocalDateTime now = LocalDateTime.now();
       long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
       long timeStamp = epochSecond - BEGIN_TIMESTAMP ;
       //生成序列号
       String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
       long count = stringRedisTemplate.opsForValue().increment("incr:" + prefix + ":" + dateTime);
       //将时间戳左移32位，然后与count作或运算，得到全局唯一的id
       return timeStamp << 32 | count;
   }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println( LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));

    }


}
