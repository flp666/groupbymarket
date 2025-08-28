package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.*;
import cn.bugstack.infrastructure.dao.*;
import cn.bugstack.infrastructure.dao.po.*;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.redisson.api.RBitSet;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class ActivityRepository implements IActivityRepository {

    @Resource
    private IGroupBuyActivityDao groupBuyActivityDao;
    @Resource
    private IGroupBuyDiscountDao groupBuyDiscountDao;
    @Resource
    private ISkuDao skuDao;

    @Resource
    private IScSkuActivityDao scSkuActivityDao;


    @Resource
    private IRedisService redisService;


    @Resource
    private DCCService dccService;

    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;


    @Override
    public GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId) {

        // 根据SC渠道值查询配置中最新的1个有效的活动
        GroupBuyActivity groupBuyActivityReq = new GroupBuyActivity();
        groupBuyActivityReq.setActivityId(activityId);
        GroupBuyActivity groupBuyActivityRes=groupBuyActivityDao.queryValidGroupBuyActivity(groupBuyActivityReq);

        if(null==groupBuyActivityRes) return null;

        String discountId = groupBuyActivityRes.getDiscountId();

        //已查出活动 取discount_id值 去折扣表查询对应的折扣信息
        GroupBuyDiscount groupBuyDiscountRes=groupBuyDiscountDao.queryGroupBuyDiscountByDiscountId(discountId);
        if(null==groupBuyDiscountRes) return null;

        //活动 折扣 已查到 接下来构建GroupBuyActivityDiscountVO 返回
        GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount =
                GroupBuyActivityDiscountVO.GroupBuyDiscount
                .builder()
                .discountDesc(groupBuyDiscountRes.getDiscountDesc())
                .discountName(groupBuyDiscountRes.getDiscountName())
                .discountType(DiscountTypeEnum.get(groupBuyDiscountRes.getDiscountType()))
                .marketExpr(groupBuyDiscountRes.getMarketExpr())
                .marketPlan(groupBuyDiscountRes.getMarketPlan())
                .build();

        GroupBuyActivityDiscountVO vo = GroupBuyActivityDiscountVO
                .builder()
                .activityId(groupBuyActivityRes.getActivityId())
                .activityName(groupBuyActivityRes.getActivityName())

                .groupBuyDiscount(groupBuyDiscount)
                .groupType(groupBuyActivityRes.getGroupType())
                .takeLimitCount(groupBuyActivityRes.getTakeLimitCount())
                .target(groupBuyActivityRes.getTarget())
                .validTime(groupBuyActivityRes.getValidTime())
                .status(groupBuyActivityRes.getStatus())
                .startTime(groupBuyActivityRes.getStartTime())
                .endTime(groupBuyActivityRes.getEndTime())
                .tagId(groupBuyActivityRes.getTagId())
                .tagScope(groupBuyActivityRes.getTagScope())
                .build();

        return vo;
    }

    @Override
    public SkuVO querySkuVO(String goodsId) {
        //查商品 查出来的是po里面的sku对象 转换成skuVo返回
        Sku sku = skuDao.querySkuByGoodsId(goodsId);

        if(null==sku) return null;
        return SkuVO.builder()
                .goodsId(sku.getGoodsId())
                .goodsName(sku.getGoodsName())
                .originalPrice(sku.getOriginalPrice())
                .build();
    }





    @Override
    public ScSkuActivityVo queryScSkuActivityByScGoodsId(String source,String channel,String goodsId) {
        ScSkuActivity scSkuActivityReq =
                ScSkuActivity.builder()
                        .source(source)
                        .channel(channel)
                        .goodsId(goodsId).build();
        ScSkuActivity scSkuActivityRes = scSkuActivityDao.queryByScGoodsId(scSkuActivityReq);

        if(null==scSkuActivityRes) return null;

        return ScSkuActivityVo.builder()
                .channel(scSkuActivityRes.getChannel())
                .source(scSkuActivityRes.getSource())
                .activityId(scSkuActivityRes.getActivityId())
                .goodsId(scSkuActivityRes.getGoodsId())
                .build();
    }



    @Override
    // 判断用户是否存在人群标签中
    //注意 从redis里查
    public Boolean isTagCrowdRange(String userId, String tagId) {
        RBitSet bitSet = redisService.getBitSet(tagId);
        if (!bitSet.isExists()) return true;

        return bitSet.get(redisService.getIndexFromUserId(userId));
    }

    @Override
    public boolean downgradeSwitch() {
        return dccService.isDowngradeSwitch();
    }

    @Override
    public boolean cutRange(String userId) {
        return dccService.isCutRange(userId);
    }







    @Override
    public List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByOwner(Long activityId, String userId, Integer ownerCount) {

        // 1. 根据用户ID、活动ID，查询用户参与的拼团队伍 这一步去list表查
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setActivityId(activityId);
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setCount(ownerCount);//注意GroupBuyOrderList继承了Page里面有count
        List<GroupBuyOrderList> groupBuyOrderLists = groupBuyOrderListDao.queryInProgressUserGroupBuyOrderDetailListByUserId(groupBuyOrderListReq);
        if (null == groupBuyOrderLists || groupBuyOrderLists.isEmpty()) return null;

        // 2. 过滤队伍获取 TeamId   拿出来刚刚查到的list表数据集合的teamId集合
        Set<String> teamIds = groupBuyOrderLists.stream()
                .map(GroupBuyOrderList::getTeamId)
                .filter(teamId -> teamId != null && !teamId.isEmpty()) // 过滤非空和非空字符串
                .collect(Collectors.toSet());

        // 3. 查询队伍明细，组装Map结构   拿着teamId集合去order表查拼团队伍
        List<GroupBuyOrder> groupBuyOrders = groupBuyOrderDao.queryGroupBuyProgressByTeamIds(teamIds);
        if (null == groupBuyOrders || groupBuyOrders.isEmpty()) return null;

        Map<String, GroupBuyOrder> groupBuyOrderMap = groupBuyOrders.stream()
                .collect(Collectors.toMap(GroupBuyOrder::getTeamId, order -> order));

        // 4. 转换数据
        List<UserGroupBuyOrderDetailEntity> userGroupBuyOrderDetailEntities = new ArrayList<>();
        for (GroupBuyOrderList groupBuyOrderList : groupBuyOrderLists) {
            String teamId = groupBuyOrderList.getTeamId();
            GroupBuyOrder groupBuyOrder = groupBuyOrderMap.get(teamId);
            if (null == groupBuyOrder) continue;

            UserGroupBuyOrderDetailEntity userGroupBuyOrderDetailEntity = UserGroupBuyOrderDetailEntity.builder()
                    .userId(groupBuyOrderList.getUserId())
                    .teamId(groupBuyOrder.getTeamId())
                    .activityId(groupBuyOrder.getActivityId())
                    .targetCount(groupBuyOrder.getTargetCount())
                    .completeCount(groupBuyOrder.getCompleteCount())
                    .lockCount(groupBuyOrder.getLockCount())
                    .validStartTime(groupBuyOrder.getValidStartTime())
                    .validEndTime(groupBuyOrder.getValidEndTime())
                    .outTradeNo(groupBuyOrderList.getOutTradeNo())
                    .build();

            userGroupBuyOrderDetailEntities.add(userGroupBuyOrderDetailEntity);
        }

        return userGroupBuyOrderDetailEntities;
    }




    @Override
    public List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByRandom(Long activityId, String userId, Integer randomCount) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setActivityId(activityId);
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setCount(randomCount*2);// 查询2倍的量，之后随机取其中 randomCount 数量
        List<GroupBuyOrderList> groupBuyOrderLists=groupBuyOrderListDao.queryInProgressUserGroupBuyOrderDetailListByRandom(groupBuyOrderListReq);
        if(null==groupBuyOrderLists || groupBuyOrderLists.isEmpty()) return null;

        // 判断总量是否大于 randomCount 取其中两个
        if(groupBuyOrderLists.size()>randomCount){
            // 随机打乱列表
            Collections.shuffle(groupBuyOrderLists);
            // 获取前 randomCount 个元素
            groupBuyOrderLists=groupBuyOrderLists.subList(0,randomCount);//如果都不够两个 不用截取 groupBuyOrderLists还是原数据
        }

        Set<String> teamIds = groupBuyOrderLists.stream()
                .map(GroupBuyOrderList::getTeamId)
                .filter(teamId -> teamId != null && !teamId.isEmpty())
                .collect(Collectors.toSet());


        List<GroupBuyOrder> groupBuyOrders = groupBuyOrderDao.queryGroupBuyProgressByTeamIds(teamIds);
        if (null == groupBuyOrders || groupBuyOrders.isEmpty()) return null;

        Map<String, GroupBuyOrder> groupBuyOrderMap = groupBuyOrders.stream().
                collect(Collectors.toMap(GroupBuyOrder::getTeamId, order -> order));

        ArrayList<UserGroupBuyOrderDetailEntity> orderDetailEntities = new ArrayList<>();
        for(GroupBuyOrderList groupBuyOrderList:groupBuyOrderLists){
            String teamId = groupBuyOrderList.getTeamId();
            GroupBuyOrder groupBuyOrder = groupBuyOrderMap.get(teamId);
            if (null == groupBuyOrder) continue;

            UserGroupBuyOrderDetailEntity orderDetailEntity = UserGroupBuyOrderDetailEntity.builder()
                    .userId(groupBuyOrderList.getUserId())
                    .teamId(groupBuyOrder.getTeamId())
                    .activityId(groupBuyOrder.getActivityId())
                    .targetCount(groupBuyOrder.getTargetCount())
                    .completeCount(groupBuyOrder.getCompleteCount())
                    .lockCount(groupBuyOrder.getLockCount())
                    .validStartTime(groupBuyOrder.getValidStartTime())
                    .validEndTime(groupBuyOrder.getValidEndTime())
                    .outTradeNo(groupBuyOrderList.getOutTradeNo())
                    .build();

            orderDetailEntities.add(orderDetailEntity);

        }
        return orderDetailEntities;

    }

    @Override
    public TeamStatisticVO queryTeamStatisticByActivityId(Long activityId) {
        // 1. 根据活动ID查询拼团队伍
        List<GroupBuyOrderList> groupBuyOrderLists= groupBuyOrderListDao.queryInProgressUserGroupBuyOrderDetailListByActivityId(activityId);
        if(null==groupBuyOrderLists || groupBuyOrderLists.isEmpty()){
            return new TeamStatisticVO(0,0,0);
        }

        // 2. 过滤队伍获取 TeamId
        Set<String> teamIds = groupBuyOrderLists.stream()
                .map(GroupBuyOrderList::getTeamId)
                .filter(teamId -> teamId != null && !teamId.isEmpty())
                .collect(Collectors.toSet());

        // 3. 统计数据
        Integer allTeamCount = groupBuyOrderDao.queryAllTeamCount(teamIds);//开团队伍数量
        Integer allTeamCompleteCount = groupBuyOrderDao.queryAllTeamCompleteCount(teamIds);//成团队伍数量
        Integer allTeamUserCount = groupBuyOrderDao.queryAllUserCount(teamIds);//参团人数总量 一个商品的总参团人数

        //上面已经根据activity_id查拼团队伍了即teamId 所以这些队伍都是针对这一活动的 针对这一商品的
        //所以groupBuyOrderDao.queryAllUserCount(teamIds)(看sql语句)这样查询这个商品总参团人数是对的
        //一个活动 (Activity) → 对应 → 一个商品 (Product/Goods)
        //   ↓
        //多个拼团队伍 (Teams) → 属于 → 这个活动
        //   ↓
        //多个用户 (Users) → 参与 → 这些队伍

        // 4. 构建对象
        return TeamStatisticVO.builder()
                .allTeamCount(allTeamCount)
                .allTeamCompleteCount(allTeamCompleteCount)
                .allTeamUserCount(allTeamUserCount)
                .build();

    }


}






