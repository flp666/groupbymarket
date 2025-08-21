package cn.bugstack.domain.activity.service.trial;


import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.types.design.framework.tree.AbstractStrategyRouter;


public abstract class AbstractGroupBuyMarketSupport extends AbstractStrategyRouter
        <MarketProductEntity,DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> {




}
