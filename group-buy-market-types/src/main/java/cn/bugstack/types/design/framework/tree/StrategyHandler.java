package cn.bugstack.types.design.framework.tree;

public interface StrategyHandler<T,D,R>{

    StrategyHandler DEFAULT = (T, D) -> null;
    /*创建一个 StrategyHandler 对象，它的apply方法接受两个参数（不管参数具体是什么）直接返回 null
    等价的传统匿名内部类写法:
    new StrategyHandler<T, D, R>() {
        @Override
        public R apply(T requestParam, D dynamicContext) {
            return null;
        }
    }
    */


    R apply(T requestParam, D dynamicContext);
}
