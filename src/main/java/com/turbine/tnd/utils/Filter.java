package com.turbine.tnd.utils;

/**
 * @author Turbine
 * @Description
 * @date 2022/1/23 18:17
 */
public interface Filter<T> {
    //过滤字符串方法
    public T filtration(T content);
}
