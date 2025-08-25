package cn.bugstack.test.types.rule02.logic;

import cn.bugstack.test.types.rule02.factory.Rule02TradeRuleFactory;
import cn.bugstack.types.design.framework.link.model2.handler.ILogicHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description
 * @create 2025-01-18 09:18
 */
@Slf4j
@Service
public class RuleLogic201 implements ILogicHandler<String, Rule02TradeRuleFactory.DynamicContext, XxxResponse> {

    public XxxResponse apply(String requestParameter, Rule02TradeRuleFactory.DynamicContext dynamicContext) throws Exception{

        log.info("link model02 RuleLogic201");

        return next(requestParameter, dynamicContext);
        //这也就是return null 因为ILogicHandler里方法next返回null
        //next() 方法本身并不找人，它只是一个信号发生器。
        //真正负责"找下一个人来干"的，是执行器 (BusinessLinkedList) 中的那个循环逻辑。
    }

}
