package com.xchange.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xchange.platform.dto.ProductDTO;
import com.xchange.platform.dto.ProductQueryDTO;
import com.xchange.platform.dto.UpdateProductDTO;
import com.xchange.platform.vo.ProductVO;

public interface ProductService {
    /**
     * 发布商品
     * @param sellerId 卖家ID（从JWT获取）
     * @param productDTO 商品信息
     * @return 商品VO（脱敏）
     */
    ProductVO publishProduct(Long sellerId, ProductDTO productDTO);

    /**
     * 分页查询商品列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    IPage<ProductVO> getProductPage(ProductQueryDTO queryDTO);

    /**
     * 查询商品详情
     * @param productId 商品ID
     * @return 商品VO
     */
    ProductVO getProductDetail(Long productId);

    /**
     * 修改商品信息
     * @param sellerId 卖家ID（从JWT获取，用于权限校验）
     * @param productId 商品ID
     * @param updateDTO 更新内容
     * @return 更新后的商品VO
     */
    ProductVO updateProduct(Long sellerId, Long productId, UpdateProductDTO updateDTO);

    /**
     * 下架商品（逻辑删除）
     * @param sellerId 卖家ID（从JWT获取，用于权限校验）
     * @param productId 商品ID
     */
    void removeProduct(Long sellerId, Long productId);

}