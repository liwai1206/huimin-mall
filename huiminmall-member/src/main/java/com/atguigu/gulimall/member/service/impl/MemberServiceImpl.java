package com.atguigu.gulimall.member.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.gulimall.member.entity.MemberLevelEntity;
import com.atguigu.gulimall.member.exception.PhoneException;
import com.atguigu.gulimall.member.exception.UsernameException;
import com.atguigu.gulimall.member.service.MemberLevelService;
import com.atguigu.gulimall.member.vo.SocialUser;
import com.atguigu.gulimall.member.vo.UserLoginVo;
import com.atguigu.gulimall.member.vo.UserRegisterVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.member.dao.MemberDao;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;


@Slf4j
@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    @Autowired
    private MemberLevelService memberLevelService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageUtils(page);
    }


    /**
     * 会员注册
     * @param vo
     */
    @Override
    public void register(UserRegisterVo vo) {
        // 添加一个MemberEntity
        MemberEntity memberEntity = new MemberEntity();

        // 先判断phone的唯一性
        checkPhoneUnique( vo.getPhone() );
        // 先判断userName的唯一性
        checkUserNameUnique( vo.getUserName() );

        memberEntity.setMobile( vo.getPhone() );
        memberEntity.setUsername( vo.getUserName() );
        memberEntity.setNickname( vo.getUserName() );

        // 设置默认等级
        MemberLevelEntity levelEntity =  memberLevelService.getDefaultLevel();
        memberEntity.setLevelId( levelEntity.getId() );

        // todo: 先对密码进行带盐值的MD5加密，然后封装
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(vo.getPassword());
        memberEntity.setPassword( encodedPassword );

        // 设置默认值
        memberEntity.setGender(0);
        memberEntity.setCreateTime( new Date());

        // 保存数据
        baseMapper.insert( memberEntity );


    }

    /**
     * 登录
     * @param vo
     */
    @Override
    public MemberEntity login(UserLoginVo vo) {
        String loginAcct = vo.getLoginAcct();
        String password = vo.getPassword();

        // 首先根据loginAcct查找member
        MemberEntity memberEntity = baseMapper.selectOne(new QueryWrapper<MemberEntity>().eq("username", loginAcct)
                                                        .or().eq("mobile", loginAcct));
        if ( memberEntity == null ){
            // 用户不存在
            return null;
        }else {
            // 验证带盐值的密码
            String passwordDB = memberEntity.getPassword();
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            boolean existed = passwordEncoder.matches(password, passwordDB);
            if ( existed ){
                return memberEntity;
            }else {
                return null;
            }
        }
    }


    /**
     * 微博社交登录
     * @param socialUser
     * @return
     */
    @Override
    public MemberEntity oauthLogin(SocialUser socialUser) throws Exception {

        MemberEntity memberEntity = baseMapper.selectOne(new QueryWrapper<MemberEntity>().eq("social_uid", socialUser.getUid()));
        if ( memberEntity == null ){
            // 第一次扫描，表示这是一次注册
            // 1.发送请求获取微博用户的信息
            //https://api.weibo.com/2/users/show.json
            Map<String, String> map = new HashMap<>();
            map.put("access_token", socialUser.getAccess_token());
            map.put("uid", socialUser.getUid());
            HttpResponse response = HttpUtils.doGet("https://api.weibo.com", "/2/users/show.json", "get", new HashMap<String, String>(), map);
            log.info(  response.getStatusLine().getStatusCode() + "--" + response.getStatusLine().getReasonPhrase() );
            if ( response.getStatusLine().getStatusCode() == 200 ){
                // 获取信息成功
                String json = EntityUtils.toString(response.getEntity());
                JSONObject jsonObject = JSON.parseObject(json);
                String name = jsonObject.getString("name");
                String gender = jsonObject.getString("gender");
                String profileImageUrl = jsonObject.getString("profile_image_url");

                memberEntity = new MemberEntity();
                memberEntity.setNickname( name );
                memberEntity.setGender("m".equals(gender)?1:0);
                memberEntity.setHeader( profileImageUrl );
                memberEntity.setCreateTime( new Date());
                memberEntity.setSocialUid( socialUser.getUid() );
                memberEntity.setAccessToken( socialUser.getAccess_token());
                memberEntity.setExpiresIn(socialUser.getExpires_in());

                // 2.添加到数据库
                baseMapper.insert( memberEntity );
            }
        }else {
            // 这是一次登录，需要更新相关信息
            MemberEntity updateMemberEntity = new MemberEntity();
            updateMemberEntity.setId(memberEntity.getId());
            updateMemberEntity.setAccessToken(socialUser.getAccess_token());
            updateMemberEntity.setExpiresIn( socialUser.getExpires_in());
            baseMapper.updateById( updateMemberEntity );

            memberEntity.setAccessToken(socialUser.getAccess_token());
            memberEntity.setExpiresIn( socialUser.getExpires_in());
        }

        return memberEntity;
    }

    /**
     * 验证电话号码是否唯一
     * @param phone
     * @throws PhoneException
     */
    private void checkPhoneUnique(String phone) throws PhoneException{
        Integer count = baseMapper.selectCount(new QueryWrapper<MemberEntity>().eq("mobile", phone));
        if ( count > 0 ){
            throw new PhoneException();
        }
    }

    /**
     * 验证用户名是否唯一
     * @param userName
     * @throws UsernameException
     */
    private void checkUserNameUnique(String userName) throws UsernameException {
        Integer count = baseMapper.selectCount(new QueryWrapper<MemberEntity>().eq("username", userName));
        if ( count > 0 ){
            throw new UsernameException();
        }
    }

}