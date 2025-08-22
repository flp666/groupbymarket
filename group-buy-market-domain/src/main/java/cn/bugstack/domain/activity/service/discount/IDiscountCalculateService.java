package cn.bugstack.domain.activity.service.discount;


import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;

import java.math.BigDecimal;

// 用户ID
// 商品原始价格
// 折扣计划配置


public interface IDiscountCalculateService {

    public BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount);
}
