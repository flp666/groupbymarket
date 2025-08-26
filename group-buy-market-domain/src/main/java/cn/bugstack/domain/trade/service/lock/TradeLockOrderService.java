package cn.bugstack.domain.trade.service.lock;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TradeLockOrderService implements ITradeLockOrderService {

    @Resource
    private ITradeRepository repository;

    @Resource//(name = "tradeLockRuleFilter")
    private BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> businessLinkedList;



    @Override
    public MarketPayOrderEntity queryNoPayMarketPayOrderByOutTradeNo(String userId, String outTradeNo) {
        log.info("拼团交易-查询未支付营销订单:{} outTradeNo:{}", userId, outTradeNo);
        return repository.queryNoPayMarketPayOrderByOutTradeNo(userId,outTradeNo);
    }

    @Override
    public GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
        log.info("拼团交易-查询拼单进度:{}", teamId);
        return repository.queryGroupBuyProgress(teamId);
    }

    @Override
    public MarketPayOrderEntity lockMarketPayOrder(UserEntity userEntity, PayActivityEntity payActivityEntity, PayDiscountEntity payDiscountEntity) throws Exception {
        log.info("拼团交易-锁定营销优惠支付订单:{} activityId:{} goodsId:{}", userEntity.getUserId(), payActivityEntity.getActivityId(), payDiscountEntity.getGoodsId());


        //交易规则过滤 责任链过滤 过滤活动有效性和用户参与次数限制 model2责任链
        TradeLockRuleCommandEntity tradeRuleCommand = TradeLockRuleCommandEntity.builder()
                .userId(userEntity.getUserId())
                .activityId(payActivityEntity.getActivityId())
                .build();
        TradeLockRuleFilterBackEntity backEntity = businessLinkedList.apply(tradeRuleCommand, new TradeLockRuleFilterFactory.DynamicContext());


        // 已参与拼团量 - 用于构建数据库唯一索引使用，确保用户只能在一个活动上参与固定的次数
        Integer userTakeOrderCount = backEntity.getUserTakeOrderCount();

        // 构建聚合对象
        GroupBuyOrderAggregate aggregate = GroupBuyOrderAggregate.builder()
                .payActivityEntity(payActivityEntity)
                .payDiscountEntity(payDiscountEntity)
                .userEntity(userEntity)
                .userTakeOrderCount(userTakeOrderCount)
                .build();
        return repository.lockMarketPayOrder(aggregate);
    }
}
