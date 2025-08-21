package cn.bugstack.types.design.framework.tree;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractMultiThreadStrategyRouter<T,D,R> implements StrategyMapper<T,D,R>,StrategyHandler<T,D,R>{


    @Getter
    @Setter
    protected StrategyHandler<T, D, R> defaultStrategyHandler = StrategyHandler.DEFAULT;


    public R router(T requestParam, D dynamicContext) throws Exception{
        StrategyHandler<T, D, R> strategyHandler = get(requestParam, dynamicContext);

        if(null!=strategyHandler){
            return strategyHandler.apply(requestParam, dynamicContext);
        }
        return defaultStrategyHandler.apply(requestParam, dynamicContext);
    }


    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        // 异步加载数据
        multiThread(requestParameter, dynamicContext);
        // 业务流程受理
        return doApply(requestParameter, dynamicContext);
    }

    protected abstract void multiThread(T requestParameter, D dynamicContext) throws Exception;

    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;



}
