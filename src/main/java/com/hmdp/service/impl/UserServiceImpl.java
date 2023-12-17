package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.dto.Result.ok;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.ServiceConstants.*;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate template;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 检验手机号码是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式错误");
        }
        //2. 生成6位的验证码
        String code = RandomUtil.randomNumbers(6);
        //3. 将验证码保存到redis中
        template.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.将手机号码保存到redis中
        template.opsForValue().set(LOGIN_PHONE_KEY + phone, phone, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //6. 发送验证码
        log.info("手机号码是:{}  验证码是:{}", phone, code);
        return ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 实现登录功能
        //1.校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号码格式不正确");
        }
        String phone = template.opsForValue().get(LOGIN_PHONE_KEY + loginForm.getPhone());
        String code = template.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        //2.检验手机号码是否一致
        if (!phone.equals(loginForm.getPhone())) {
            return Result.fail("两次输入的手机号码不一致");
        }
        //3.校验验证码是否一致
        if (!code.equals(loginForm.getCode())) {
            return Result.fail("验证码输入错误");
        }
        //4.根据手机号码查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        //5.不存在则注册用户
        if (ObjectUtil.isNull(user)) {
            String userName = USER_NAME_PREFIX + RandomUtil.randomString(10);
            user.setNickName(userName);
            user.setPhone(loginForm.getPhone());
            save(user);
        }
        //6.将用户信息保存到redis中
        Map<String, Object> map = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class), new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String token = UUID.randomUUID().toString(true);
        template.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        //设置过期时间
        template.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //删除验证码
        template.delete(LOGIN_CODE_KEY + code);
        return ok(token);
    }

    @Override
    public Result loginOut(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        template.delete(LOGIN_USER_KEY + token);
        return ok();
    }
}
