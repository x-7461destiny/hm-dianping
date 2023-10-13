package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryType() {
        // 1. 从redis查询list-type
        String key =  CACHE_SHOP_TYPE ;
        String cache_shop_list = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(cache_shop_list)) {
            // 3. 存在， 返回
//            List<ShopType> shop_types = JSONUtil.toList(cache_shop_list,ShopType.class);
            List<ShopType> shopTypes = JSONUtil.toList(new JSONArray(cache_shop_list), ShopType.class);
            return Result.ok(shopTypes);
        }
        // 4. 从数据库拿到所有类型
        List<ShopType> shop_types = query().orderByAsc("sort").list();
        if (shop_types == null) {
            // 5. 不存在，
            return Result.fail("暂无分类");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop_types));
        return Result.ok(shop_types);
    }
}
