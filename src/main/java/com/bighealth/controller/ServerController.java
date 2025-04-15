package com.bighealth.controller;

import com.bighealth.entity.KGEntity;
import com.bighealth.service.GraphBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kg")
public class ServerController {

    @Autowired
    private GraphBuilder graphBuilder;


    @GetMapping("/getAllEntities")
    public List<KGEntity> getAllEntities() {
        return null;
    }

    @PostMapping("/buildGraph")
    public String buildGraph(@RequestBody String schemaName) {
        return null;
    }

    @PostMapping("/buildAllGraphs")
    public String buildAllGraphs() {
        graphBuilder.buildAllGraphs();
        return "success";
    }

}