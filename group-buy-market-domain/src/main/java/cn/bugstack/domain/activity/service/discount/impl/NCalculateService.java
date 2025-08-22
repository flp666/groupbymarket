package cn.bugstack.domain.activity.service.discount.impl;

import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("N")
@Slf4j
public class NCalculateService extends AbstractDiscountCalculateService {
    @Override
    protected BigDecimal doCalculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {
        log.info("优惠策略折扣计算:{}", groupBuyDiscount.getDiscountType().getCode());

        String marketExpr = groupBuyDiscount.getMarketExpr();
        return new BigDecimal(marketExpr);

    }
}
