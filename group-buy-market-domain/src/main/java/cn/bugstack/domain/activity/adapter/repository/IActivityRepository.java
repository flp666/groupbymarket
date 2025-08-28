package cn.bugstack.domain.activity.adapter.repository;


import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.ScSkuActivityVo;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

//不用@Repository
public interface IActivityRepository {

    GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId);

    SkuVO querySkuVO(String goodsId);


    ScSkuActivityVo queryScSkuActivityByScGoodsId(String source,String channel,String goodsId);


    Boolean isTagCrowdRange(String userId, String tagId);


    boolean downgradeSwitch();

    boolean cutRange(String userId);


    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByOwner(Long activityId, String userId, Integer ownerCount);

    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByRandom(Long activityId, String userId, Integer randomCount);

    TeamStatisticVO queryTeamStatisticByActivityId(Long activityId);

}
