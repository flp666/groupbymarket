package cn.bugstack.config;

import cn.bugstack.types.annotations.DCCValue;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @description 基于 Redis 实现动态配置中心
 */

@Slf4j
@Configuration
public class DCCValueBeanFactory implements BeanPostProcessor {

    private static final String BASE_CONFIG_PATH = "group_buy_market_dcc_";
    private final RedissonClient redissonClient;
    private final Map<String, Object> dccObjGroup = new HashMap<>();
    public DCCValueBeanFactory(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean("dccTopic")
    //定义频道：它定义了一个名为 group_buy_market_dcc 的Redis发布/订阅频道。
    //注册监听器：它注册了一个监听器（Listener），7x24小时不间断地监听这个频道
    //定义行为：它规定了一旦听到消息后应该怎么做：解析消息 -> 更新Redis -> 查找目标 -> 反射修改内存。
    //交付工具：它最终返回一个 RTopic 对象，Spring会把它管理起来，确保这个监听能力在整个应用生命周期内都有效。
    //最终效果就是：你通过HTTP接口发一个命令，这段代码里addListener后面的逻辑就会被自动触发，毫秒级地完成配置更新。它是一切“动态”效果的来源。
    public RTopic dccRedisTopicListener(RedissonClient redissonClient) {
        RTopic topic = redissonClient.getTopic("group_buy_market_dcc");//getTopic 是 Redisson 提供的方法 获取一个Redis的“发布/订阅”频道（Topic）的操作手柄。
        topic.addListener(String.class, (charSequence, s) -> {//s是Redisson框架在触发你的监听器回调时，自动塞给你的参数，它的值就是最初通过 redisService.publish() 发送出去的那个字符串。
            String[] split = s.split(Constants.SPLIT);

            // 获取值
            String attribute = split[0];
            String key = BASE_CONFIG_PATH + attribute;
            String value = split[1];

            // 设置值  (更新redis)
            RBucket<String> bucket = redissonClient.getBucket(key);
            boolean exists = bucket.isExists();
            if (!exists) return;//防御性编程。如果有人通过接口胡乱发送了一个不存在的配置Key，系统在这里会被拦截。因为一个不存在的Key意味着它没有被 @DCCValue 注解过，也意味着 dccObjGroup 花名册里没有它的记录，后续的反射修改也会失败。所以这里提前判断并退出，避免了执行无效操作。
            bucket.set(value);

            Object objBean = dccObjGroup.get(key);
            if (null == objBean) return;

            Class<?> objBeanClass = objBean.getClass();
            // 检查 objBean 是否是代理对象
            if (AopUtils.isAopProxy(objBean)) {
                // 获取代理对象的目标对象
                objBeanClass = AopUtils.getTargetClass(objBean);
            }

            try {
                // 1. getDeclaredField 方法用于获取指定类中声明的所有字段，包括私有字段、受保护字段和公共字段。
                // 2. getField 方法用于获取指定类中的公共字段，即只能获取到公共访问修饰符（public）的字段。
                Field field = objBeanClass.getDeclaredField(attribute);
                field.setAccessible(true);
                field.set(objBean, value);
                field.setAccessible(false);

                log.info("DCC 节点监听，动态设置值 {} {}", key, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return topic;
    }

    @Override
    //这个方法的执行不是由你的业务代码触发的，而是由 Spring IoC 容器在启动过程中自动触发的。
    //你启动应用程序，Spring容器开始启动。Spring开始根据配置（注解、XML等）创建（new）所有的Bean对象。
    //每成功创建并初始化一个Bean，Spring就会：
    //检查有没有实现了BeanPostProcessor接口的Bean（比如我们的DCCValueBeanFactory）。
    //如果有，就逐个调用这些Processor的postProcessAfterInitialization方法，并把当前创建好的Bean传进去。
    //就这样，直到所有Bean都被创建完毕，并且每个Bean都经过了BeanPostProcessor的处理，Spring容器的启动过程才算完成。
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 注意；增加 AOP 代理后，获得类的方式要通过 AopProxyUtils.getTargetClass(bean); 不能直接 bean.class 因为代理后类的结构发生变化，这样不能获得到自己的自定义注解了。

        //获得类
        Class<?> targetBeanClass = bean.getClass();
        Object targetBeanObject = bean;
        if (AopUtils.isAopProxy(bean)) {
            targetBeanClass = AopUtils.getTargetClass(bean);
            targetBeanObject = AopProxyUtils.getSingletonTarget(bean);
        }

        //根据反射获得类里面的成员变量
        Field[] fields = targetBeanClass.getDeclaredFields();
        for (Field field : fields) {//大循环
            if (!field.isAnnotationPresent(DCCValue.class)) {
                //成员变量上没有@DCCValue注解
                continue;
            }

            //成员变量上有@DCCValue注解
            DCCValue dccValue = field.getAnnotation(DCCValue.class);

            //读取写在注解括号里的那个字符串
            String value = dccValue.value();
            if (StringUtils.isBlank(value)) {
                //@DCCValue 注解的值有特定格式和作用的配置定义： 配置键:默认值 例如：downgradeSwitch:0
                //配置键：用于在Redis中标识和存储该配置的唯一key
                //默认值：当Redis中没有对应配置时使用的初始值
                //代码在 @DCCValue 值为空时抛出异常，而不是尝试从Redis获取值：
                //@DCCValue 的值为空意味着没有定义配置键和默认值
                //没有配置键，系统就无法知道应该从Redis的哪个位置读取配置
                throw new RuntimeException(field.getName() + " @DCCValue is not config value config case 「isSwitch/isSwitch:1」");
            }

            String[] splits = value.split(":");
            String key = BASE_CONFIG_PATH.concat(splits[0]);//形成key 如:group_buy_market_dcc_downgradeSwitch
            String defaultValue = splits.length == 2 ? splits[1] : null;

            // 设置值
            String setValue = defaultValue;

            try {
                // 如果为空则抛出异常
                if (StringUtils.isBlank(defaultValue)) {
                    throw new RuntimeException("dcc config error " + key + " is not null - 请配置默认值！");
                }

                // Redis 操作，判断配置Key是否存在，不存在则创建，存在则获取最新值
                RBucket<String> bucket = redissonClient.getBucket(key);
                boolean exists = bucket.isExists();//检查这个Key在Redis中是否存在。
                if (!exists) {
                    bucket.set(defaultValue);//redis里面 当你对一个不存在的Key执行SET操作时，Redis会自动创建这个Key，并把值赋给它。
                } else {
                    setValue = bucket.get();//获取redis里面这个Key对应的Value 用这个value。
                }

                //利用反射修改值 魔法 修改正在运行的Java程序里，某个对象实例的某个字段的值。
                //targetBeanObject：这是目标对象。它就是从Spring容器里取出来的、真实的、已经被实例化的那个对象。
                //比如，它就是 DCCService 这个类的一个具体实例（如 DCCService@5f1509）。
                field.setAccessible(true);
                field.set(targetBeanObject, setValue);  //这个对象的这个属性 设置为setValue
                field.setAccessible(false);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //建立花名册：这是最关键的一步！在dccObjGroup Map记下一行：
            //Key（要找谁）：如：group_buy_market_dcc_downgradeSwitch (Redis里的key)
            //Value（它在哪）：那个具体的DCCService对象 (对象的内存地址)
            //目的：以后只要有人提到key，我就能立刻找到要修改哪个对象。
            dccObjGroup.put(key, targetBeanObject);
        }

        return bean;
    }
}
