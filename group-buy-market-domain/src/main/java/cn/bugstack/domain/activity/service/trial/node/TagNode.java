package cn.bugstack.domain.activity.service.trial.node;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.trial.AbstractGroupBuyMarketSupport;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.types.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TagNode extends AbstractGroupBuyMarketSupport {

    @Resource
    private EndNode endNode;

    @Resource
    private IActivityRepository activityRepository;

    @Override
    protected TrialBalanceEntity doApply(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {

        // 获取拼团活动配置
        GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = dynamicContext.getGroupBuyActivityDiscountVO();
        boolean enable = groupBuyActivityDiscountVO.isEnable();
        boolean visible = groupBuyActivityDiscountVO.isVisible();
        String tagId = groupBuyActivityDiscountVO.getTagId();

        // 人群标签配置为空，则走默认值
        if(StringUtils.isBlank(tagId)){
            dynamicContext.setIsVisible(true);
            dynamicContext.setIsEnable(true);
            return router(requestParameter, dynamicContext);
        }

        // 是否在人群范围内
        Boolean isWithin=activityRepository.isTagCrowdRange(requestParameter.getUserId(),tagId);

        //把最终的结写入上下文
        dynamicContext.setIsVisible(visible || isWithin);
        dynamicContext.setIsEnable(enable || isWithin);

        //走EndNode
        return router(requestParameter, dynamicContext);

    }



    @Override
    public StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> get(MarketProductEntity requestParam, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return endNode;
    }
}
