package com.turbine.tnd.dao;

import com.turbine.tnd.bean.ShareResource;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.web.bind.annotation.Mapping;

/**
 * @author Turbine
 * @Description
 * @date 2022/3/28 15:14
 */
@Mapper
public interface UserShareResourceDao {

    public int modifyShareResource(ShareResource sr);
    public int addShareResource(ShareResource sr);

}
