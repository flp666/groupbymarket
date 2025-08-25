package cn.bugstack.domain.activity.service.discount;


import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.DiscountTypeEnum;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;

import javax.annotation.Resource;
import java.math.BigDecimal;

public abstract class AbstractDiscountCalculateService implements IDiscountCalculateService {

    @Resource
    private IActivityRepository repository;

    @Override
    public BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {

        // 1. 人群标签过滤 限定人群优惠
        if(DiscountTypeEnum.TAG.equals(groupBuyDiscount.getDiscountType())){
            Boolean flag=filterTagId(userId,groupBuyDiscount.getTagId());//这个id是从活动查出来赋给它的
            if(!flag){
                return originalPrice;
            }
        }
        // 2. 折扣优惠计算 discount_type是BASE 或是TAG且userId在这个人群标签内（过滤通过） 就会优惠
        return doCalculate(userId,originalPrice,groupBuyDiscount);
    }

    private Boolean filterTagId(String userId, String tagId) {
        return repository.isTagCrowdRange(userId,tagId);
    }

    protected abstract BigDecimal doCalculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount);
}
