package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    @Transactional
    public Result seckillVocher(Long voucherId) {
        //1. 查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2. 校验活动是否开始
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("活动未开始！！！");
        }
        //3.校验活动是否结束
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("活动已经结束！！！");
        }
        //4. 校验库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足！！！");
        }

        //6. 查询订单表，查看当前用户是否已经下单当前优惠券
        Long id = UserHolder.getUser().getId();
        //尝试获取锁
        SimpleRedisLock lock = new SimpleRedisLock("order:"+id,stringRedisTemplate);
        boolean flag = lock.tryLock(1200);
        if (!flag){
            return Result.fail("不允许重复下单！！！");
        }
        return createVoucherOrder(voucherId, seckillVoucher);
    }

    private Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        int count = query().eq("voucher_id", voucherId).eq("user_id", UserHolder.getUser().getId()).count();
        if (count>0){
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
