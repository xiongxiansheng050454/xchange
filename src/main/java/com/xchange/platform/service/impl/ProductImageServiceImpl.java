package com.xchange.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xchange.platform.entity.ProductImage;
import com.xchange.platform.mapper.ProductImageMapper;
import com.xchange.platform.service.ProductImageService;
import com.xchange.platform.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageMapper productImageMapper;
    private final MinioUtil minioUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProductImages(Long productId, String coverImage, List<String> detailImages) {
        if (productId == null) {
            throw new RuntimeException("商品ID不能为空");
        }

        log.info("保存商品图片: productId={}, coverImage={}, detailImages={}",
                productId, coverImage, detailImages != null ? detailImages.size() : 0);

        // 1. 保存封面图（类型：1）
        if (StringUtils.isNotBlank(coverImage)) {
            ProductImage coverImageEntity = new ProductImage();
            coverImageEntity.setProductId(productId);
            coverImageEntity.setImageUrl(coverImage);
            coverImageEntity.setImageType(1); // 封面图
            coverImageEntity.setSortOrder(0);
            productImageMapper.insert(coverImageEntity);
            log.debug("封面图保存成功: productId={}", productId);
        }

        // 2. 保存详情图（类型：2）
        if (detailImages != null && !detailImages.isEmpty()) {
            List<ProductImage> detailImageEntities = detailImages.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(url -> {
                        ProductImage image = new ProductImage();
                        image.setProductId(productId);
                        image.setImageUrl(url);
                        image.setImageType(2); // 详情图
                        image.setSortOrder(0);
                        return image;
                    })
                    .collect(Collectors.toList());

            // MyBatis-Plus批量插入
            productImageMapper.insert(detailImageEntities);
            log.debug("详情图批量保存成功: productId={}, count={}", productId, detailImageEntities.size());
        }
    }

    @Override
    public List<String> getProductImageUrls(Long productId) {
        return productImageMapper.selectImageUrlsByProductId(productId);
    }

    @Override
    public String getCoverImageUrl(Long productId) {
        LambdaQueryWrapper<ProductImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductImage::getProductId, productId)
                .eq(ProductImage::getImageType, 1) // 封面图
                .eq(ProductImage::getDeleted, 0)
                .orderByDesc(ProductImage::getCreateTime)
                .last("LIMIT 1");

        ProductImage image = productImageMapper.selectOne(wrapper);
        return image != null ? image.getImageUrl() : null;
    }

    /**
     * 物理删除商品的图片文件（从MinIO删除）
     * @return 成功删除的文件数量
     */
    @Override
    public int deleteProductImageFiles(Long productId) {
        if (productId == null) {
            return 0;
        }

        log.info("开始删除商品图片文件: productId={}", productId);

        // 1. 查询所有图片URL
        List<String> imageUrls = getProductImageUrls(productId);
        if (imageUrls.isEmpty()) {
            log.info("商品无图片需要删除: productId={}", productId);
            return 0;
        }

        log.info("准备删除{}个图片文件: productId={}", imageUrls.size(), productId);

        // 2. 转换为MinIO对象名称
        List<String> objectNames = imageUrls.stream()
                .map(this::extractObjectNameFromUrl)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        if (objectNames.isEmpty()) {
            log.warn("无法提取对象名称: productId={}", productId);
            return 0;
        }

        // 3. 批量从MinIO删除
        try {
            int deletedCount = minioUtil.batchDeleteObjects(minioUtil.getBucketName(), objectNames);
            log.info("MinIO文件删除完成: productId={}, deleted={}", productId, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("MinIO文件删除失败: productId={}, error={}", productId, e.getMessage());
            throw new RuntimeException("删除图片文件失败", e);
        }
    }

    /**
     * 逻辑删除商品图片（标记deleted=1）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProductImages(Long productId) {
        if (productId == null) {
            return;
        }

        log.info("逻辑删除商品图片记录: productId={}", productId);

        // 使用MyBatis-Plus逻辑删除
        LambdaQueryWrapper<ProductImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductImage::getProductId, productId)
                .eq(ProductImage::getDeleted, 0);

        // 查询要删除的数据
        List<ProductImage> images = productImageMapper.selectList(wrapper);

        if (images.isEmpty()) {
            log.info("无图片记录需要删除: productId={}", productId);
            return;
        }

        // 批量逻辑删除
        for (ProductImage image : images) {
            image.setDeleted(1);
            productImageMapper.updateById(image);
        }

        log.info("商品图片记录删除完成: productId={}, count={}", productId, images.size());
    }

    /**
     * 从URL提取对象名称
     */
    private String extractObjectNameFromUrl(String fileUrl) {
        if (StringUtils.isBlank(fileUrl)) {
            return null;
        }
        try {
            String endpoint = minioUtil.getEndpoint();
            String path = fileUrl.replace(endpoint + "/", "");
            String bucketName = minioUtil.getBucketName();
            return path.replace(bucketName + "/", "");
        } catch (Exception e) {
            log.warn("URL解析失败: url={}, error={}", fileUrl, e.getMessage());
            return null;
        }
    }
}