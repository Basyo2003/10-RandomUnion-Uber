package com.team10.uber.driver.adapter;

public interface NoSqlAdapter<S, T> {
    T adapt(S source);
}
