package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.entity.PayActivityEntity;
import cn.bugstack.domain.trade.model.entity.PayDiscountEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.common.Constants;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class TradeRepository implements ITradeRepository {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;


    @Override
    public MarketPayOrderEntity queryNoPayMarketPayOrderByOutTradeNo(String userId, String outTradeNo) {
        GroupBuyOrderList groupBuyOrderListReq = GroupBuyOrderList.builder().userId(userId).outTradeNo(outTradeNo).build();
        GroupBuyOrderList groupBuyOrderListRes = groupBuyOrderListDao.queryGroupBuyOrderRecordByOutTradeNo(groupBuyOrderListReq);

        if(null==groupBuyOrderListRes) return null;

        return MarketPayOrderEntity.builder()
                .orderId(groupBuyOrderListRes.getOrderId())
                .deductionPrice(groupBuyOrderListRes.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.valueOf(groupBuyOrderListRes.getStatus()))
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
                        .status(TradeOrderStatusEnumVO.CREATE.getCode())
                        .outTradeNo(payDiscountEntity.getOutTradeNo())
                        // 构建 bizId 唯一值；活动id_用户id_参与次数累加
                        .bizId(payActivityEntity.getActivityId() + Constants.UNDERLINE +
                                userEntity.getUserId() + Constants.UNDERLINE + (userTakeOrderCount + 1))
                        .build();

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
                .build();
    }
}
