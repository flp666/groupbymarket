package cn.bugstack.domain.activity.service.trial.node;

import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.domain.activity.service.trial.AbstractGroupBuyMarketSupport;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.activity.service.trial.thread.QueryGroupBuyActivityDiscountVOThreadTask;
import cn.bugstack.domain.activity.service.trial.thread.QuerySkuVOThreadTask;
import cn.bugstack.types.design.framework.tree.StrategyHandler;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class MarketNode extends AbstractGroupBuyMarketSupport {
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private EndNode endNode;

    @Resource
    private ErrorNode errorNode;

    @Resource
    private Map<String, IDiscountCalculateService> discountCalculateServiceMap;



    @Override
    protected TrialBalanceEntity doApply(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("拼团商品查询试算服务-MarketNode userId:{} requestParameter:{}", requestParameter.getUserId(), JSON.toJSONString(requestParameter));
        // 拼团优惠试算


        GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = dynamicContext.getGroupBuyActivityDiscountVO();
        if(null==groupBuyActivityDiscountVO) return router(requestParameter,dynamicContext);//到ErrorNode  当根据 source（来源）、channel（渠道）、goodsId（商品ID）在数据库里完全没有查到任何活动配置时，这个对象就是 null。这意味着：这个商品在这个渠道下根本没有参与任何拼团活动，是一个最根本的“无活动”状态。


        SkuVO skuVO = dynamicContext.getSkuVO();
        GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount = dynamicContext.getGroupBuyActivityDiscountVO().getGroupBuyDiscount();
        if(null==groupBuyDiscount || null==skuVO) return router(requestParameter, dynamicContext);//到ErrorNode 虽然查到了活动配置（groupBuyActivityDiscountVO 不是null），但这个配置里没有有效的折扣规则。这可能是因为数据配置错误或折扣已过期失效。skuVO 为空：虽然商品ID传过来了，但在数据库里没找到这个商品。这可能是参数错误或商品已下架。


        String key = groupBuyDiscount.getMarketPlan();
        IDiscountCalculateService service = discountCalculateServiceMap.get(key);

        if (null == service) {
            log.info("不存在{}类型的折扣计算服务，支持类型为:{}", groupBuyDiscount.getMarketPlan(), JSON.toJSONString(discountCalculateServiceMap.keySet()));
            throw new AppException(ResponseCode.E0001.getCode(), ResponseCode.E0001.getInfo());
        }

        //这里需要的商品原始价格 从上下文的skuVo中取
        BigDecimal deductionPrice = service.calculate(requestParameter.getUserId(), skuVO.getOriginalPrice(), groupBuyDiscount);

        //把折扣价格存入上下文
        dynamicContext.setDeductionPrice(deductionPrice);

        return router(requestParameter, dynamicContext);
    }


    @Override
    public StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> get(MarketProductEntity requestParam, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {


        //刚开始疑惑为什么这里不对折扣判空
        //groupBuyDiscount 为 null，根本不会执行到计算折扣价 deductionPrice 那一步代码，所以 deductionPrice 肯定也是 null。所以折扣为空也走ErrorNode
        //只有当活动、折扣价、商品信息三者全都齐全时，才叫成功，其他任何情况都是失败”。这比在 doApply 里用多个 if-else 判断要更一目了然。
        if(null==dynamicContext.getGroupBuyActivityDiscountVO()
        || null==dynamicContext.getDeductionPrice()
        || null==dynamicContext.getSkuVO()){
            return errorNode;// 不存在配置的拼团活动，走异常节点
        }

        return endNode;
    }


    @Override
    protected void multiThread(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

        //异步查询活动优惠配置
        QueryGroupBuyActivityDiscountVOThreadTask task1 = new
                QueryGroupBuyActivityDiscountVOThreadTask(requestParameter.getSource(), requestParameter.getChannel(), repository,requestParameter.getGoodsId());
        FutureTask<GroupBuyActivityDiscountVO> futureTask1 = new FutureTask<>(task1);
        threadPoolExecutor.execute(futureTask1);

        //异步查询商品信息-在实际生产中，商品有同步库或者调用接口查询。这里暂时使用DB方式查询。
        QuerySkuVOThreadTask task2 = new QuerySkuVOThreadTask(requestParameter.getGoodsId(), repository);
        FutureTask<SkuVO> futureTask2 = new FutureTask<>(task2);
        threadPoolExecutor.execute(futureTask2);

        //写入上下文-对于一些复杂场景，获取数据的操作，有时候会在下N个节点获取，这样前置查询数据，可以提高接口响应效率 //TODO 写入之后怎么流转的?
        GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = futureTask1.get(timeout, TimeUnit.MINUTES);
        SkuVO skuVO = futureTask2.get(timeout, TimeUnit.MINUTES);
        dynamicContext.setGroupBuyActivityDiscountVO(groupBuyActivityDiscountVO);
        dynamicContext.setSkuVO(skuVO);

        log.info("拼团商品查询试算服务-MarketNode userId:{} 异步线程加载数据「GroupBuyActivityDiscountVO、SkuVO」完成", requestParameter.getUserId());
    }
}
