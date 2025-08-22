package cn.bugstack.domain.tags.service;

import cn.bugstack.domain.tags.adapter.repository.ITagRepository;
import cn.bugstack.domain.tags.model.entity.CrowdTagsJobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TagServiceImpl implements ITagService {

    @Resource
    private ITagRepository repository;


    public void execTagBatchJob(String tagId, String batchId) {
        log.info("人群标签批次任务 tagId:{} batchId:{}", tagId, batchId);

        // 1. 查询批次任务
        CrowdTagsJobEntity crowdTagsJobEntity=repository.queryCrowdTagsJobEntity(tagId,batchId);

        // 2. 采集用户数据 - 这部分需要采集用户的消费类数据，后续有用户发起拼单后再处理。
        //TODO 下面我们采用模拟数据


        // 3. 数据写入记录
        ArrayList<String> userIdList = new ArrayList<>();
        userIdList.add("xiaofuge");
        userIdList.add("liergou");

        // 4. 一般人群标签的处理在公司中，会有专门的数据数仓团队通过脚本方式写入到数据库，就不用这样一个个或者批次来写。
        for (String userId:userIdList){

            //保存 即给用户贴标签
            repository.addCrowdTagsUserId(tagId,userId);

            //还要存入redis 后面实施层实现类去实现存入redis

        }

        // 5. 更新人群标签统计量
        repository.updateCrowdTagsStatistics(tagId,userIdList.size());

    }
}



