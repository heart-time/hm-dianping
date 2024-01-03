package com.hmdp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUserInfo(blog);
            queryBlogLiked(blog);
        });
        return Result.ok(records);
    }

    private void queryBlogLiked(Blog blog) {
        String userId = UserHolder.getUser().getId().toString();
        if (userId == null){
            return ;
        }
        //redis的set集合中是否点赞过
        String key = BLOG_LIKED_KEY + blog.getId();
        Double member = stringRedisTemplate.opsForZSet().score(key, userId);
        if (member == null) {
            blog.setIsLike(false);
        } else {
            blog.setIsLike(true);
        }
    }

    private void queryBlogUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (ObjectUtil.isNull(blog)) {
            return Result.fail("博客不存在");
        }
        queryBlogLiked(blog);
        queryBlogUserInfo(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        String userId = UserHolder.getUser().getId().toString();
        //redis的set集合中是否点赞过
        String key = BLOG_LIKED_KEY + id;
        Double member = stringRedisTemplate.opsForZSet().score(key, userId);
        //没有点赞过，点赞数+1，将该用户加入set集合中
        if (ObjectUtil.isNull(member)) {
            // 修改点赞数量
            boolean update = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            if (update) {
                stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
        } else {
            //点赞过，点赞数-1取消点赞，将该用户从set集合中移除

            boolean update = update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            if (update) {
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
        }
        return Result.ok();

    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //根据id获取点赞列表
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 5);
        if (range == null) {
            return Result.ok();
        }
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询点赞列表前五位的用户
        List<UserDTO> lists = userMapper.selectBatchIds(ids).stream().map((user) -> {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setNickName(user.getNickName());
            userDTO.setIcon(user.getIcon());
            return userDTO;
        }).collect(Collectors.toList());
        //返回用户信息
        return Result.ok(lists);
    }
}
