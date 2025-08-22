package cn.bugstack.domain.activity.service.discount;


import cn.bugstack.domain.activity.model.valobj.DiscountTypeEnum;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;

import java.math.BigDecimal;

public abstract class AbstractDiscountCalculateService implements IDiscountCalculateService {

    @Override
    public BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {

        // 1. 人群标签过滤 限定人群优惠
        if(DiscountTypeEnum.TAG.equals(groupBuyDiscount.getDiscountType())){
            Boolean flag=filterTagId(userId,groupBuyDiscount.getTagId());
            if(!flag){
                return originalPrice;
            }
        }
        // 2. 折扣优惠计算
        return doCalculate(userId,originalPrice,groupBuyDiscount);
    }

    private Boolean filterTagId(String userId, String tagId) {
        return true;
        // TODO
    }

    protected abstract BigDecimal doCalculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount);
}
