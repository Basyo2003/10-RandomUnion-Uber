package com.team10.uber.driver.repository.elastic;

import com.team10.uber.driver.model.elastic.DriverSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DriverSearchRepository
        extends ElasticsearchRepository<DriverSearchDocument, String> {
    // Custom query methods added later in A-13
}
