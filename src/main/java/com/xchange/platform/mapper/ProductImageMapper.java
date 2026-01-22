package com.xchange.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xchange.platform.entity.ProductImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {

    /**
     * 查询所有被引用的图片URL（去重）
     * @return URL集合
     */
    @Select("SELECT DISTINCT image_url FROM tb_product_image WHERE deleted = 0")
    Set<String> selectAllReferencedUrls();

    /**
     * 查询商品的图片列表（按排序顺序）
     */
    @Select("SELECT image_url FROM tb_product_image " +
            "WHERE product_id = #{productId} AND deleted = 0 " +
            "ORDER BY sort_order ASC, create_time DESC")
    List<String> selectImageUrlsByProductId(@Param("productId") Long productId);
}