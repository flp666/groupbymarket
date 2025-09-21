package cn.bugstack.trigger.http;


import cn.bugstack.api.IMarketIndexService;
import cn.bugstack.api.dto.GoodsMarketRequestDTO;
import cn.bugstack.api.dto.GoodsMarketResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/index/")
public class MarketIndexController implements IMarketIndexService {

    //注意：在活动域

    @Resource
    private IIndexGroupBuyMarketService indexGroupBuyMarketService;


    @Override
    @RequestMapping(value = "query_group_buy_market_config", method = RequestMethod.POST)
    public Response<GoodsMarketResponseDTO> queryGroupBuyMarketConfig(@RequestBody GoodsMarketRequestDTO requestDTO) throws Exception {

        try {
            log.info("查询拼团营销配置开始:{} goodsId:{}", requestDTO.getUserId(), requestDTO.getGoodsId());

            //校验参数
            if(StringUtils.isBlank(requestDTO.getUserId()) || StringUtils.isBlank(requestDTO.getSource()) || StringUtils.isBlank(requestDTO.getChannel()) || StringUtils.isBlank(requestDTO.getGoodsId())){
                return Response.<GoodsMarketResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 1. 营销优惠试算
            MarketProductEntity marketProductEntity = MarketProductEntity.builder()
                    .userId(requestDTO.getUserId())
                    .goodsId(requestDTO.getGoodsId())
                    .source(requestDTO.getSource())
                    .channel(requestDTO.getChannel())
                    .build();
            TrialBalanceEntity trialBalanceEntity = indexGroupBuyMarketService.indexMarketTrial(marketProductEntity);
            GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = trialBalanceEntity.getGroupBuyActivityDiscountVO();

            Long activityId = groupBuyActivityDiscountVO.getActivityId();

            // 2. 查询拼团组队   UserGroupBuyOrderDetailEntity和GoodsMarketResponseDTO的List<Team>的Team一样
            List<UserGroupBuyOrderDetailEntity> userGroupBuyOrderDetailEntities=indexGroupBuyMarketService.queryInProgressUserGroupBuyOrderDetailList(activityId, requestDTO.getUserId(), 1, 2);


            // 3. 统计！拼团数据  TeamStatisticVO和GoodsMarketResponseDTO的TeamStatistic一样
            TeamStatisticVO teamStatisticVO=indexGroupBuyMarketService.queryTeamStatisticByActivityId(activityId);

            // 4.构建GoodsMarketResponseDTO并返回
            //4.1构建goods
            GoodsMarketResponseDTO.Goods goods= GoodsMarketResponseDTO.Goods
                    .builder()
                    .goodsId(trialBalanceEntity.getGoodsId())
                    .originalPrice(trialBalanceEntity.getOriginalPrice())
                    .deductionPrice(trialBalanceEntity.getDeductionPrice())
                    .payPrice(trialBalanceEntity.getPayPrice())
                    .build();

            // 4.2构建teamList
            List<GoodsMarketResponseDTO.Team> teamList=new ArrayList<>();
            if (null != userGroupBuyOrderDetailEntities && !userGroupBuyOrderDetailEntities.isEmpty()) {
                for(UserGroupBuyOrderDetailEntity userGroupBuyOrderDetailEntity:userGroupBuyOrderDetailEntities){
                    GoodsMarketResponseDTO.Team team = GoodsMarketResponseDTO.Team
                            .builder()
                            .userId(userGroupBuyOrderDetailEntity.getUserId())
                            .teamId(userGroupBuyOrderDetailEntity.getTeamId())
                            .activityId(userGroupBuyOrderDetailEntity.getActivityId())
                            .targetCount(userGroupBuyOrderDetailEntity.getTargetCount())
                            .completeCount(userGroupBuyOrderDetailEntity.getCompleteCount())
                            .lockCount(userGroupBuyOrderDetailEntity.getLockCount())
                            .validStartTime(userGroupBuyOrderDetailEntity.getValidStartTime())
                            .validEndTime(userGroupBuyOrderDetailEntity.getValidEndTime())
                            .validTimeCountdown(GoodsMarketResponseDTO.Team.differenceDateTime2Str(new Date(),userGroupBuyOrderDetailEntity.getValidEndTime()))//TODO new date?
                            .outTradeNo(userGroupBuyOrderDetailEntity.getOutTradeNo())
                            .build();

                    teamList.add(team);
                }
            }
            // 4.3构建teamStatistic
            GoodsMarketResponseDTO.TeamStatistic teamStatistic= GoodsMarketResponseDTO.TeamStatistic
                    .builder()
                    .allTeamCompleteCount(teamStatisticVO.getAllTeamCompleteCount())
                    .allTeamCount(teamStatisticVO.getAllTeamCount())
                    .allTeamUserCount(teamStatisticVO.getAllTeamUserCount())
                    .build();

            GoodsMarketResponseDTO responseDTO = GoodsMarketResponseDTO.builder()
                    .goods(goods)
                    .teamList(teamList)
                    .teamStatistic(teamStatistic)
                    .activityId(activityId)
                    .build();

            log.info("查询拼团营销配置完成:{} goodsId:{} response:{}", requestDTO.getUserId(), requestDTO.getGoodsId(), JSON.toJSONString(responseDTO));

            return Response.<GoodsMarketResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();

        } catch (Exception e) {
            log.error("查询拼团营销配置失败:{} goodsId:{}", requestDTO.getUserId(), requestDTO.getGoodsId(), e);
            return Response.<GoodsMarketResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }

    }
}
