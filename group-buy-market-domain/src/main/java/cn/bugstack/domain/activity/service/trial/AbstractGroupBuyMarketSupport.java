package cn.bugstack.domain.activity.service.trial;


import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.types.design.framework.tree.AbstractMultiThreadStrategyRouter;

import javax.annotation.Resource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


public abstract class AbstractGroupBuyMarketSupport extends AbstractMultiThreadStrategyRouter
        <MarketProductEntity,DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> {

    //注意这里timeout repository都是MarketNode里面线程部分要用到的 老师把repository在这注入了
    protected long timeout = 500;
    @Resource
    protected IActivityRepository repository;




    @Override
    protected void multiThread(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        //缺省实现
    }
}
