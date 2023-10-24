package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 3. 如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到 redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute("code",code);

        // 5. 发送验证码, 真实情况需要阿里云提供的短信服务之类的
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        //2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
//        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3. 不一致报错
            return Result.fail("验证码错误！");
        }
        //4. 一致根据手机号查用户
        User user = query().eq("phone", phone).one();
        //5. 判断用户是否存在
        if (user == null) {
            //6. 创建新用户，保存
            user = createUserWithPhone(phone);
        }
        // 6保存用户信息到redis
        // 6.1 生成随机token，作为登录令牌
        String token = UUID.randomUUID().toString(false);
        // 6.2 将User对象转化为 HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString())
                );
        // 6.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        // 6.4 设置有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY +userId + keySuffix;

        int day = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,day-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY +userId + keySuffix;

        int day = now.getDayOfMonth();
        //
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(day))
                        .valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num ==0) {
            return Result.ok(0);
        }
        int count = 0;
        while ((num & 1) > 0) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName( USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
//        利用mybatis plus 保存用户
        save(user);
        return user;
    }
}
