package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScSkuActivity {

    private Long id;
    private String source;
    private String channel;
    private String goodsId;
    private Long activityId;
    private Date createTime;
    private Date updateTime;

}
