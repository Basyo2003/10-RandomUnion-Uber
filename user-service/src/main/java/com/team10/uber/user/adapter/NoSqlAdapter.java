package com.team10.uber.user.adapter;

public interface NoSqlAdapter<S, T> {
    T adapt(S source);
}
