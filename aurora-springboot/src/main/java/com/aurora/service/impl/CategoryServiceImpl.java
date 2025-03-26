package com.aurora.service.impl;

import com.aurora.model.dto.CategoryAdminDTO;
import com.aurora.model.dto.CategoryDTO;
import com.aurora.model.dto.CategoryOptionDTO;
import com.aurora.entity.Article;
import com.aurora.entity.Category;
import com.aurora.exception.BizException;
import com.aurora.mapper.ArticleMapper;
import com.aurora.mapper.CategoryMapper;
import com.aurora.service.CategoryService;
import com.aurora.util.BeanCopyUtil;
import com.aurora.util.PageUtil;
import com.aurora.model.vo.CategoryVO;
import com.aurora.model.vo.ConditionVO;
import com.aurora.model.dto.PageResultDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Override
    public List<CategoryDTO> listCategories() {
        return categoryMapper.listCategories();
    }

    @SneakyThrows
    @Override
    public PageResultDTO<CategoryAdminDTO> listCategoriesAdmin(ConditionVO conditionVO) {
        Integer count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Category::getCategoryName, conditionVO.getKeywords()));
        // like(boolean condition, R column, Object val)，like()方法的源码第一条是条件判断，如果keywords不是null或空字符串就和分类名进行模糊匹配，否则相当于全量查询。
        if (count == 0) {
            return new PageResultDTO<>();
        }
        // 相比于前台的分类查询，后台都是分页查。
        List<CategoryAdminDTO> categoryList = categoryMapper.listCategoriesAdmin(PageUtil.getLimitCurrent(), PageUtil.getSize(), conditionVO);
        return new PageResultDTO<>(categoryList, count);
    }

    @SneakyThrows
    @Override
    public List<CategoryOptionDTO> listCategoriesBySearch(ConditionVO conditionVO) {
        List<Category> categoryList = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Category::getCategoryName, conditionVO.getKeywords())
                .orderByDesc(Category::getId));
        return BeanCopyUtil.copyList(categoryList, CategoryOptionDTO.class);
    }


    @Override
    public void deleteCategories(List<Integer> categoryIds) {
        Integer count = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .in(Article::getCategoryId, categoryIds));
        if (count > 0) {
            throw new BizException("删除失败，该分类下存在文章");
        }
        categoryMapper.deleteBatchIds(categoryIds);
    }

    @Override
    public void saveOrUpdateCategory(CategoryVO categoryVO) {
        // 根据分类名查出来表里面分类id
        Category existCategory = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .select(Category::getId)
                .eq(Category::getCategoryName, categoryVO.getCategoryName()));
        // 如果存在并且id和新的id也相同，说明已经存在该名称的分类了。
        if (Objects.nonNull(existCategory) && !existCategory.getId().equals(categoryVO.getId())) {
            throw new BizException("分类名已存在");
        }
        // 两种情况：1、不存在该分类，说明是新增。2、存在该分类，且id相同，说明是修改。
        Category category = Category.builder()
                .id(categoryVO.getId())
                .categoryName(categoryVO.getCategoryName())
                .build();
        this.saveOrUpdate(category);
    }
}
