package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {


    @Resource
    private ShopServiceImpl shopServiceImp;
    @Resource
    private RedisUtils redisUtils;

    @Resource
    private RedisIdWorker worker;

    @Test
    public void test() throws InterruptedException {
        Shop byId = shopServiceImp.getById(1L);
        redisUtils.setLogicalExpireTime(RedisConstants.CACHE_SHOP_KEY + 1, byId, 20L, TimeUnit.SECONDS);

    }

    @Test
    public void test9() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(500);
        CountDownLatch latch = new CountDownLatch(400);
        long start = System.currentTimeMillis();
        for (int i = 0; i < 400; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 300; j++) {
                    long order = worker.generateId("order");
                    System.out.println(order);
                }
                latch.countDown();
            });
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - start);
    }

}
