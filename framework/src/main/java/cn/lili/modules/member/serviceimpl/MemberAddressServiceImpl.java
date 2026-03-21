package cn.lili.modules.member.serviceimpl;

import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.member.entity.dos.MemberAddress;
import cn.lili.modules.member.mapper.MemberAddressMapper;
import cn.lili.modules.member.service.MemberAddressService;
import cn.lili.mybatis.util.PageUtil;
import cn.lili.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 收货地址业务层实现
 *
 * @author Chopper
 * @since 2020/11/18 9:44 上午
 */
@Service
public class MemberAddressServiceImpl extends ServiceImpl<MemberAddressMapper, MemberAddress> implements MemberAddressService {

    private static final Set<String> POSTAL_CODE_REQUIRED_COUNTRIES = new HashSet<>(Arrays.asList(
            "US", "CA", "JP", "KR", "AU", "AE", "SA", "QA", "KW", "OM", "BH", "TH", "VN"
    ));

    @Override
    public IPage<MemberAddress> getAddressByMember(PageVO page, String memberId) {
        return this.page(PageUtil.initPage(page),
                new QueryWrapper<MemberAddress>()
                        .eq("member_id", memberId));

    }

    @Override
    public MemberAddress getMemberAddress(String id) {
        AuthUser authUser = UserContext.getCurrentUser();
        if (authUser.getIsSuper() || UserEnums.MANAGER.equals(authUser.getRole())){
            return this.getOne(new QueryWrapper<MemberAddress>().eq("id", id));
        }else{
            return this.getOne(
                    new QueryWrapper<MemberAddress>()
                            .eq("member_id", Objects.requireNonNull(UserContext.getCurrentUser()).getId())
                            .eq("id", id));
        }
    }

    /**
     * 校验地址信息
     *
     * @param memberAddress 收货地址
     */
    private void checkAddress(MemberAddress memberAddress) {
        if (StringUtils.isEmpty(memberAddress.getCountryCode())) {
            memberAddress.setCountryCode("CN");
        }

        if (POSTAL_CODE_REQUIRED_COUNTRIES.contains(memberAddress.getCountryCode().toUpperCase())
                && StringUtils.isEmpty(memberAddress.getPostalCode())) {
            throw new ServiceException("该国家/地区邮政编码为必填项");
        }
    }
    /**
     * 根据地址ID获取当前会员地址信息
     *
     * @return 当前会员的地址信息
     */
    @Override
    public MemberAddress getDefaultMemberAddress() {
        return this.getOne(
                new QueryWrapper<MemberAddress>()
                        .eq("member_id", Objects.requireNonNull(UserContext.getCurrentUser()).getId())
                        .eq("is_default", true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberAddress saveMemberAddress(MemberAddress memberAddress) {
        //校验地址信息
        checkAddress(memberAddress);
        //判断当前地址是否为默认地址，如果为默认需要将其他的地址修改为非默认
        removeDefaultAddress(memberAddress);
        //添加会员地址
        this.save(memberAddress);

        return memberAddress;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberAddress updateMemberAddress(MemberAddress memberAddress) {
        //校验地址信息
        checkAddress(memberAddress);
        MemberAddress originalMemberAddress = this.getMemberAddress(memberAddress.getId());
        if (originalMemberAddress != null) {
            if (memberAddress.getIsDefault() == null) {
                memberAddress.setIsDefault(false);
            }
            //判断当前地址是否为默认地址，如果为默认需要将其他的地址修改为非默认
            removeDefaultAddress(memberAddress);
            this.saveOrUpdate(memberAddress);
        }

        return memberAddress;
    }

    @Override
    public boolean removeMemberAddress(String id) {
        return this.remove(new QueryWrapper<MemberAddress>()
                .eq("id", id));
    }

    /**
     * 修改会员默认收件地址
     *
     * @param memberAddress 收件地址
     */
    private void removeDefaultAddress(MemberAddress memberAddress) {
        //如果不是默认地址不需要处理
        if (Boolean.TRUE.equals(memberAddress.getIsDefault())) {
            //将会员的地址修改为非默认地址
            LambdaUpdateWrapper<MemberAddress> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
            lambdaUpdateWrapper.set(MemberAddress::getIsDefault, false);
            lambdaUpdateWrapper.eq(MemberAddress::getMemberId, memberAddress.getMemberId());
            this.update(lambdaUpdateWrapper);
        }

    }
}