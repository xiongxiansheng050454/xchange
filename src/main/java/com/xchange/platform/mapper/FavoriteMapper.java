package com.xchange.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xchange.platform.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
