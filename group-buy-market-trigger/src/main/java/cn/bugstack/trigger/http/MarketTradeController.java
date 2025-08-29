package cn.bugstack.trigger.http;


import cn.bugstack.api.IMarketTradeService;
import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.LockMarketPayOrderResponseDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Objects;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/trade/")
public class MarketTradeController implements IMarketTradeService {

    @Resource
    private ITradeLockOrderService tradeOrderService;
    @Resource
    private IIndexGroupBuyMarketService indexGroupBuyMarketService;

    @Resource
    private ITradeSettlementOrderService tradeSettlementOrderService;

    //锁单================================================================================================

    @RequestMapping(value = "lock_market_pay_order", method = RequestMethod.POST)
    public Response<LockMarketPayOrderResponseDTO> lockMarketPayOrder(@RequestBody LockMarketPayOrderRequestDTO lockMarketPayOrderRequestDTO) throws Exception {

        try {
            // 参数
            String userId = lockMarketPayOrderRequestDTO.getUserId();
            String source = lockMarketPayOrderRequestDTO.getSource();
            String channel = lockMarketPayOrderRequestDTO.getChannel();
            String goodsId = lockMarketPayOrderRequestDTO.getGoodsId();
            Long activityId = lockMarketPayOrderRequestDTO.getActivityId();
            String outTradeNo = lockMarketPayOrderRequestDTO.getOutTradeNo();
            String teamId = lockMarketPayOrderRequestDTO.getTeamId();
            String notifyUrl = lockMarketPayOrderRequestDTO.getNotifyUrl();

            log.info("营销交易锁单:{} LockMarketPayOrderRequestDTO:{}", userId, JSON.toJSONString(lockMarketPayOrderRequestDTO));

            if (StringUtils.isBlank(userId) || StringUtils.isBlank(source) || StringUtils.isBlank(channel) || StringUtils.isBlank(goodsId) || StringUtils.isBlank(goodsId) || null == activityId || StringUtils.isBlank(notifyUrl)) {
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }

            // 查询 outTradeNo 是否已经存在交易记录
            MarketPayOrderEntity marketPayOrderEntity = tradeOrderService.queryNoPayMarketPayOrderByOutTradeNo(userId, outTradeNo);
            if (null != marketPayOrderEntity) {
                LockMarketPayOrderResponseDTO responseDTO =
                        LockMarketPayOrderResponseDTO.builder()
                                .orderId(marketPayOrderEntity.getOrderId())
                                .deductionPrice(marketPayOrderEntity.getDeductionPrice())
                                .tradeOrderStatus(marketPayOrderEntity.getTradeOrderStatusEnumVO().getCode())
                                .build();

                log.info("交易锁单记录(存在):{} marketPayOrderEntity:{}", userId, JSON.toJSONString(marketPayOrderEntity));
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(responseDTO)
                        .build();
            }


            //判断拼团锁单是否完成了目标 查进度
            if (null != teamId) {
                GroupBuyProgressVO groupBuyProgressVO = tradeOrderService.queryGroupBuyProgress(teamId);
                if (null != groupBuyProgressVO && Objects.equals(groupBuyProgressVO.getTargetCount(), groupBuyProgressVO.getLockCount())) {
                    log.info("交易锁单拦截-拼单目标已达成:{} {}", userId, teamId);
                    return Response.<LockMarketPayOrderResponseDTO>builder()
                            .code(ResponseCode.E0006.getCode())
                            .info(ResponseCode.E0006.getInfo())
                            .build();
                }
            }


            //营销优惠试算
            MarketProductEntity marketProductEntity = MarketProductEntity.builder()
                    .userId(userId)
                    .goodsId(goodsId)
                    .source(source)
                    .channel(channel)
                    .build();
            TrialBalanceEntity trialBalanceEntity = indexGroupBuyMarketService.indexMarketTrial(marketProductEntity);
            GroupBuyActivityDiscountVO groupBuyActivityDiscountVO = trialBalanceEntity.getGroupBuyActivityDiscountVO();


            //人群标签过滤  2-7的人群标签节点过滤 TagNode 表示用户是否可见可参与这个活动 E0007:"拼团人群限定，不可参与"
            Boolean isVisible = trialBalanceEntity.getIsVisible();
            Boolean isEnable = trialBalanceEntity.getIsEnable();
            if ((!isVisible) || (!isEnable)) {
                return Response.<LockMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.E0007.getCode())
                        .info(ResponseCode.E0007.getInfo())
                        .build();
            }


            //锁单
            marketPayOrderEntity = tradeOrderService.lockMarketPayOrder(
                    UserEntity.builder().userId(userId).build(),
                    PayActivityEntity.builder()
                            .teamId(teamId)
                            .activityId(activityId)
                            .activityName(groupBuyActivityDiscountVO.getActivityName())
                            .startTime(groupBuyActivityDiscountVO.getStartTime())
                            .endTime(groupBuyActivityDiscountVO.getEndTime())
                            .targetCount(groupBuyActivityDiscountVO.getTarget())
                            .validTime(groupBuyActivityDiscountVO.getValidTime())
                            .build(),
                    PayDiscountEntity.builder()
                            .source(source)
                            .channel(channel)
                            .goodsId(goodsId)
                            .goodsName(trialBalanceEntity.getGoodsName())
                            .originalPrice(trialBalanceEntity.getOriginalPrice())
                            .deductionPrice(trialBalanceEntity.getDeductionPrice())
                            .outTradeNo(outTradeNo)
                            .payPrice(trialBalanceEntity.getPayPrice())
                            .notifyUrl(notifyUrl)
                            .build()
            );

            log.info("交易锁单记录(新):{} marketPayOrderEntity:{}", userId, JSON.toJSONString(marketPayOrderEntity));
            LockMarketPayOrderResponseDTO responseDTO = LockMarketPayOrderResponseDTO.builder()
                    .orderId(marketPayOrderEntity.getOrderId())
                    .deductionPrice(marketPayOrderEntity.getDeductionPrice())
                    .tradeOrderStatus(marketPayOrderEntity.getTradeOrderStatusEnumVO().getCode())
                    .build();

            return Response.<LockMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO).build();

            //写到这里 完结 把上面代码try catch起来

        } catch (AppException e) {
            log.error("营销交易锁单业务异常:{} LockMarketPayOrderRequestDTO:{}", lockMarketPayOrderRequestDTO.getUserId(), JSON.toJSONString(lockMarketPayOrderRequestDTO), e);
            return Response.<LockMarketPayOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("营销交易锁单服务失败:{} LockMarketPayOrderRequestDTO:{}", lockMarketPayOrderRequestDTO.getUserId(), JSON.toJSONString(lockMarketPayOrderRequestDTO), e);
            return Response.<LockMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }

    }




    //交易====================================================================================================

    @Override
    @RequestMapping(value = "settlement_market_pay_order", method = RequestMethod.POST)
    public Response<SettlementMarketPayOrderResponseDTO> settlementMarketPayOrder(@RequestBody SettlementMarketPayOrderRequestDTO requestDTO) throws Exception {

        try {
            log.info("营销交易组队结算开始:{} outTradeNo:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo());

            //1.校验参数
            if (StringUtils.isBlank(requestDTO.getUserId()) || StringUtils.isBlank(requestDTO.getSource()) || StringUtils.isBlank(requestDTO.getChannel()) || StringUtils.isBlank(requestDTO.getOutTradeNo()) || null == requestDTO.getOutTradeTime()) {
                return Response.<SettlementMarketPayOrderResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }


            //2.调用交易服务
            TradePaySuccessEntity tradePaySuccessEntity = TradePaySuccessEntity.builder()
                    .userId(requestDTO.getUserId())
                    .source(requestDTO.getSource())
                    .channel(requestDTO.getChannel())
                    .outTradeNo(requestDTO.getOutTradeNo())
                    .outTradeTime(requestDTO.getOutTradeTime())
                    .build();
            TradePaySettlementEntity tradePaySettlementEntity = tradeSettlementOrderService.settlementMarketPayOrder(tradePaySuccessEntity);

            //3.构建返回值并返回
            SettlementMarketPayOrderResponseDTO responseDTO = SettlementMarketPayOrderResponseDTO.builder()
                    .activityId(tradePaySettlementEntity.getActivityId())
                    .userId(tradePaySettlementEntity.getUserId())
                    .teamId(tradePaySettlementEntity.getTeamId())
                    .outTradeNo(tradePaySettlementEntity.getOutTradeNo())
                    .build();

            log.info("营销交易组队结算完成:{} outTradeNo:{} response:{}", requestDTO.getUserId(), requestDTO.getOutTradeNo(), JSON.toJSONString(responseDTO));

            return Response.<SettlementMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();

            //以上内容 try catch try起来
        } catch (AppException e) {
            log.error("营销交易组队结算异常:{} LockMarketPayOrderRequestDTO:{}", requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            return Response.<SettlementMarketPayOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        }
        catch (Exception e) {
            log.error("营销交易组队结算失败:{} LockMarketPayOrderRequestDTO:{}", requestDTO.getUserId(), JSON.toJSONString(requestDTO), e);
            return Response.<SettlementMarketPayOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }
}
