package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        //1、查询缓存是否有数据
       String typeListFromJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_LIST);
        if(!StrUtil.isBlank(typeListFromJson)){
            List<ShopType> typeList = JSONUtil.toList(typeListFromJson,ShopType.class);
            log.info("缓存中的商店数据是:{}",typeList);
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_LIST,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            log.info("刷新缓存的时间为{}分钟", RedisConstants.CACHE_SHOP_TTL);
            return Result.ok(typeList);
        }
        //3、缓存没有数据查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //4、查询不到数据抛出数据不存在异常
        if(ObjectUtils.isEmpty(typeList)){
            return Result.fail("数据不存在");
        }
        String typeListJson = JSONUtil.toJsonStr(typeList);
        //5、数据存在将数据返回，并将查询到的数据加入到缓存中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_LIST,typeListJson);
        //设置缓存的过期时间
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_LIST,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        log.info("设置店铺种类的缓存的时间为{}分钟", RedisConstants.CACHE_SHOP_TTL);
        return Result.ok(typeList);
    }
}
