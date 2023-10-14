package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2. 查看秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //2.1 秒杀尚未开始
            return Result.fail("秒杀尚未开始!");
        }
        //3. 查看秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //2.1 秒杀已经结束
            return Result.fail("秒杀已经结束!");
        }
        //4. 判断库存是否充足
        if (voucher.getStock() <= 0) {
            //4.1 否，返回结果
            return Result.fail("库存不足!");
        }
        Long user_id = UserHolder.getUser().getId();

        // 使用synchronized 锁如下：
        synchronized (user_id.toString().intern()) {  // 不必对整个代码加锁，只需要对每个user_id 加锁即可
            // 使用tostring 方法不能保证唯一id，因为toString内部都是new了一个String
            //再使用 intern方法，返回字符串常量值一样的东西
            // 又由于事务可能触发线程安全问题，所以应该等事务处理完后，再释放锁
            // 又由于spring 事务需要拿代理对象，而createVoucherOrder 是this.cre... 是目标对象，不是代理对象，所以没有事务功能
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 拿到代理对象
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //  一人一单
        Long user_id = UserHolder.getUser().getId();
            Integer count = query().eq("user_id", user_id).eq("voucher_id", voucherId).count();
            if (count > 0) {
                // 用户已下单
                return Result.fail("用户已下单");
            }
            //4.2 是，减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1").eq("voucher_id", voucherId)
                    .gt("stock", 0) // 更新时候库存为查询时的库存，乐观锁
                    .update();
            if (!success) {
                return Result.fail("库存不足");
            }

            //4.3 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //生成订单id
            long orderId = RedisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 用户id
            Long userId = UserHolder.getUser().getId();
            voucherOrder.setUserId(userId);
            //代金券id
            voucherOrder.setVoucherId(voucherId);
            //将订单写入数据库
            save(voucherOrder);
            //5. 返回
            return Result.ok(orderId);
    }
}
