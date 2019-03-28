package com.supermarket.service.impl;

import com.github.pagehelper.PageHelper;
import com.supermarket.dao.UmsAdminPermissionRelationDao;
import com.supermarket.dao.UmsAdminRoleRelationDao;
import com.supermarket.dto.UmsAdminParam;
import com.supermarket.mapper.UmsAdminLoginLogMapper;
import com.supermarket.mapper.UmsAdminMapper;
import com.supermarket.mapper.UmsAdminPermissionRelationMapper;
import com.supermarket.mapper.UmsAdminRoleRelationMapper;
import com.supermarket.model.*;
import com.supermarket.service.UmsAdminService;
import com.supermarket.util.JwtTokenUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.encoding.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0 created by chenyichang_fh on 2019/3/22 15:51
 */
@Service
public class UmsAdminServiceImpl implements UmsAdminService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UmsAdminMapper umsAdminMapper;

    @Autowired
    private UmsAdminRoleRelationMapper adminRoleRelationMapper;

    @Autowired
    private UmsAdminRoleRelationDao adminRoleRelationDao;

    @Autowired
    private UmsAdminPermissionRelationMapper adminPermissionRelationMapper;

    @Autowired
    private UmsAdminPermissionRelationDao adminPermissionRelationDao;

    @Autowired
    private UmsAdminLoginLogMapper loginLogMapper;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Value("${jwt.tokenHead}")
    private String tokenHead;

    @Override
    public UmsAdmin getAdminByUsername(String username) {
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsAdmin> umsAdmins = umsAdminMapper.selectByExample(example);
        if (null != umsAdmins && umsAdmins.size() > 0) {
            return umsAdmins.get(0);
        }
        return null;
    }

    @Override
    public UmsAdmin register(UmsAdminParam umsAdminParam) {
        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(umsAdminParam, umsAdmin);
        umsAdmin.setCreateTime(new Date());
        umsAdmin.setStatus(1);//默认启用该账号
        //查询是否有相同用户名的用户
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(umsAdmin.getUsername());
        List<UmsAdmin> umsAdmins = umsAdminMapper.selectByExample(example);

        if (umsAdmins.size() > 0) {
            System.out.println("用户名已存在");
            return null;
        }
        //密码加密
        String md5Password = passwordEncoder.encodePassword(umsAdmin.getPassword(), null);
        umsAdmin.setPassword(md5Password);
        umsAdminMapper.insert(umsAdmin);
        return umsAdmin;
    }

    @Override
    public String login(String username, String password) {
        String token = null;
        //密码加密
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(username, passwordEncoder.encodePassword(password, null));
        try {
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            token = jwtTokenUtil.generateToken(userDetails);
            updateLoginTimeByUsername(username);
            insertLoginLog(username);

        } catch (Exception e) {
            System.out.println("登录异常" + e.getMessage());
            e.printStackTrace();
        }
        return token;
    }

    /**
     * 添加登录记录
     * @param username 用户名
     */
    private void insertLoginLog(String username) {
        UmsAdmin admin = getAdminByUsername(username);
        UmsAdminLoginLog loginLog = new UmsAdminLoginLog();
        loginLog.setAdminId(admin.getId());
        loginLog.setCreateTime(new Date());
        ServletRequestAttributes requestAttributes
                = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes.getRequest();
        loginLog.setIp(request.getRemoteAddr());

        loginLogMapper.insert(loginLog);

    }

    /**
     * 根据用户名修改登录时间
     * @param username
     */
    private void updateLoginTimeByUsername(String username) {
        UmsAdmin admin = new UmsAdmin();
        admin.setLoginTime(new Date());
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(username);
        umsAdminMapper.updateByExampleSelective(admin, example);
    }

    @Override
    public String refreshToken(String oldToken) {
        String token = oldToken.substring(tokenHead.length());
        if (jwtTokenUtil.canRefresh(token)) {
            return jwtTokenUtil.refreshToken(token);
        }
        return null;
    }

    @Override
    public UmsAdmin getUserById(Long id) {
        return umsAdminMapper.selectByPrimaryKey(id);
    }

    @Override
    public List<UmsAdmin> list(String name, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        UmsAdminExample example = new UmsAdminExample();
        if (!StringUtils.isEmpty(name)) {
            example.createCriteria().andUsernameLike("%" + name + "%");
            example.or(example.createCriteria().andNickNameLike("%" + name + "%"));
        }
        return umsAdminMapper.selectByExample(example);
    }

    @Override
    public int update(Long id, UmsAdmin admin) {
        admin.setId(id);
        return umsAdminMapper.updateByPrimaryKey(admin);
    }

    @Override
    public int delete(Long id) {
        return umsAdminMapper.deleteByPrimaryKey(id);
    }

    @Override
    public int updateRole(Long adminId, List<Long> roleIds) {
        int count = roleIds == null ? 0 : roleIds.size();
        //删除原来的关系
        UmsAdminRoleRelationExample example = new UmsAdminRoleRelationExample();
        example.createCriteria().andAdminIdEqualTo(adminId);
        adminRoleRelationMapper.deleteByExample(example);

        //建立新关系
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<UmsAdminRoleRelation> list = new ArrayList<>();
            for (Long id : roleIds) {
                UmsAdminRoleRelation roleRelation = new UmsAdminRoleRelation();

                roleRelation.setAdminId(adminId);
                roleRelation.setRoleId(id);

                list.add(roleRelation);
            }
            adminRoleRelationDao.insertList(list);
        }
        return count;
    }

    @Override
    public List<UmsRole> getRoleList(Long adminId) {
        return adminRoleRelationDao.getRoleList(adminId);
    }

    @Override
    public int updatePermission(Long adminId, List<Long> permissionIds) {
        //删除原所有的权限关系
        UmsAdminPermissionRelationExample example = new UmsAdminPermissionRelationExample();
        example.createCriteria().andAdminIdEqualTo(adminId);
        adminPermissionRelationMapper.deleteByExample(example);
        //获取用户所有角色权限
        List<UmsPermission> permissionList = adminRoleRelationDao.getPermissionList(adminId);

        List<Long> rolePermissionList = permissionList.stream()
                .map(UmsPermission::getId).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(rolePermissionList)) {
            List<UmsAdminPermissionRelation> adminPermissionRelations = new ArrayList<>();
            // TODO 注意理解
            //筛选出+权限
            List<Long> addPermissionIdList = permissionIds.stream()
                    .filter(permissionId -> !rolePermissionList.contains(permissionId))
                    .collect(Collectors.toList());
            //筛选出-权限
            List<Long> subPermissionIdList = rolePermissionList.stream()
                    .filter(permissionId -> !permissionIds.contains(permissionId))
                    .collect(Collectors.toList());
            //筛选出+-权限
            adminPermissionRelations.addAll(convert(adminId, 1, addPermissionIdList));
            adminPermissionRelations.addAll(convert(adminId, -1, subPermissionIdList));
            return adminPermissionRelationDao.insertList(adminPermissionRelations);
        }
        return 0;
    }

    /**
     * 把=-权限关系转换成对象
     * @param adminId
     * @param type
     * @param permissionIdList
     * @return
     */
    private List<UmsAdminPermissionRelation> convert(Long adminId, int type, List<Long> permissionIdList) {
        return permissionIdList.stream()
                .map(permissionId -> {
                    UmsAdminPermissionRelation umsAdminPermissionRelation = new UmsAdminPermissionRelation();
                    umsAdminPermissionRelation.setType(type);
                    umsAdminPermissionRelation.setAdminId(adminId);
                    umsAdminPermissionRelation.setPermissionId(permissionId);
                    return umsAdminPermissionRelation;
                }).collect(Collectors.toList());
    }

    @Override
    public List<UmsPermission> getPermissionList(Long adminId) {

        return adminRoleRelationDao.getPermissionList(adminId);
    }
}
