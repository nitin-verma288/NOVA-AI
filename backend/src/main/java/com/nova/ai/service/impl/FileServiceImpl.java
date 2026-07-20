package com.nova.ai.service.impl;

import com.nova.ai.dto.DocumentDto;
import com.nova.ai.entity.Document;
import com.nova.ai.entity.User;
import com.nova.ai.exception.BadRequestException;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.DocumentRepository;
import com.nova.ai.service.FileService;
import com.nova.ai.service.OllamaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    private final DocumentRepository documentRepository;
    private final OllamaService ollamaService;

    public FileServiceImpl(DocumentRepository documentRepository, OllamaService ollamaService) {
        this.documentRepository = documentRepository;
        this.ollamaService = ollamaService;
    }

    @Override
    @Transactional
    public DocumentDto uploadAndProcessFile(User user, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new BadRequestException("Invalid file name");
        }

        String fileType = getFileExtension(fileName);
        long fileSize = file.getSize();
        String parsedText;

        try (InputStream is = file.getInputStream()) {
            switch (fileType.toLowerCase()) {
                case "pdf":
                    parsedText = parsePdf(file.getBytes());
                    break;
                case "docx":
                    parsedText = parseDocx(is);
                    break;
                case "xls":
                case "xlsx":
                    parsedText = parseExcel(is);
                    break;
                case "csv":
                    parsedText = parseCsv(is);
                    break;
                case "txt":
                    parsedText = parseTxt(is);
                    break;
                default:
                    throw new BadRequestException("Unsupported file type: ." + fileType);
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Failed to parse file: " + fileName, e);
            throw new RuntimeException("File parsing error: " + e.getMessage());
        }

        if (parsedText == null || parsedText.trim().isEmpty()) {
            throw new BadRequestException("Extracted document content is empty!");
        }

        // Generate summary of file content using local model
        String model = user.getSettings() != null ? user.getSettings().getModelName() : "gemma3:4b";
        String summaryPrompt = "Extract 3 to 5 core bullet points summarizing the following document details:\n\n" +
                (parsedText.length() > 4000 ? parsedText.substring(0, 4000) : parsedText);

        String summary = ollamaService.generateText(summaryPrompt, model, 0.4);

        Document doc = Document.builder()
                .user(user)
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(fileSize)
                .fileContentText(parsedText)
                .summary(summary)
                .build();

        Document savedDoc = documentRepository.save(doc);
        return mapToDto(savedDoc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getAllDocuments(User user) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(User user, String docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!doc.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized access to document");
        }

        return mapToDto(doc);
    }

    @Override
    @Transactional
    public void deleteDocument(User user, String docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!doc.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized access to document");
        }

        documentRepository.delete(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public String askDocumentQuestion(User user, String docId, String question) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!doc.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized access to document");
        }

        String fileContent = doc.getFileContentText();
        String context = getRelevantContext(fileContent, question);

        String ragPrompt = "You are a helpful assistant reading the document '" + doc.getFileName() + "'.\n" +
                "Use the following relevant excerpt context to answer the user's question.\n" +
                "If the answer cannot be found in the context, mention that but answer using general knowledge where appropriate.\n\n" +
                "Context excerpts:\n" + context + "\n\n" +
                "User's Question: " + question + "\n\n" +
                "Detailed Answer:";

        String model = user.getSettings() != null ? user.getSettings().getModelName() : "gemma3:4b";
        return ollamaService.generateText(ragPrompt, model, 0.7);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDocumentContent(User user, String docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!doc.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized access to document");
        }
        return doc.getFileContentText();
    }

    private String getFileExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index > 0) {
            return fileName.substring(index + 1);
        }
        return "";
    }

    private String parsePdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String parseExcel(InputStream is) throws Exception {
        StringBuilder content = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(is)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("[Sheet: ").append(sheet.getSheetName()).append("]\n");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        content.append(getCellValueAsString(cell)).append("\t");
                    }
                    content.append("\n");
                }
                content.append("\n");
            }
        }
        return content.toString();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }

    private String parseCsv(InputStream is) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String parseTxt(InputStream is) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String getRelevantContext(String fileContent, String question) {
        if (fileContent.length() <= 8000) {
            return fileContent;
        }

        // Segment text by double newlines into paragraphs
        String[] paragraphs = fileContent.split("\n\\s*\n");
        String[] questionWords = question.toLowerCase().split("\\s+");

        Map<String, Integer> paragraphScores = new HashMap<>();
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) continue;
            int score = 0;
            String lowerParagraph = paragraph.toLowerCase();
            for (String word : questionWords) {
                if (word.length() > 3 && lowerParagraph.contains(word)) {
                    score++;
                }
            }
            paragraphScores.put(paragraph, score);
        }

        List<String> sortedParagraphs = paragraphScores.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(Map.Entry::getKey)
                .limit(5)
                .collect(Collectors.toList());

        return String.join("\n\n---\n\n", sortedParagraphs);
    }

    private DocumentDto mapToDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .summary(doc.getSummary())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
