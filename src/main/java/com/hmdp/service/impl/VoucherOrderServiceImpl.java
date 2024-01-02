package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker worker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> defaultRedisScript;

    static {
        defaultRedisScript = new DefaultRedisScript<>();
        defaultRedisScript.setResultType(Long.class);
        defaultRedisScript.setLocation(new ClassPathResource("seckill.lua"));
    }

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Result seckillVocher(Long voucherId) {
        //1、执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long execute = stringRedisTemplate.execute(defaultRedisScript,
                Collections.emptyList()
                , voucherId.toString()
                , userId.toString());
        //2、返回1、2说明用户不具备下单资格
        if (execute.intValue() != 0){
            return Result.fail(execute.intValue() == 1 ?"库存不足":"不允许重复下单");
        }
        long orderId = worker.generateId("order");
        //3、返回0将订单id、用户id加入阻塞队列
        //4返回订单id
        return Result.ok(orderId);
    }


//    @Override
//    @Transactional
//    public Result seckillVocher(Long voucherId) {
//        //1. 查询优惠券信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2. 校验活动是否开始
//        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
//            return Result.fail("活动未开始！！！");
//        }
//        //3.校验活动是否结束
//        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
//            return Result.fail("活动已经结束！！！");
//        }
//        //4. 校验库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足！！！");
//        }
//
//        //6. 查询订单表，查看当前用户是否已经下单当前优惠券
//        Long id = UserHolder.getUser().getId();
//        //尝试获取锁
////        SimpleRedisLock lock = new SimpleRedisLock("order:"+id,stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:"+id);
//        boolean flag = lock.tryLock();
//        if (!flag){
//            return Result.fail("不允许重复下单！！！");
//        }
//        if (flag){
//            try {
//                return createVoucherOrder(voucherId, seckillVoucher);
//            } finally {
//                lock.unlock();
//            }
//        }
//        return  null;
//    }

    private Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        int count = query().eq("voucher_id", voucherId).eq("user_id", UserHolder.getUser().getId()).count();
        if (count > 0) {
            return Result.fail("用户已经下单优惠券");
        }
        //5. 扣优惠券库存
        seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        boolean update = seckillVoucherService.update().setSql("stock = stock-1").eq("voucher_id", voucherId).gt("stock", 0).update();

        if (!update) {
            return Result.fail("库存不足！！！");
        }
        //7. 下订单，将订单信息插入数据库中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(worker.generateId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
