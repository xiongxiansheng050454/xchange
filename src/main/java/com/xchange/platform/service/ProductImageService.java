package com.xchange.platform.service;

import java.util.List;

public interface ProductImageService {

    /**
     * 保存商品图片（批量）
     * @param productId 商品ID
     * @param coverImage 封面图URL
     * @param detailImages 详情图URL列表
     */
    void saveProductImages(Long productId, String coverImage, List<String> detailImages);

    /**
     * 查询商品的所有图片
     * @param productId 商品ID
     * @return 图片URL列表
     */
    List<String> getProductImageUrls(Long productId);

    /**
     * 物理删除商品的图片文件（从MinIO删除）
     * @param productId 商品ID
     * @return 成功删除的文件数量
     */
    int deleteProductImageFiles(Long productId);

    /**
     * 逻辑删除商品的图片文件（从mysql删除）
     * @param productId 商品ID
     */
    void deleteProductImages(Long productId);

    /**
     * 查询商品的封面图
     * @param productId 商品ID
     * @return 封面图URL
     */
    String getCoverImageUrl(Long productId);
}