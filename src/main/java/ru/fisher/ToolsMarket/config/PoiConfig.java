package ru.fisher.ToolsMarket.config;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PoiConfig {

    @PostConstruct
    public void setup() {
        ZipSecureFile.setMinInflateRatio(0.001);
    }
}
