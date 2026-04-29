package com.team10.uber.location.adapter;

public interface NoSqlAdapter<S, T> {
    T adapt(S source);
}
