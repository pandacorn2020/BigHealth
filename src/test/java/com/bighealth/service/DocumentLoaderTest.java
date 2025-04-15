package com.bighealth.service;

import com.bighealth.util.FileConverter;
import org.junit.jupiter.api.Test;

public class DocumentLoaderTest {

    @Test
    public void readKgSystemPrompt() throws Exception {
        DocumentLoader loader = new DocumentLoader();
        String s = loader.readKgSystemPrompt();
        System.out.println(s);
    }

    @Test
    public void testSplit() {
        DocumentLoader loader = new DocumentLoader();
        String text = null;
        try {
            text = FileConverter.tikaConvertWordToText(
                    FileConverter.getResourcePath("1-中国糖尿病防治指南.docx").toUri().toURL().openStream(),
                    "1-中国糖尿病防治指南.docx");
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] split = loader.splitText(text);
        for (String s : split) {
            System.out.println(s);
            System.out.println("===================================");
        }
    }





}
