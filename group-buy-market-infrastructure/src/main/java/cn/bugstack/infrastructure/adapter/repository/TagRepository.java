package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.tags.adapter.repository.ITagRepository;
import cn.bugstack.domain.tags.model.entity.CrowdTagsJobEntity;
import cn.bugstack.infrastructure.dao.ICrowdTagsDao;
import cn.bugstack.infrastructure.dao.ICrowdTagsDetailDao;
import cn.bugstack.infrastructure.dao.ICrowdTagsJobDao;
import cn.bugstack.infrastructure.dao.po.CrowdTags;
import cn.bugstack.infrastructure.dao.po.CrowdTagsDetail;
import cn.bugstack.infrastructure.dao.po.CrowdTagsJob;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.redisson.api.RBitSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class TagRepository implements ITagRepository {

    @Resource
    private ICrowdTagsDao crowdTagsDao;
    @Resource
    private ICrowdTagsJobDao crowdTagsJobDao;
    @Resource
    private ICrowdTagsDetailDao crowdTagsDetailDao;

    @Resource
    private IRedisService redisService;


    @Override
    public CrowdTagsJobEntity queryCrowdTagsJobEntity(String tagId, String batchId) {
        CrowdTagsJob crowdTagsJobReq = CrowdTagsJob.builder().batchId(batchId).tagId(tagId).build();

        CrowdTagsJob crowdTagsJobRes=crowdTagsJobDao.queryCrowdJob(crowdTagsJobReq);
        if (null == crowdTagsJobRes) return null;

        return CrowdTagsJobEntity.builder()
                .tagType(crowdTagsJobRes.getTagType())
                .tagRule(crowdTagsJobRes.getTagRule())
                .statStartTime(crowdTagsJobReq.getStatStartTime())
                .statEndTime(crowdTagsJobRes.getStatEndTime())
                .build();
    }

    @Override
    public void addCrowdTagsUserId(String tagId, String userId) {
        CrowdTagsDetail crowdTagsDetailReq = new CrowdTagsDetail();
        crowdTagsDetailReq.setTagId(tagId);
        crowdTagsDetailReq.setUserId(userId);

        try{
            //存入数据库
            crowdTagsDetailDao.addCrowdTagsUserId(crowdTagsDetailReq);

            //存入redis
            //获取BitSet
            RBitSet bitSet = redisService.getBitSet(tagId);
            int index = redisService.getIndexFromUserId(userId);
            bitSet.set(index,true);
        }catch(DuplicateKeyException ignore) {
            // 忽略唯一索引冲突 TODO?
        }


    }

    @Override
    public void updateCrowdTagsStatistics(String tagId, int size) {
        CrowdTags crowdTagsReq = new CrowdTags();
        crowdTagsReq.setTagId(tagId);
        crowdTagsReq.setStatistics(size);//这个size其实是增量

        crowdTagsDao.updataTagsStatistics(crowdTagsReq);

    }
}
