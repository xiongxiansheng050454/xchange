package com.xchange.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xchange.platform.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
