package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public interface IShopService extends IService<Shop> {

    Result getShopByType(Integer typeId, Integer current);

    Result getCacheById(Long id);

    Result updateCacheById(Shop shop);
}
