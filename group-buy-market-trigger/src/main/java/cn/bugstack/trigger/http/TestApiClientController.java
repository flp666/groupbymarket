package cn.bugstack.trigger.http;


import cn.bugstack.api.dto.NotifyRequestDTO;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 回调服务接口测试
 * @create 2025-01-31 08:59
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/test/")

//它扮演“外部微服务”的角色，提供一个模拟的接收回调的接口 (/api/v1/test/group_buy_notify)，
// 用于测试整个回调链路是否通畅。
public class TestApiClientController {

    /**
     * 模拟回调案例
     * http://127.0.0.1:8091/api/v1/test/group_buy_notify
     * @param notifyRequestDTO 通知回调参数
     * @return success 成功，error 失败
     */
    @RequestMapping(value = "group_buy_notify", method = RequestMethod.POST)
    public String groupBuyNotify(@RequestBody NotifyRequestDTO notifyRequestDTO) {
        log.info("模拟测试第三方服务接收拼团回调 {}", JSON.toJSONString(notifyRequestDTO));

        return "success";
    }

}
