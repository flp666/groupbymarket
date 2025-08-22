package cn.bugstack.domain.activity.service.discount.impl;

import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.discount.AbstractDiscountCalculateService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
//满减
@Service("MJ")
@Slf4j
public class MJCalculateService extends AbstractDiscountCalculateService {
    @Override
    protected BigDecimal doCalculate(String userId, BigDecimal originalPrice, GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount) {

        log.info("优惠策略折扣计算:{}", groupBuyDiscount.getDiscountType().getCode());

        String marketExpr = groupBuyDiscount.getMarketExpr();
        String[] arr = marketExpr.split(Constants.SPLIT);
        BigDecimal man = new BigDecimal(arr[0]);
        BigDecimal jian = new BigDecimal(arr[1]);

        if(originalPrice.compareTo(man)<0){
            return originalPrice;
        }

        BigDecimal deductionPrice = originalPrice.subtract(jian);

        if(deductionPrice.compareTo(BigDecimal.ZERO)<=0){
            return new BigDecimal(0.01);
        }
        return deductionPrice;
    }
}
