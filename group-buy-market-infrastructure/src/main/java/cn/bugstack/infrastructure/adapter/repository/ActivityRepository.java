package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.DiscountTypeEnum;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.ScSkuActivityVo;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.infrastructure.dao.IGroupBuyActivityDao;
import cn.bugstack.infrastructure.dao.IGroupBuyDiscountDao;
import cn.bugstack.infrastructure.dao.IScSkuActivityDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.dao.po.*;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.redisson.api.RBitSet;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

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


}






