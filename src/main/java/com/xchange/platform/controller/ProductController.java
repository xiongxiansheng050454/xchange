package com.xchange.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xchange.platform.common.Result;
import com.xchange.platform.dto.ProductDTO;
import com.xchange.platform.dto.ProductQueryDTO;
import com.xchange.platform.dto.UpdateProductDTO;
import com.xchange.platform.service.ProductService;
import com.xchange.platform.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "商品管理", description = "商品发布、查询、编辑等接口")
public class ProductController {

    private final ProductService productService;

    /**
     * 发布商品
     * POST /api/products
     * 需要JWT认证（自动从token获取sellerId）
     */
    @PostMapping
    @Operation(summary = "发布商品", description = "需要携带有效的JWT Token，自动绑定当前用户为卖家")
    public Result<ProductVO> publishProduct(
            @RequestAttribute("userId") Long sellerId,
            @Valid @RequestBody ProductDTO productDTO) {

        try {
            ProductVO productVO = productService.publishProduct(sellerId, productDTO);
            return Result.success("商品发布成功", productVO);
        } catch (RuntimeException e) {
            log.warn("商品发布失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("商品发布异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 分页查询商品列表
     * GET /api/products
     * 支持多条件筛选、排序
     * 接口示例:
     * /api/products?pageNum=1&pageSize=10&name=笔记本&status=1&sortBy=price&asc=false
     */
    @GetMapping
    @Operation(summary = "分页查询商品列表",
            description = "支持商品名称模糊查询、状态筛选、排序等")
    public Result<IPage<ProductVO>> getProductPage(@Valid ProductQueryDTO queryDTO) {

        try {
            IPage<ProductVO> pageResult = productService.getProductPage(queryDTO);

            // 添加友好的空数据提示
            if (pageResult.getTotal() == 0) {
                return Result.success("暂无商品数据", pageResult);
            }

            return Result.success("查询成功", pageResult);

        } catch (RuntimeException e) {
            log.warn("商品查询失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("商品查询异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 查询商品详情
     * GET /api/products/{id}
     * 公开接口，无需认证
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情", description = "公开接口")
    public Result<ProductVO> getProductDetail(@PathVariable("id") Long productId) {

        try {
            ProductVO productVO = productService.getProductDetail(productId);
            return Result.success("查询成功", productVO);
        } catch (RuntimeException e) {
            log.warn("查询失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 修改商品信息
     * PUT /api/products/{id}
     * 需要JWT认证，只能修改自己的商品
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改商品信息", description = "需要JWT认证，只能修改自己发布的商品")
    public Result<ProductVO> updateProduct(
            @RequestAttribute("userId") Long sellerId,
            @PathVariable("id") Long productId,
            @Valid @RequestBody UpdateProductDTO updateDTO) {

        try {
            ProductVO productVO = productService.updateProduct(sellerId, productId, updateDTO);
            return Result.success("商品修改成功", productVO);
        } catch (RuntimeException e) {
            log.warn("修改失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("修改异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 下架商品（逻辑删除）
     * DELETE /api/products/{id}
     * 需要JWT认证，只能下架自己的商品
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "下架商品", description = "需要JWT认证，执行逻辑删除，只能下架自己发布的商品")
    public Result<Void> removeProduct(
            @RequestAttribute("userId") Long sellerId,
            @PathVariable("id") Long productId) {

        try {
            productService.removeProduct(sellerId, productId);
            return Result.success("商品下架成功");
        } catch (RuntimeException e) {
            log.warn("下架失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("下架异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

}