package com.bighealth.util;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.apache.tika.Tika;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileConverter {

    public static Path getResourcePath(String resourceFileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceFileName);
        return Paths.get(resource.getURI());
    }

    public static String convertFileToText(InputStream inputStream, String fileName) throws IOException {
        if (fileName.endsWith(".pdf")) {
            return convertPdfToText(inputStream);
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            return convertWordToText(inputStream, fileName);
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            return convertExcelToText(inputStream, fileName);
        } else {
            // read input stream as text
            return new String(inputStream.readAllBytes(), "UTF-8");
        }
    }

    public static String convertPdfToText(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        }
    }

    public static String convertWordToText(InputStream inputStream, String fileName) throws IOException {
        if (fileName.endsWith(".doc")) {
            try (HWPFDocument document = new HWPFDocument(inputStream)) {
                WordExtractor extractor = new WordExtractor(document);
                return extractor.getText();
            }
        } else if (fileName.endsWith(".docx")) {
            try (XWPFDocument document = new XWPFDocument(inputStream)) {
                XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                return extractor.getText();
            }
        } else {
            throw new IllegalArgumentException("The specified file is not a Word document");
        }
    }

    public static String tikaConvertWordToText(InputStream inputStream, String fileName) throws IOException {
        try {
            Tika tika = new Tika();
            // 提取文本
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String convertExcelToText(InputStream inputStream, String fileName) throws IOException {
        StringBuilder text = new StringBuilder();
        try (Workbook workbook = fileName.endsWith(".xls") ? new HSSFWorkbook(inputStream) : new XSSFWorkbook(inputStream)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        text.append(cell.toString()).append("\t");
                    }
                    text.append("\n");
                }
            }
        }
        return text.toString();
    }

    public static String convertExcelToJson(Path excelFilePath) throws IOException{
        List<Map<String, String>> sheetData = new ArrayList<>();
        InputStream inputStream = Files.newInputStream(excelFilePath);
        try (InputStream fis = Files.newInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // Read the first sheet
            Row headerRow = sheet.getRow(0); // Assume the first row contains headers

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Iterate through rows
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < headerRow.getLastCellNum(); j++) { // Iterate through columns
                    Cell headerCell = headerRow.getCell(j);
                    Cell cell = row.getCell(j);

                    String header = headerCell != null ? headerCell.getStringCellValue() : "Column" + j;
                    String value = cell != null ? getCellValueAsString(cell) : "";

                    rowData.put(header, value);
                }
                sheetData.add(rowData);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(sheetData); // Convert to JSON string
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    public static void main(String[] args) {
        try {
            Path resourcePath = Paths.get("C:/temp/维生素缺乏症.xlsx");
            String jsonOutput = convertExcelToJson(resourcePath);
            System.out.println(jsonOutput);
            Files.writeString(Paths.get("C:/temp/维生素缺乏症.json"), jsonOutput);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}