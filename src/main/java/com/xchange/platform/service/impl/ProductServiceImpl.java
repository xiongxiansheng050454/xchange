package com.xchange.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xchange.platform.document.ProductDocument;
import com.xchange.platform.dto.ProductDTO;
import com.xchange.platform.dto.ProductQueryDTO;
import com.xchange.platform.dto.UpdateProductDTO;
import com.xchange.platform.entity.Product;
import com.xchange.platform.mapper.ProductMapper;
import com.xchange.platform.repository.ProductESRepository;
import com.xchange.platform.service.ProductImageService;
import com.xchange.platform.service.ProductService;
import com.xchange.platform.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductImageService productImageService;
    private final ProductESRepository productESRepository;

    /**
     * 实体转换为VO（抽取公共方法）
     */
    private ProductVO convertToVO(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .campusLocation(product.getCampusLocation())
                .status(product.getStatus())
                .sellerId(product.getSellerId())
                .createTime(product.getCreateTime())
                .build();
    }

    @Override
    public ProductVO getProductDetail(Long productId) {
        log.info("查询商品详情: productId={}", productId);

        // 1. 查询商品（未删除且已上架）
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getId, productId)
                        .eq(Product::getDeleted, 0)
                        .eq(Product::getStatus, 1)
        );

        if (product == null) {
            log.warn("商品不存在或已下架: productId={}", productId);
            throw new RuntimeException("商品不存在或已下架");
        }

        // 2. 转换为VO并返回
        return convertToVO(product);
    }

    /**
     * 校验商品归属权（确保只能操作自己的商品）
     * @param sellerId 当前登录用户ID
     * @param productId 商品ID
     * @return 商品实体
     */
    private Product validateProductOwnership(Long sellerId, Long productId) {
        Product product = productMapper.selectById(productId);

        if (product == null) {
            log.warn("商品不存在: productId={}", productId);
            throw new RuntimeException("商品不存在");
        }

        if (Objects.equals(product.getDeleted(), 1)) {
            log.warn("商品已下架: productId={}", productId);
            throw new RuntimeException("商品已下架，无法操作");
        }

        // 关键：校验商品是否属于当前用户
        if (!product.getSellerId().equals(sellerId)) {
            log.warn("无权操作他人商品: sellerId={}, productSellerId={}",
                    sellerId, product.getSellerId());
            throw new RuntimeException("无权操作该商品");
        }

        return product;
    }

    @Override
    public ProductVO updateProduct(Long sellerId, Long productId, UpdateProductDTO updateDTO) {
        log.info("修改商品请求: sellerId={}, productId={}", sellerId, productId);

        // 1. 查询商品并校验归属
        Product product = validateProductOwnership(sellerId, productId);

        // 2. 更新字段（只更新非空字段）
        if (StringUtils.isNotBlank(updateDTO.getName())) {
            product.setName(updateDTO.getName());
        }
        if (StringUtils.isNotBlank(updateDTO.getDescription())) {
            product.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getPrice() != null) {
            product.setPrice(updateDTO.getPrice());
        }
        if (updateDTO.getStock() != null) {
            product.setStock(updateDTO.getStock());
        }
        if (StringUtils.isNotBlank(updateDTO.getCoverImage())) {
            product.setCoverImage(updateDTO.getCoverImage());
        }
        if (StringUtils.isNotBlank(updateDTO.getCampusLocation())) {
            product.setCampusLocation(updateDTO.getCampusLocation());
        }
        if (updateDTO.getStatus() != null) {
            product.setStatus(updateDTO.getStatus());
        }

        // 3. 执行更新（MyBatis-Plus自动更新updateTime）
        int updateCount = productMapper.updateById(product);
        if (updateCount != 1) {
            log.error("商品更新失败: productId={}", productId);
            throw new RuntimeException("商品更新失败，请稍后重试");
        }

        log.info("商品更新成功: productId={}, sellerId={}", productId, sellerId);

        // 4. 返回更新后的数据
        return convertToVO(product);
    }

    /**
     * 下架商品（逻辑删除）+ 级联删除图片
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeProduct(Long sellerId, Long productId) {
        log.info("删除商品请求: sellerId={}, productId={}", sellerId, productId);

        // 校验商品归属权
        validateProductOwnership(sellerId, productId);

        try {
            // 先物理删除MinIO中的图片文件
            log.info("删除商品图片文件...");
            int deletedFiles = productImageService.deleteProductImageFiles(productId);
            log.info("图片文件删除完成: count={}", deletedFiles);

            // 再逻辑删除数据库中的图片记录
            log.info("删除商品图片记录...");
            productImageService.deleteProductImages(productId);
            log.info("图片记录删除完成");

            // 从Elasticsearch删除
            try {
                productESRepository.deleteById(productId);
                log.info("ES文档删除成功: productId={}", productId);
            } catch (Exception e) {
                log.error("ES文档删除失败: productId={}, error={}", productId, e.getMessage());
            }

            // 最后逻辑删除商品
            log.info("删除商品记录...");
            int deleteCount = productMapper.deleteById(productId); // MyBatis-Plus逻辑删除
            if (deleteCount != 1) {
                log.error("商品删除失败: productId={}", productId);
                throw new RuntimeException("商品删除失败，请稍后重试");
            }

            log.info("商品删除成功: productId={}, sellerId={}, deletedImages={}",
                    productId, sellerId, deletedFiles);

        } catch (RuntimeException e) {
            log.error("商品删除失败: productId={}, error={}", productId, e.getMessage());
            throw e; // 触发事务回滚
        } catch (Exception e) {
            log.error("商品删除异常: productId={}", productId, e);
            throw new RuntimeException("删除失败，请稍后重试");
        }
    }

    @Override
    public IPage<ProductVO> getProductPage(ProductQueryDTO queryDTO) {
        log.info("商品分页查询请求: pageNum={}, pageSize={}, name={}",
                queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getName());

        // 1. 创建分页对象
        Page<Product> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 2. 创建查询条件（动态SQL）
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 只查询未删除、已上架的商品（可根据需求调整）
        wrapper.eq(Product::getDeleted, 0)
                .eq(Product::getStatus, 1);

        // 模糊查询商品名称
        if (StringUtils.isNotBlank(queryDTO.getName())) {
            wrapper.like(Product::getName, queryDTO.getName());
        }

        // 按校区筛选
        if (StringUtils.isNotBlank(queryDTO.getCampusLocation())) {
            wrapper.eq(Product::getCampusLocation, queryDTO.getCampusLocation());
        }

        // 3. 排序规则
        if (StringUtils.isNotBlank(queryDTO.getSortBy())) {
            String sortColumn = queryDTO.getSortBy();
            boolean asc = Boolean.TRUE.equals(queryDTO.getAsc());

            // 根据sortBy字段名映射到实体属性
            switch (sortColumn) {
                case "price":
                    wrapper.orderBy(true, asc, Product::getPrice);
                    break;
                case "stock":
                    wrapper.orderBy(true, asc, Product::getStock);
                    break;
                case "createTime":
                default:
                    wrapper.orderBy(true, asc, Product::getCreateTime);
                    break;
            }
        } else {
            // 默认按创建时间降序（最新商品在前）
            wrapper.orderByDesc(Product::getCreateTime);
        }

        // 4. 执行分页查询
        IPage<Product> productPage = productMapper.selectPage(page, wrapper);

        // 5. 转换为VO对象（脱敏处理）
        return productPage.convert(this::convertToVO);
    }

    @Override
    public ProductVO publishProduct(Long sellerId, ProductDTO productDTO) {
        log.info("商品发布请求: sellerId={}, name={}", sellerId, productDTO.getName());

        // 1. 创建商品实体（补全缺失字段）
        Product product = new Product();
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        product.setStock(productDTO.getStock());
        product.setCampusLocation(productDTO.getCampusLocation());
        product.setStatus(1); // 默认上架状态
        product.setSellerId(sellerId);

        // 2. 插入数据库（MyBatis-Plus自动填充createTime/updateTime）
        int insertCount = productMapper.insert(product);
        if (insertCount != 1) {
            log.error("商品发布失败: sellerId={}", sellerId);
            throw new RuntimeException("商品发布失败，请稍后重试");
        }

        log.info("商品发布成功: productId={}, sellerId={}", product.getId(), sellerId);

        // 3. 转换为VO并返回（脱敏）
        ProductVO productVO = convertToVO(product);

        // 4. 同步到Elasticsearch（异步执行，不影响主流程）
        try {
            ProductDocument document = convertToDocument(productVO);
            productESRepository.save(document);
            log.info("商品已同步到ES: productId={}", product.getId());
        } catch (Exception e) {
            log.error("同步到ES失败: productId={}, error={}", product.getId(), e.getMessage());
            // 不抛出异常，避免影响主流程（可后续补偿）
        }

        return productVO;
    }

    /**
     * ProductVO 转换为 ProductDocument
     */
    private ProductDocument convertToDocument(ProductVO productVO) {
        return ProductDocument.builder()
                .id(productVO.getId())
                .name(productVO.getName())
                .description(productVO.getDescription())
                .price(productVO.getPrice())
                .stock(productVO.getStock())
                .campusLocation(productVO.getCampusLocation())
                .status(productVO.getStatus())
                .sellerId(productVO.getSellerId())
                .coverImageUrl(productVO.getCoverImageUrl())
                .detailImageUrls(productVO.getDetailImageUrls())
                .createTime(productVO.getCreateTime())
                .searchBoost(1)  // 默认权重
                .build();
    }
}