package com.team10.uber.driver.controller;

import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.service.DriverDocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/driver-documents")
public class DriverDocumentController {

    private final DriverDocumentService driverDocumentService;

    public DriverDocumentController(DriverDocumentService driverDocumentService) {
        this.driverDocumentService = driverDocumentService;
    }

    @PostMapping("/driver/{driverId}")
    public DriverDocument createDriverDocument(@PathVariable Long driverId, @RequestBody DriverDocument driverDocument) {
        return driverDocumentService.createDriverDocument(driverId, driverDocument);
    }

    @GetMapping
    public List<DriverDocument> getAllDriverDocuments() {
        return driverDocumentService.getAllDriverDocuments();
    }

    @GetMapping("/{id}")
    public DriverDocument getDriverDocumentById(@PathVariable Long id) {
        return driverDocumentService.getDriverDocumentById(id);
    }

    @PutMapping("/{id}")
    public DriverDocument updateDriverDocument(@PathVariable Long id, @RequestBody DriverDocument updatedDriverDocument) {
        return driverDocumentService.updateDriverDocument(id, updatedDriverDocument);
    }

    @DeleteMapping("/{id}")
    public String deleteDriverDocument(@PathVariable Long id) {
        driverDocumentService.deleteDriverDocument(id);
        return "Driver document deleted successfully";
    }
}
