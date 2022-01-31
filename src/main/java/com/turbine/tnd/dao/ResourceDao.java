package com.turbine.tnd.dao;

import com.turbine.tnd.bean.Resource;
import com.turbine.tnd.bean.ResourceType;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Turbine
 * @Description
 * @date 2022/1/25 13:47
 */
@Mapper
public interface ResourceDao {
    /**
     * 添加一个资源到数据库
     * @param re
     */
    void addResource(Resource re);

    /**
     *保存资源的的类型 resource_type表
     * @param fileName     资源名 uuid
     * @param type_id      资源类型id
     */
    void addReourceType(String fileName,int type_id);

    /**
     * 保存资源的上传者
     * @param u_id          用户id
     * @param fileName      资源名 uuid
     */
    void addResourceUser(int u_id,String fileName,String originalName);

    /**
     * 根据文件类型来查询类型文件id
     * @param suffix
     * @return
     */
    ResourceType inquireType(String suffix);

    /**
     * 根据文件名查询文件是否已经存在
     * @param fileName
     * @return
     */
    Resource inquireByName(String fileName);
}