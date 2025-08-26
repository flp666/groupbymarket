package cn.bugstack.domain.trade.service.lock.factory;


import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.TradeRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.lock.filter.ActivityUsabilityRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.UserTakeLimitRuleFilter;
import cn.bugstack.types.design.framework.link.model2.LinkArmory;
import cn.bugstack.types.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * @description 交易规则过滤工厂
 */
@Slf4j
@Service
public class TradeRuleFilterFactory {

    //创建配置一个责任链实例
    @Bean("tradeRuleFilter")
    public BusinessLinkedList<TradeRuleCommandEntity, TradeRuleFilterFactory.DynamicContext, TradeRuleFilterBackEntity> tradeRuleFilter(ActivityUsabilityRuleFilter activityFilter, UserTakeLimitRuleFilter userFilter){

        // 组装链
        LinkArmory<TradeRuleCommandEntity, TradeRuleFilterFactory.DynamicContext, TradeRuleFilterBackEntity> linkArmory = new LinkArmory<>("交易规则过滤链",activityFilter,userFilter);

        //链对象
        return linkArmory.getLogicLink();

    }


    //动态上下文 用于处理器handler即filter之间共享数据
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DynamicContext{

        private GroupBuyActivityEntity groupBuyActivityEntity;

    }

}
