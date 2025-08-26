package cn.bugstack.domain.trade.service.settlement;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TradeSettlementOrderService implements ITradeSettlementOrderService {

    @Resource
    private ITradeRepository repository;

    @Resource//(name = "tradeSettlementRuleFilter")//能够按照类型匹配成功
    private BusinessLinkedList<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext, TradeSettlementRuleFilterBackEntity>  tradeSettlementRuleFilter;

    @Override
    public TradePaySettlementEntity settlementMarketPayOrder(TradePaySuccessEntity tradePaySuccessEntity) throws Exception {

        log.info("拼团交易-支付订单结算:{} outTradeNo:{}", tradePaySuccessEntity.getUserId(), tradePaySuccessEntity.getOutTradeNo());

        /*1. 查询拼团信息 也就是判断是不是锁单订单
        MarketPayOrderEntity marketPayOrderEntity = repository.queryNoPayMarketPayOrderByOutTradeNo(tradePaySuccessEntity.getUserId(), tradePaySuccessEntity.getOutTradeNo());
        if(null==marketPayOrderEntity){
            log.info("不存在的外部交易单号或用户已退单，不需要做支付订单结算:{} outTradeNo:{}", tradePaySuccessEntity.getUserId(), tradePaySuccessEntity.getOutTradeNo());
            return null;
        }
        2. 查询组团信息
        GroupBuyTeamEntity groupBuyTeamEntity=repository.queryGroupBuyTeamByTeamId(marketPayOrderEntity.getTeamId());
        这两块 包括别的交易结算过滤 写在责任链里 所以责任链返回值TradeSettlementRuleFilterBackEntity 和GroupBuyTeamEntity一样*/

        //执行责任链
        // 1. 结算规则过滤
        TradeSettlementRuleCommandEntity command = TradeSettlementRuleCommandEntity.builder()
                .source(tradePaySuccessEntity.getSource())
                .channel(tradePaySuccessEntity.getChannel())
                .userId(tradePaySuccessEntity.getUserId())
                .outTradeNo(tradePaySuccessEntity.getOutTradeNo())
                .outTradeTime(tradePaySuccessEntity.getOutTradeTime())
                .build();
        TradeSettlementRuleFilterBackEntity filterBackEntity =  tradeSettlementRuleFilter.apply(command, new TradeSettlementRuleFilterFactory.DynamicContext());


        // 2.组装组团信息
        GroupBuyTeamEntity groupBuyTeamEntity =
                GroupBuyTeamEntity.builder()
                        .teamId(filterBackEntity.getTeamId())
                        .activityId(filterBackEntity.getActivityId())
                        .targetCount(filterBackEntity.getTargetCount())
                        .completeCount(filterBackEntity.getCompleteCount())
                        .lockCount(filterBackEntity.getLockCount())
                        .status(filterBackEntity.getStatus())
                        .validStartTime(filterBackEntity.getValidStartTime())
                        .validEndTime(filterBackEntity.getValidEndTime())
                        .build();


        // 3. 构建聚合对象
        GroupBuyTeamSettlementAggregate aggregate = GroupBuyTeamSettlementAggregate.builder()
                .groupBuyTeamEntity(groupBuyTeamEntity)
                .tradePaySuccessEntity(tradePaySuccessEntity)
                .userEntity(UserEntity.builder().userId(tradePaySuccessEntity.getUserId()).build())
                .build();

        // 4. 拼团交易结算
        repository.settlementMarketPayOrder(aggregate);

        // 5. 返回结算信息 - 公司中开发这样的流程时候，会根据外部需要进行值的设置
        return TradePaySettlementEntity.builder()
                .source(tradePaySuccessEntity.getSource())
                .channel(tradePaySuccessEntity.getChannel())
                .userId(tradePaySuccessEntity.getUserId())
                .teamId(groupBuyTeamEntity.getTeamId())
                .activityId(groupBuyTeamEntity.getActivityId())
                .outTradeNo(tradePaySuccessEntity.getOutTradeNo())
                .build();
    }
}
