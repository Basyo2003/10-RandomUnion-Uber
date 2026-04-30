package com.team10.uber.payment.adapter;

public interface NoSqlAdapter<S, T> {
    T adapt(S source);
}
