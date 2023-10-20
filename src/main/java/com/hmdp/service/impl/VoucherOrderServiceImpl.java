package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private BlockingQueue<VoucherOrder> order_tasks = new ArrayBlockingQueue<>(1024*1024);
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();


    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列的信息，XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2. 判断消息是否发送成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明没有消息，继续循环
                        continue;
                    }
                    // 3. 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 4. 获取成功，可以下当
                    handlerVoucherOrder(voucherOrder);
                    // 5. ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlerPendingList();
                }
            }
        }

        private void handlerPendingList() {

            while (true) {
                try {
                    // 1.获取pending-list的信息，XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2. 判断消息是否发送成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明pengding-list没有消息，继续循环
                        break;
                    }
                    // 3. 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 4. 获取成功，可以下当
                    handlerVoucherOrder(voucherOrder);
                    // 5. ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {

                    log.error("pending-list 异常");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1. 获取队列中的订单信息
//                    VoucherOrder voucherOrder = order_tasks.take();
//                    // 2. 创建订单
//                    handlerVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long user_id = voucherOrder.getUserId();
//        // 使用redisson创建锁对象
        RLock lock = redissonClient.getLock("lock:order:"+user_id);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //  获取锁失败，
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
        // 使用synchronized 锁如下：
//        synchronized (user_id.toString().intern()) {  // 不必对整个代码加锁，只需要对每个user_id 加锁即可
            // 使用tostring 方法不能保证唯一id，因为toString内部都是new了一个String
            //再使用 intern方法，返回字符串常量值一样的东西
            // 又由于事务可能触发线程安全问题，所以应该等事务处理完后，再释放锁
            // 又由于spring 事务需要拿代理对象，而createVoucherOrder 是this.cre... 是目标对象，不是代理对象，所以没有事务功能
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 拿到代理对象
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }

    }
@PostConstruct
    void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        // 初始化lua脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

        @Override

        public Result seckillVoucher(Long voucherId) {
            // 获取用户id
            Long user_id = UserHolder.getUser().getId();
            // 获取订单id
            long id = RedisIdWorker.nextId("order");
            // 1.执行lua脚本
            Long res_id = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(), user_id.toString(),String.valueOf(id)
            );
            int r = res_id.intValue();
            // 判断结果是否为0
            if (r != 0) {
                // 不为0 没有购买资格
                return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
            }
            //
            long order_id = id;

            // 获取代理对象
            proxy = (IVoucherOrderService) AopContext.currentProxy();
            return Result.ok(order_id);
    }
    private IVoucherOrderService proxy;
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long user_id = UserHolder.getUser().getId();
//        // 1.执行lua脚本
//        Long res_id = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), user_id.toString()
//        );
//        int r = res_id.intValue();
//        // 判断结果是否为0
//        if (r != 0) {
//            // 不为0 没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        //
//        long order_id = RedisIdWorker.nextId("order");
//
//        //2.3 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //生成订单id
//        long orderId = RedisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        // 用户id
//        Long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//        //代金券id
//        voucherOrder.setVoucherId(voucherId);
//        // 放入队列
//        order_tasks.add(voucherOrder);
//        // 获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        return Result.ok(order_id);
//    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1. 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2. 查看秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //2.1 秒杀尚未开始
//            return Result.fail("秒杀尚未开始!");
//        }
//        //3. 查看秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //2.1 秒杀已经结束
//            return Result.fail("秒杀已经结束!");
//        }
//        //4. 判断库存是否充足
//        if (voucher.getStock() <= 0) {
//            //4.1 否，返回结果
//            return Result.fail("库存不足!");
//        }
//        Long user_id = UserHolder.getUser().getId();
//        // 创建自定义锁对象
////        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + user_id);
//        // 使用redisson创建锁对象
//        RLock lock = redissonClient.getLock("lock:order:"+user_id);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            //  获取锁失败，
//            return Result.fail("不允许重复下单");
//        }
//
//        // 使用synchronized 锁如下：
////        synchronized (user_id.toString().intern()) {  // 不必对整个代码加锁，只需要对每个user_id 加锁即可
//            // 使用tostring 方法不能保证唯一id，因为toString内部都是new了一个String
//            //再使用 intern方法，返回字符串常量值一样的东西
//            // 又由于事务可能触发线程安全问题，所以应该等事务处理完后，再释放锁
//            // 又由于spring 事务需要拿代理对象，而createVoucherOrder 是this.cre... 是目标对象，不是代理对象，所以没有事务功能
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 拿到代理对象
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
////        }
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //  一人一单
//        Long user_id = UserHolder.getUser().getId();
        Long user_id = voucherOrder.getUserId();
        Integer count = query().eq("user_id", user_id).eq("voucher_id", voucherOrder).count();
            if (count > 0) {
                // 用户已下单
                log.error("用户已下单");
                return;
            }
            //4.2 是，减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1").eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0) // 更新时候库存为查询时的库存，乐观锁
                    .update();
            if (!success) {
                log.error("库存不足");
                return;
            }

//            //4.3 创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            //生成订单id
//            long orderId = RedisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            // 用户id
//            Long userId = UserHolder.getUser().getId();
//            voucherOrder.setUserId(userId);
//            //代金券id
//            voucherOrder.setVoucherId(voucherOrder);
            //将订单写入数据库
            save(voucherOrder);
            //5. 返回
//            return Result.ok(orderId);
    }
}
