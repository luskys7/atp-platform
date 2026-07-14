package com.atp.platform.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PageResult<T> {
    private java.util.List<T> list;
    private long total;
    private int page;
    private int pageSize;
}
