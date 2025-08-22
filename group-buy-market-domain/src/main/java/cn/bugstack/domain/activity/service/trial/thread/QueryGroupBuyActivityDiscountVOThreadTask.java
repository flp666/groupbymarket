package cn.bugstack.domain.activity.service.trial.thread;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.ScSkuActivityVo;

import java.util.concurrent.Callable;

public class QueryGroupBuyActivityDiscountVOThreadTask implements Callable<GroupBuyActivityDiscountVO> {

    //来源
    private final String source;
    //渠道
    private final String channel;
    //活动仓储
    private final IActivityRepository activityRepository;

    private String goodsId;


    public QueryGroupBuyActivityDiscountVOThreadTask(String source, String channel, IActivityRepository activityRepository,String goodsId) {
        this.source = source;
        this.channel = channel;
        this.activityRepository = activityRepository;
        this.goodsId=goodsId;

    }

    @Override
    public GroupBuyActivityDiscountVO call() throws Exception {

        //查询活动商品关联表
        ScSkuActivityVo scSkuActivityVo=activityRepository.queryScSkuActivityByScGoodsId(source,channel,goodsId);
        if(null==scSkuActivityVo) return null;

        //得到活动id
        Long activityId = scSkuActivityVo.getActivityId();

        //查询活动表
        return activityRepository.queryGroupBuyActivityDiscountVO(activityId);
    }

}
