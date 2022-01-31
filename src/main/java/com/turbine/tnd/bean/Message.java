package com.turbine.tnd.bean;

import lombok.Data;
import lombok.ToString;

/**
 * @author 邱信强
 * @Description
 * @date 2022/1/19 16:24
 */


@ToString
@Data
public class  Message {
    private int code;
    private String message;
    private Object data;




    public void setResultCode(ResultCode result){
        this.code = result.getCode();
        this.message = result.getMessage();
    }

}