package cn.bugstack.infrastructure.dao;


import cn.bugstack.infrastructure.dao.po.ScSkuActivity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IScSkuActivityDao {

    public ScSkuActivity queryByScGoodsId(ScSkuActivity scSkuActivity);
}
