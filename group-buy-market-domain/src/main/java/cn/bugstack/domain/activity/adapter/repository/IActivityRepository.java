package cn.bugstack.domain.activity.adapter.repository;


import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.ScSkuActivityVo;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import org.springframework.stereotype.Repository;

//不用@Repository
public interface IActivityRepository {

    GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId);

    SkuVO querySkuVO(String goodsId);


    ScSkuActivityVo queryScSkuActivityByScGoodsId(String source,String channel,String goodsId);
}
