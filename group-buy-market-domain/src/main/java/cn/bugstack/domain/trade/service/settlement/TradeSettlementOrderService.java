package cn.bugstack.domain.trade.service.settlement;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TradeSettlementOrderService implements ITradeSettlementOrderService {

    @Resource
    private ITradeRepository repository;

    @Resource
    private ITradePort port;

    @Resource//(name = "tradeSettlementRuleFilter")//能够按照类型匹配成功
    private BusinessLinkedList<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext, TradeSettlementRuleFilterBackEntity> tradeSettlementRuleFilter;

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
        TradeSettlementRuleFilterBackEntity filterBackEntity = tradeSettlementRuleFilter.apply(command, new TradeSettlementRuleFilterFactory.DynamicContext());


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
                        .notifyUrl(filterBackEntity.getNotifyUrl())
                        .build();


        // 3. 构建聚合对象
        GroupBuyTeamSettlementAggregate aggregate = GroupBuyTeamSettlementAggregate.builder()
                .groupBuyTeamEntity(groupBuyTeamEntity)
                .tradePaySuccessEntity(tradePaySuccessEntity)
                .userEntity(UserEntity.builder().userId(tradePaySuccessEntity.getUserId()).build())
                .build();

        // 4. 拼团交易结算
        Boolean notify = repository.settlementMarketPayOrder(aggregate);


        //5.如果拼团成功结算完成 回调 这里的if(notify=true)也就是上面结算成功 而且已经往notify_task插入数据了(当时插入的status=0 初始状态 还是未执行的任务)
        if (notify) {
            Map<String, Integer> notifyResultMap = execSettlementNotifyJob(groupBuyTeamEntity.getTeamId());
            log.info("回调通知拼团完结 result:{}", JSON.toJSONString(notifyResultMap));
        }

        // 6. 返回结算信息 - 公司中开发这样的流程时候，会根据外部需要进行值的设置
        return TradePaySettlementEntity.builder()
                .source(tradePaySuccessEntity.getSource())
                .channel(tradePaySuccessEntity.getChannel())
                .userId(tradePaySuccessEntity.getUserId())
                .teamId(groupBuyTeamEntity.getTeamId())
                .activityId(groupBuyTeamEntity.getActivityId())
                .outTradeNo(tradePaySuccessEntity.getOutTradeNo())
                .build();
    }


    //回调=================================================================================================

    @Override
    public Map<String, Integer> execSettlementNotifyJob(){//用于 Job（定时任务/调度任务）的监听和执行。
        log.info("拼团交易-执行结算通知任务");

        // 查询未执行任务
        List<NotifyTaskEntity> notifyTaskEntityList = repository.queryUnExecutedNotifyTaskList();
        return execSettlementNotifyJob(notifyTaskEntityList);
    }


    @Override
    public Map<String, Integer> execSettlementNotifyJob(String teamId) {//用于本类中 拼团交易成功后直接回调 可能回调失败 但是没关系 因为我们有任务监听groupBuyNotifyJob
        log.info("拼团交易-执行结算通知回调，指定 teamId:{}", teamId);

        //查询未执行任务 根据teamId查 其实最多查出来一条数据
        List<NotifyTaskEntity> notifyTaskEntityList = repository.queryUnExecutedNotifyTaskList(teamId);
        return execSettlementNotifyJob(notifyTaskEntityList);
    }


    public Map<String, Integer> execSettlementNotifyJob(List<NotifyTaskEntity> notifyTaskEntityList) {

        int successCount = 0, errorCount = 0, retryCount = 0;

        for (NotifyTaskEntity notifyTaskEntity : notifyTaskEntityList) {
            // 回调处理 response：success 成功，error 失败
            String response = port.groupBuyNotify(notifyTaskEntity);

            // 更新状态判断&变更数据库表回调任务状态
            if (NotifyTaskHTTPEnumVO.SUCCESS.getCode().equals(response)) {
                int updateCount = repository.updateNotifyTaskStatusSuccess(notifyTaskEntity.getTeamId());
                if (1 == updateCount) {
                    successCount += 1;
                }
            } else if (NotifyTaskHTTPEnumVO.ERROR.getCode().equals(response)) {
                if (notifyTaskEntity.getNotifyCount() < 5) {
                    int updateCount = repository.updateNotifyTaskStatusRetry(notifyTaskEntity.getTeamId());
                    if (1 == updateCount) {
                        retryCount += 1;
                    }
                } else {
                    int updateCount = repository.updateNotifyTaskStatusError(notifyTaskEntity.getTeamId());
                    if (1 == updateCount) {
                        errorCount += 1;
                    }
                }

            }

        }
        Map<String, Integer> resultMap = new HashMap<>();
        resultMap.put("waitCount", notifyTaskEntityList.size());
        resultMap.put("successCount", successCount);
        resultMap.put("errorCount", errorCount);
        resultMap.put("retryCount", retryCount);

        return resultMap;
    }
}
