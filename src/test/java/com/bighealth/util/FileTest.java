package com.bighealth.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bighealth.service.DocumentLoader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileTest {
    public static void convertExcelToJson(String excelFilePath, String jsonFilePath) throws Exception {
        InputStream fileInputStream = DocumentLoader.getInputStream(excelFilePath);
        Workbook workbook = new XSSFWorkbook(fileInputStream);
        Sheet sheet = workbook.getSheetAt(0);

        List<Map<String, String>> data = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        int numOfColumns = headerRow.getPhysicalNumberOfCells();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            Map<String, String> rowData = new HashMap<>();
            for (int j = 0; j < numOfColumns; j++) {
                Cell cell = row.getCell(j);
                String header = headerRow.getCell(j).getStringCellValue();
                String value = cell != null ? cell.toString() : "";
                rowData.put(header, value);
            }
            data.add(rowData);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new FileOutputStream(jsonFilePath), data);

        workbook.close();
        fileInputStream.close();
    }

    @Test
    public void testConvertExcelToJson() {
        try {
            convertExcelToJson("0辨证功能调理方案研究第六版20250302.xlsx", "/temp/file.json");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
