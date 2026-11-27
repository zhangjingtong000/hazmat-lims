package com.qingyan.hazmat.common;

import java.util.List;

public class TableData<T> {

    private long total;
    private List<T> records;

    public TableData() {
    }

    public TableData(long total, List<T> records) {
        this.total = total;
        this.records = records;
    }

    public static <T> TableData<T> of(List<T> records, long total) {
        return new TableData<>(total, records);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
