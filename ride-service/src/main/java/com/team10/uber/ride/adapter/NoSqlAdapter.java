package com.team10.uber.ride.adapter;

public interface NoSqlAdapter<S, T> {
    T adapt(S source);
}
