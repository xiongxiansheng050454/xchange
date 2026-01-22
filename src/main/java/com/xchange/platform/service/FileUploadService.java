package com.xchange.platform.service;

import com.xchange.platform.vo.UploadResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FileUploadService {

    /**
     * 批量上传图片（异步）
     * @param sellerId 卖家ID
     * @param productId 商品ID
     * @param imageType 图片类型
     * @param files 文件列表
     * @return CompletableFuture包装的结果列表
     */
    CompletableFuture<List<UploadResultVO>> uploadProductImagesAsync(
            Long sellerId, Long productId, String imageType, List<MultipartFile> files);

    /**
     * 单文件上传（异步）
     * @param sellerId 卖家ID
     * @param productId 商品ID
     * @param imageType 图片类型
     * @param file 单个文件
     * @return CompletableFuture包装的单个结果
     */
    CompletableFuture<UploadResultVO> uploadSingleFileAsync(
            Long sellerId, Long productId, String imageType, MultipartFile file);

    /**
     * 删除图片文件（同步）
     * @param sellerId 卖家ID（用于权限校验）
     * @param fileUrl 文件URL
     */
    void deleteImage(Long sellerId, String fileUrl);

    /**
     * 上传商品相关图片（返回URL列表）
     */
    List<String> uploadAndGetUrls(Long sellerId, Long productId, List<MultipartFile> files);
}