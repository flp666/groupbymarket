package cn.bugstack.domain.activity.service.trial.thread;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.types.design.framework.tree.StrategyHandler;

import java.util.concurrent.Callable;

public class QuerySkuVOThreadTask implements Callable<SkuVO> {//这里的泛型就是返回值


    private final String goodsId;
    private final IActivityRepository activityRepository;

    public QuerySkuVOThreadTask(String goodsId, IActivityRepository activityRepository) {
        this.goodsId = goodsId;
        this.activityRepository = activityRepository;
    }

    @Override
    public SkuVO call() throws Exception {
        return activityRepository.querySkuVO(goodsId);
    }
}
