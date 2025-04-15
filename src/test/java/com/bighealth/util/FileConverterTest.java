package com.bighealth.util;

import com.bighealth.service.DocumentLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

public class FileConverterTest {

    @Test
    public void testConvertPdfToText() {
        try {
            InputStream is = DocumentLoader.getInputStream("china-economic-monitor-q2-2024.pdf");
            String text = FileConverter.convertPdfToText(is);
            System.out.println(text);
            Assertions.assertTrue(text.contains("一季度中国GDP"));
            Assertions.assertTrue(text.contains("国家药品监督管理局发布"));

        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to convert PDF to text");
        }
    }

    @Test
    public void testConvertWordToText() {
        try {
            InputStream is = DocumentLoader.getInputStream("100一个散结方.docx");
            String text = FileConverter.convertWordToText(is, "100一个散结方.docx");
            System.out.println(text);
            //Assertions.assertTrue(text.contains("一季度中国GDP"));
            //Assertions.assertTrue(text.contains("国家药品监督管理局发布"));

        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to convert Word to text");
        }
    }
}
