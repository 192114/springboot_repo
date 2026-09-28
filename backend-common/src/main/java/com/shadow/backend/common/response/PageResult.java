package com.shadow.backend.common.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {

    private long current;
    private long size;
    private long total;
    private long pages;
    private List<T> records;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords() != null ? page.getRecords() : Collections.emptyList());
        return result;
    }

    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setCurrent(current);
        result.setSize(size);
        result.setTotal(total);
        result.setPages(size == 0 ? 0 : (total + size - 1) / size);
        result.setRecords(records != null ? records : Collections.emptyList());
        return result;
    }
}
