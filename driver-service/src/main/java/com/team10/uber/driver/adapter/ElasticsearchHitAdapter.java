package com.team10.uber.driver.adapter;

import com.team10.uber.driver.model.elastic.DriverSearchDocument;
import org.springframework.data.elasticsearch.core.SearchHit;

public class ElasticsearchHitAdapter implements NoSqlAdapter<SearchHit<DriverSearchDocument>, DriverSearchDocument> {
    @Override
    public DriverSearchDocument adapt(SearchHit<DriverSearchDocument> source) {
        return source.getContent();
    }
}
