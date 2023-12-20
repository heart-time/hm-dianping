package com.hmdp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import sun.swing.StringUIClientPropertyKey;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Override
    public Result getShopByType(Integer typeId, Integer current) {
        //1、根据商店类型查询缓存是否存在数据
        String shopList = stringRedisTemplate.opsForValue().get(RedisConstants.LOCK_SHOP_KEY + typeId);
        //2、缓存存在数据直接返回
        if (StrUtil.isNotBlank(shopList)) {
            List<Shop> shops = JSONUtil.toList(shopList,  Shop.class);
            //刷新缓存时间
            stringRedisTemplate.expire(RedisConstants.LOCK_SHOP_KEY + typeId, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
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
        stringRedisTemplate.opsForValue().set(RedisConstants.LOCK_SHOP_KEY + typeId, JSONUtil.toJsonStr(page.getRecords()));
        stringRedisTemplate.expire(RedisConstants.LOCK_SHOP_KEY + typeId, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回数据
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getCacheById(Long id) {
        //1、根据id查询具体商铺的详情
        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、查询到则直接返回
        if(!StrUtil.isBlank(s)){
            Shop shop = JSONUtil.toBean(s, Shop.class);
            //更新缓存的时间
            stringRedisTemplate.expire(CACHE_SHOP_KEY+id,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
            return Result.ok(shop);
        }
        //3、查询不到则查询数据库
        Shop byId = getById(id);
        //4、数据查询到加入缓存中（设置过期时间为30分钟）、并且将数据返回
        //5、查询不到则报错
        if (ObjectUtil.isNull(byId)){
            return Result.fail("店铺信息不存在");
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(byId));
        stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return Result.ok(byId);
    }

    @Override
    public Result updateCacheById(Shop shop) {
        if (ObjectUtil.isNull(shop)) {
        return Result.fail("更新的商铺信息为空");
        }
        //更新策略先更新数据库在删除缓存
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return  Result.ok();

    }
}
