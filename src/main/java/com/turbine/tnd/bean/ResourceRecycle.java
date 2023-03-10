package com.turbine.tnd.bean;

import lombok.Data;
import lombok.ToString;

import java.sql.Date;

/**
 * @author Turbine
 * @Description
 * @date 2022/3/28 19:06
 */
@Data
@ToString
public class ResourceRecycle {
    Integer u_id;
    Integer resourceId;
    Date deleteTime;
    String originalName;
    Integer parentId;
    Integer typeId;


}
