package com.hmdp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Resource
    private RedisUtils redisUtils;
    @Override
    public Result getShopByType(Integer typeId, Integer current) {
        //1、根据商店类型查询缓存是否存在数据
        String shopList = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY + typeId);
        //2、缓存存在数据直接返回
        if (StrUtil.isNotBlank(shopList)) {
            List<Shop> shops = JSONUtil.toList(shopList, Shop.class);
            //刷新缓存时间
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY + typeId, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shops);
        }
        //3、缓存不存在数据查询数据库
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        //4、数据库不存在抛出数据不存在异常
        if (ObjectUtil.isEmpty(page.getRecords())) {
            ShopType shopType = shopTypeMapper.selectById(typeId);
            log.info("商店类型为{}的商铺不存在", shopType.getName());
            return Result.fail("不存在对应的商店");
        }
        //5、查询到数据加入缓存中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY + typeId, JSONUtil.toJsonStr(page.getRecords()));
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY + typeId, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回数据
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getCacheById(Long id) {
        //缓存击穿
//        Shop shop = redisUtils.queryPassThrough(CACHE_SHOP_KEY, id, Shop.class, id1 -> getById(id), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //缓存穿透
        Shop shop = redisUtils.queryWithLogicalExpireTime(CACHE_SHOP_KEY, id, Shop.class, id1 -> getById(id), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    private ExecutorService getExcutorService(){
        return Executors.newFixedThreadPool(10);
    }
    /**
     * 逻辑过期·处理缓存穿透
     *
     * @param id
     * @return
     */
//    private Result queryWithLogicExpire(Long id) {
//        //1、根据id查询具体商铺的详情
//        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //2、查询不到则直接返回
//        if (StrUtil.isBlank(s)) {
//            return Result.ok();
//        }
//        //3 缓存是否过期
//        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //3.1 未过期直接返回商店信息
//        if (!LocalDateTime.now().isAfter(redisData.getExpireTime())){
//            return Result.ok(shop);
//        }
//        //3.2 过期
//        //3.2.1 尝试获取锁
//        boolean flag = tryLock(id);
//        if (!flag){
//            //3.2.1.1 获取锁失败，返回旧数据
//            return Result.ok(shop);
//        }
//        //3.2.1.2 获取锁成功，
//
//        //4 开启一个线程，返回旧数据
//        getExcutorService().submit(()->{
//            //将数据库的值写入redis并且设置过期时间
//            try {
//                saveShop2Redis(id,3600L*30L);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            } finally {
//                unLock(id);
//            }
//            //释放互斥锁
//        });
//        return Result.ok(shop);
//
//    }

//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        //查询商铺信息
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        //创建RedisData实体类
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        //设置RedisData实体类的过逻辑期时间
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //将RedisData加入缓存中
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//
//    }

    /**
     * 加锁处理逻辑过期
     *
     * @param id
     * @return
     */
//    private Result getResultMutex(Long id) {
//        //1、根据id查询具体商铺的详情
//        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //2、查询到则直接返回
//        if (!StrUtil.isBlank(s)) {
//            Shop shop = JSONUtil.toBean(s, Shop.class);
//            //更新缓存的时间
//            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//            return Result.ok(shop);
//        }
//
//        //3、查询不到,则获取锁查询数据库，将数据加入缓存中
//        //获取不到锁，休眠一段时间重新尝试获取锁
//        Shop byId = null;
//        try {
//            if (!tryLock(id)) {
//                Thread.sleep(50);
//                return getResultMutex(id);
//
//            }
//            byId = getById(id);
//            Thread.sleep(500);
//            //4、数据查询到加入缓存中（设置过期时间为30分钟）、并且将数据返回
//            if (byId == null) {
//                return Result.fail("店铺信息不存在");
//            }
//            String s1 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//            if (StrUtil.isBlank(s1)) {
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(byId));
//                stringRedisTemplate.expire(CACHE_SHOP_KEY + id, LOCK_SHOP_TTL, TimeUnit.SECONDS);
//            }
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unLock(id);
//        }
//        return Result.ok(byId);
//    }

//    private boolean tryLock(Long id) {
//
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(Long id) {
//        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
//    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return
     */
    private Result getResultPassOver(Long id) {
        //1、根据id查询具体商铺的详情
        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、查询到则直接返回
        if (!StrUtil.isBlank(s)) {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            //更新缓存的时间
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        }
        if (s != null) {
            return Result.ok("店铺信息不存在");
        }
        //3、查询不到则查询数据库
        Shop byId = getById(id);
        //4、数据查询到加入缓存中（设置过期时间为30分钟）、并且将数据返回
        //5、查询不到则报错
        if (ObjectUtil.isNull(byId)) {
            //如果商铺信息不存在，则将空对象缓存起来，防止缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "");
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺信息不存在");
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(byId));
        stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(byId);
    }


    @Override
    public Result updateCacheById(Shop shop) {
        if (ObjectUtil.isNull(shop)) {
            return Result.fail("更新的商铺信息为空");
        }
        //更新策略先更新数据库在删除缓存
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();

    }
}
