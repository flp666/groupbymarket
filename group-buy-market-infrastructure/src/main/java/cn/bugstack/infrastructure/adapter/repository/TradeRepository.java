package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyActivityDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.types.common.Constants;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Repository
public class TradeRepository implements ITradeRepository {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IGroupBuyActivityDao groupBuyActivityDao;

    @Resource
    private INotifyTaskDao notifyTaskDao;

    @Resource
    private DCCService dccService;


    @Override
    public MarketPayOrderEntity queryNoPayMarketPayOrderByOutTradeNo(String userId, String outTradeNo) {
        GroupBuyOrderList groupBuyOrderListReq = GroupBuyOrderList.builder().userId(userId).outTradeNo(outTradeNo).build();
        GroupBuyOrderList groupBuyOrderListRes = groupBuyOrderListDao.queryGroupBuyOrderRecordByOutTradeNo(groupBuyOrderListReq);

        if(null==groupBuyOrderListRes) return null;

        return MarketPayOrderEntity.builder()
                .orderId(groupBuyOrderListRes.getOrderId())
                .deductionPrice(groupBuyOrderListRes.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.valueOf(groupBuyOrderListRes.getStatus()))
                .teamId(groupBuyOrderListRes.getTeamId())
                .build();
    }


    @Override
    public GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
        GroupBuyOrder groupBuyOrder = groupBuyOrderDao.queryGroupBuyProgress(teamId);
        if (null == groupBuyOrder) return null;
        return GroupBuyProgressVO.builder()
                .completeCount(groupBuyOrder.getCompleteCount())
                .targetCount(groupBuyOrder.getTargetCount())
                .lockCount(groupBuyOrder.getLockCount())
                .build();
    }

    @Transactional(timeout = 500) //TODO 事务
    @Override
    public MarketPayOrderEntity lockMarketPayOrder(GroupBuyOrderAggregate aggregate) {

        //聚合对象信息
        PayActivityEntity payActivityEntity = aggregate.getPayActivityEntity();
        PayDiscountEntity payDiscountEntity = aggregate.getPayDiscountEntity();
        UserEntity userEntity = aggregate.getUserEntity();
        Integer userTakeOrderCount = aggregate.getUserTakeOrderCount();

        String teamId = payActivityEntity.getTeamId();




        if(StringUtils.isBlank(teamId)){
            //teamId为空

            // 使用 RandomStringUtils.randomNumeric 替代公司里使用的雪花算法UUID
            teamId = RandomStringUtils.randomNumeric(8);


            // 日期处理
            Date currentDate = new Date();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(currentDate);
            calendar.add(Calendar.MINUTE, payActivityEntity.getValidTime());


            // 构建拼团订单
            GroupBuyOrder groupBuyOrder = GroupBuyOrder.builder()
                    .teamId(teamId)
                    .activityId(payActivityEntity.getActivityId())
                    .source(payDiscountEntity.getSource())
                    .channel(payDiscountEntity.getChannel())
                    .originalPrice(payDiscountEntity.getOriginalPrice())
                    .deductionPrice(payDiscountEntity.getDeductionPrice())
                    .payPrice(payDiscountEntity.getPayPrice())
                    .targetCount(payActivityEntity.getTargetCount())
                    .completeCount(0)
                    .lockCount(1)
                    .validStartTime(currentDate) //现在
                    .validEndTime(calendar.getTime()) //现在+活动的valid_time
                    .notifyUrl(payDiscountEntity.getNotifyUrl())
                    .build();
            //写入group_buy_order
            groupBuyOrderDao.insert(groupBuyOrder);

        }else{
            //teamId不是空
            //在group_buy_order这一teamId下的lock_count加1
            int update = groupBuyOrderDao.updateAddLockCount(teamId);
            //正常情况下update操作返回值1 表示处理了一条数据
            //有可能拼团已满 也就是锁单数等于需要完成数 这时候update操作返回值不是1
            if(update!=1){
                throw new AppException(ResponseCode.E0005);
            }

        }

        //把用户数据写入group_buy_order_list
        //使用 RandomStringUtils.randomNumeric 替代公司里使用的雪花算法UUID
        String orderId = RandomStringUtils.randomNumeric(12);
        GroupBuyOrderList groupBuyOrderListReq =
                GroupBuyOrderList.builder()
                        .userId(userEntity.getUserId())
                        .teamId(teamId)
                        .orderId(orderId)
                        .activityId(payActivityEntity.getActivityId())
                        .startTime(payActivityEntity.getStartTime())
                        .endTime(payActivityEntity.getEndTime())
                        .goodsId(payDiscountEntity.getGoodsId())
                        .source(payDiscountEntity.getSource())
                        .channel(payDiscountEntity.getChannel())
                        .originalPrice(payDiscountEntity.getOriginalPrice())
                        .deductionPrice(payDiscountEntity.getDeductionPrice())
                        .payPrice(payDiscountEntity.getPayPrice())
                        .status(TradeOrderStatusEnumVO.CREATE.getCode())
                        .outTradeNo(payDiscountEntity.getOutTradeNo())
                        // 构建 bizId 唯一值；活动id_用户id_参与次数累加
                        .bizId(payActivityEntity.getActivityId() + Constants.UNDERLINE +
                                userEntity.getUserId() + Constants.UNDERLINE + (userTakeOrderCount + 1))
                        .build();//因为现在是锁单 没有支付 所以不写入out_trade_time

        try {
            // 写入拼团记录
            groupBuyOrderListDao.insert(groupBuyOrderListReq);
        } catch (DuplicateKeyException e) {//唯一索引冲突
            throw new AppException(ResponseCode.INDEX_EXCEPTION);
        }


        //返回结果
        return MarketPayOrderEntity.builder()
                .orderId(orderId)
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.CREATE)
                .teamId(teamId)
                .originalPrice(payDiscountEntity.getOriginalPrice())
                .payPrice(payDiscountEntity.getPayPrice())
                .build();
    }

    @Override
    public GroupBuyTeamEntity queryGroupBuyTeamByTeamId(String teamId) {
        GroupBuyOrder groupBuyOrder=groupBuyOrderDao.queryGroupBuyTeamByTeamId(teamId);

        if(groupBuyOrder==null) return null;

        return GroupBuyTeamEntity.builder()
                .teamId(groupBuyOrder.getTeamId())
                .activityId(groupBuyOrder.getActivityId())
                .targetCount(groupBuyOrder.getTargetCount())
                .completeCount(groupBuyOrder.getCompleteCount())
                .lockCount(groupBuyOrder.getLockCount())
                .status(GroupBuyOrderEnumVO.valueOf(groupBuyOrder.getStatus()))
                .validStartTime(groupBuyOrder.getValidStartTime())
                .validEndTime(groupBuyOrder.getValidEndTime())//注意
                .notifyUrl(groupBuyOrder.getNotifyUrl())
                .build();
    }

    @Override
    public GroupBuyActivityEntity queryGroupBuyActivityByActivityId(Long activityId) {

        GroupBuyActivity groupBuyActivity=groupBuyActivityDao.queryGroupBuyActivityByActivityId(activityId);
        return GroupBuyActivityEntity.builder()
                .activityId(groupBuyActivity.getActivityId())
                .activityName(groupBuyActivity.getActivityName())
                .discountId(groupBuyActivity.getDiscountId())
                .groupType(groupBuyActivity.getGroupType())
                .takeLimitCount(groupBuyActivity.getTakeLimitCount())
                .target(groupBuyActivity.getTarget())
                .validTime(groupBuyActivity.getValidTime())
                .status(ActivityStatusEnumVO.valueOf(groupBuyActivity.getStatus()))
                .startTime(groupBuyActivity.getStartTime())
                .endTime(groupBuyActivity.getEndTime())
                .tagId(groupBuyActivity.getTagId())
                .tagScope(groupBuyActivity.getTagScope())
                .build();
    }

    @Override
    public Integer queryOrderCountByActivityId(Long activityId, String userId) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setActivityId(activityId);
        groupBuyOrderListReq.setUserId(userId);

        return groupBuyOrderListDao.queryOrderCountByActivityId(groupBuyOrderListReq);
    }


    @Transactional(timeout = 500)//事务
    @Override
    public Boolean settlementMarketPayOrder(GroupBuyTeamSettlementAggregate aggregate) {

        //聚合对象 先都取出来
        GroupBuyTeamEntity groupBuyTeamEntity = aggregate.getGroupBuyTeamEntity();
        TradePaySuccessEntity tradePaySuccessEntity = aggregate.getTradePaySuccessEntity();
        UserEntity userEntity = aggregate.getUserEntity();

        // 1. 更新拼团订单明细状态 list表状态改为1
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userEntity.getUserId());
        groupBuyOrderListReq.setOutTradeNo(tradePaySuccessEntity.getOutTradeNo());
        groupBuyOrderListReq.setOutTradeTime(tradePaySuccessEntity.getOutTradeTime());
        Integer updateOrderListStatusCount = groupBuyOrderListDao.updateOrderStatus2COMPLETE(groupBuyOrderListReq);

        if(updateOrderListStatusCount!=1){
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        // 2. 更新拼团达成数量 order表complete_count+1
        Integer updateAddCount =groupBuyOrderDao.updateAddCompleteCount(groupBuyTeamEntity.getTeamId());
        if(updateAddCount!=1){
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }


        // 3. 更新拼团完成状态 若这是拼团中最后一笔订单 则此单交易成功 拼团就成功 状态改为1
        //也就是如果这笔订单交易的时候 complete=2 target=3 status=0
        //上面已经把complete+1 =3 现在complete=3 target=3 status=0
        //所以要把status改为完成 status=1
        if(groupBuyTeamEntity.getTargetCount()-groupBuyTeamEntity.getCompleteCount()==1){
            Integer updateOrderStatusCount= groupBuyOrderDao.updateOrderStatus2COMPLETE(groupBuyTeamEntity.getTeamId());
            if(updateOrderStatusCount!=1){
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }

            // 3.1查询拼团交易完成外部单号列表
            List<String> outTradeNoList=groupBuyOrderListDao.queryGroupBuyCompleteOrderOutTradeNoListByTeamId(groupBuyTeamEntity.getTeamId());

            // 3.2拼团完成写入回调任务记录

            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("teamId",groupBuyTeamEntity.getTeamId());
            hashMap.put("outTradeNoList",outTradeNoList);

            NotifyTask notifyTask = NotifyTask.builder()
                    .activityId(groupBuyTeamEntity.getActivityId())
                    .teamId(groupBuyTeamEntity.getTeamId())
                    .notifyCount(0)
                    .notifyStatus(0) //初始0
                    .parameterJson(JSON.toJSONString(hashMap))
                    .notifyUrl(groupBuyTeamEntity.getNotifyUrl())
                    .build();

            notifyTaskDao.insert(notifyTask);

            return true;
        }

        return false;
    }

    @Override
    public boolean isSCBlackIntercept(String source, String channel) {
        return dccService.isSCBlackIntercept(source,channel);
    }





    @Override
    public List<NotifyTaskEntity>queryUnExecutedNotifyTaskList(String teamId) {

        NotifyTask notifyTask = notifyTaskDao.queryUnExecutedNotifyTaskByTeamId(teamId);
        if (null == notifyTask) return new ArrayList<>();

        return Collections.singletonList(NotifyTaskEntity.builder()
                .teamId(notifyTask.getTeamId())
                .notifyUrl(notifyTask.getNotifyUrl())
                .notifyCount(notifyTask.getNotifyCount())
                .parameterJson(notifyTask.getParameterJson())
                .build());
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList() {

        List<NotifyTask> notifyTaskList = notifyTaskDao.queryUnExecutedNotifyTaskList();
        if (notifyTaskList.isEmpty()) return new ArrayList<>();

        List<NotifyTaskEntity> notifyTaskEntities = new ArrayList<>();
        for (NotifyTask notifyTask : notifyTaskList) {

            NotifyTaskEntity notifyTaskEntity = NotifyTaskEntity.builder()
                    .teamId(notifyTask.getTeamId())
                    .notifyUrl(notifyTask.getNotifyUrl())
                    .notifyCount(notifyTask.getNotifyCount())
                    .parameterJson(notifyTask.getParameterJson())
                    .build();

            notifyTaskEntities.add(notifyTaskEntity);
        }

        return notifyTaskEntities;
    }





    @Override
    public int updateNotifyTaskStatusSuccess(String teamId) {
        return notifyTaskDao.updateNotifyTaskStatusSuccess(teamId);
    }

    @Override
    public int updateNotifyTaskStatusRetry(String teamId) {
        return notifyTaskDao.updateNotifyTaskStatusRetry(teamId);
    }

    @Override
    public int updateNotifyTaskStatusError(String teamId) {
        return notifyTaskDao.updateNotifyTaskStatusError(teamId);
    }
}

