package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.types.design.framework.tree.StrategyHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class IIndexGroupBuyMarketServiceImpl implements IIndexGroupBuyMarketService{

    @Resource
    private DefaultActivityStrategyFactory defaultActivityStrategyFactory;

    @Resource
    private IActivityRepository repository;


    public TrialBalanceEntity indexMarketTrial(MarketProductEntity marketProductEntity) throws Exception {
        StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity>
                strategyHandler = defaultActivityStrategyFactory.strategyHandler();

        TrialBalanceEntity trialBalanceEntity = strategyHandler.apply(marketProductEntity, new DefaultActivityStrategyFactory.DynamicContext());
        return trialBalanceEntity;
    }





    @Override
    public List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailList(Long activityId, String userId, Integer ownerCount, Integer randomCount) {

        List<UserGroupBuyOrderDetailEntity> unionAllList = new ArrayList<>();

        //查询个人拼团数据
        if(ownerCount>0){
            List<UserGroupBuyOrderDetailEntity> ownerList=repository.queryInProgressUserGroupBuyOrderDetailListByOwner(activityId,userId,ownerCount);
            if (null != ownerList && !ownerList.isEmpty()){
                unionAllList.addAll(ownerList);
            }
        }

        //查询非个人即其他人拼团数据  排除这个userId 要查别的数据
        if(randomCount>0){
            List<UserGroupBuyOrderDetailEntity> randomList=repository.queryInProgressUserGroupBuyOrderDetailListByRandom(activityId,userId,randomCount);
            if (null != randomList && !randomList.isEmpty()){
                unionAllList.addAll(randomList);
            }
        }
        return unionAllList;
    }

    @Override
    public TeamStatisticVO queryTeamStatisticByActivityId(Long activityId) {
        return repository.queryTeamStatisticByActivityId(activityId);
    }


}
