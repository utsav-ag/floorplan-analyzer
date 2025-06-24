package com.example.controller;

import com.example.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ImageProcessingController {

private final TextractClient textractClient;  
private final S3Client s3Client;  
private final DynamoDbClient dynamoDbClient;  
private final OkHttpClient httpClient;  
private final ObjectMapper objectMapper;  

private final String bucketName;  
private final String dynamoTable;  
private final String openaiApiKey;
private final String claudeApiKey;  
private final String region;
private HashMap<String, String> extractedValue; 

public ImageProcessingController() {  
    // Read AWS_REGION from env (or you can set via application.properties)  
    this.region = System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "us-east-1";  
    this.textractClient = TextractClient.builder().region(Region.of(region)).build();  
    this.s3Client = S3Client.builder().region(Region.of(region)).build();  
    this.dynamoDbClient = DynamoDbClient.builder().region(Region.of(region)).build();  
    this.httpClient = new OkHttpClient.Builder()
    .connectTimeout(100, TimeUnit.SECONDS) // time to establish the connection
    .readTimeout(600, TimeUnit.SECONDS) // time waiting for the server to send data
    .writeTimeout(600, TimeUnit.SECONDS) // time allowed for sending the request
    .build();  
    this.objectMapper = new ObjectMapper();
    this.extractedValue = new HashMap<>();

    // Read configuration from environment variables (or load from properties)  
    this.bucketName = System.getenv("S3_BUCKET_NAME") != null ? System.getenv("S3_BUCKET_NAME") : "floorplanimage";  
    this.dynamoTable = System.getenv("DYNAMO_TABLE") != null ? System.getenv("DYNAMO_TABLE") : "floorplan_analyzed_data";  
    this.openaiApiKey = System.getenv("OPENAI_API_KEY") != null ? System.getenv("OPENAI_API_KEY") : "";
    this.claudeApiKey = System.getenv("CLAUDE_API_KEY") != null ? System.getenv("CLAUDE_API_KEY") : "";
}  

@GetMapping("/")
public String test() {
    return "Working controller!";
}

@PostMapping("/upload")  
public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file) throws Exception {  
    // Process the file (save it to disk, upload to S3, etc.)  
    // For instance, log file details:  
    System.out.println("Received file: " + file.getOriginalFilename());
    // 1. Upload the image to S3

        // Create a unique identifier for this record  
        extractedValue = uploadImageToS3IfNotExists(file);
        
        String s3ImageUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + extractedValue.get("objectKey");

        //String s3ImageUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + objectkey;  

    // Return a JSON message  
    return new ResponseEntity<>(s3ImageUrl, HttpStatus.OK);  
} 

@PostMapping("/process-image")  
public ResponseEntity<?> processImage(@RequestBody ImageRequest request) {  
    try {  
 
        String modelName = request.getUseModel();  
        if (modelName == null || modelName.isEmpty()) {  
            return ResponseEntity.badRequest().body("modelName is required");  
        }  
        
        String id = extractedValue.get("objectKey");
        String s3ImageUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + id;
        
         // ------------- NEW: Check if result already exists -----------  
         AnalysisResult analysisResult = getAnalysisResultFromDynamoDB(id, modelName);  
         if (analysisResult != null) {  
             // Found in DB, just return it!  
             Map<String, Object> responseBody = new HashMap<>();  
             responseBody.put("id", extractedValue.get("fileHash"));  
             responseBody.put("imageUrl", s3ImageUrl);  
             responseBody.put("analysisResult", analysisResult);  
             responseBody.put("source", "dynamodb-cache");  
             return ResponseEntity.ok(responseBody);  
         }  
        
        // 1. Call AWS Textract to extract text from the image  
        // GetObjectRequest getObjectRequest = GetObjectRequest.builder()  
        //     .bucket(bucketName)  
        //     .key(id)  
        //     .build();   
        // ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);  
        // byte[] imageBytes = objectBytes.asByteArray();  
        // List<String> textBlocks = null;
        // textBlocks = callTextract(imageBytes);  

        // 2. Call OpenAI API to analyze the exhibition layout using the image and text data  
        analysisResult = callAI(s3ImageUrl, modelName);    

        // 3. Save the analyzed result to DynamoDB  
        putRecordToDynamoDB(id, s3ImageUrl, analysisResult, modelName);  

        // 4. Return the result  
        Map<String, Object> responseBody = new HashMap<>();  
        responseBody.put("id", extractedValue.get("fileHash"));  
        responseBody.put("imageUrl", s3ImageUrl);  
        responseBody.put("analysisResult", analysisResult);  

        return ResponseEntity.ok(responseBody);  
    } catch (Exception e) {  
        e.printStackTrace();  
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)  
                .body("Error processing image: " + e.getMessage());  
    }  
}  

private List<String> callTextract(byte[] imageBytes) {  
    try {  
        DetectDocumentTextRequest textractRequest = DetectDocumentTextRequest.builder()  
                .document(builder -> builder.bytes(SdkBytes.fromByteArray(imageBytes)))  
                .build();  

        DetectDocumentTextResponse textractResponse = textractClient.detectDocumentText(textractRequest);  
        List<String> textBlocks = new ArrayList<>();  
        if (textractResponse.blocks() != null) {  
            for (Block block : textractResponse.blocks()) {  
                if ("LINE".equals(block.blockTypeAsString()) && block.text() != null) {  
                    textBlocks.add(block.text());  
                }  
            }  
        }  
        return textBlocks;  
    } catch (Exception e) {  
        throw new RuntimeException("Textract error: " + e.getMessage(), e);  
    }  
}  

private AnalysisResult callAI(String imageDataUrl, String modelName) {  
    try {  
        // Construct messages following the style required by OpenAI Chat API  
        String sysMsg = "You are an expert exhibition layout analyzer. Carefully examine the floor plan image and:\\n" + //
                        "Identify ALL the booths and their precise coordinates in normalized values [0-1] relative to image dimensions. \\n" + //
                        "Booth names typically follow patterns like:\\n" + //
                        "- Alphanumeric codes (e.g., \\\"B12\\\", \\\"A-15\\\")\\n" + //
                        "- Sequential numbers in a series (e.g., \\\"100\\\", \\\"102\\\", \\\"104\\\") or general numbers\\n" + //
                        "- Section-based numbering (e.g., \\\"North-1\\\", \\\"ZoneC-12\\\")\\n" + //
                        "- Ignore generic labels like \\\"Parking\\\", \\\"Restrooms\\\", or \\\"Information Desk\\\" or words that has some meaning\\n" + //
                        "For EACH booth, provide:\\n" + //
                        "- Confidence score (0-1)\\n" + //
                        "- Coordinates of all four corners (top-left, top-right, bottom-right, bottom-left)\\n" + //
                        "- Any visible company names, booth sizes, or special features\\n" + //
                        "Analyze overall layout characteristics:\\n" + //
                        "- Total number of booths\\n" + //
                        "- Main aisle locations\\n" + //
                        "- Special areas (food courts, stages, etc.)\\n" + //
                        "          \\n" + //
                        "           Use this JSON structure:\\n" + //
                        "           {\\n" + //
                        "             \\\"booths\\\": [{\\n" + //
                        "               \\\"booth_number\\\": \\\"string\\\",\\n" + //
                        "               \\\"confidence\\\": number,\\n" + //
                        "               \\\"coordinates\\\": {\\n" + //
                        "                 \\\"top_left\\\": [x, y],\\n" + //
                        "                 \\\"top_right\\\": [x, y],\\n" + //
                        "                 \\\"bottom_left\\\": [x, y],\\n" + //
                        "                 \\\"bottom_right\\\": [x, y]\\n" + //
                        "               },\\n" + //
                        "               \\\"additional_info\\\": {\\n" + //
                        "                 \\\"company_name\\\": \\\"string\\\",\\n" + //
                        "                 \\\"booth_size\\\": \\\"string\\\",\\n" + //
                        "                 \\\"features\\\": [\\\"list\\\"]\\n" + //
                        "               }\\n" + //
                        "             }],\\n" + //
                        "             \\\"layout_characteristics\\\": {\\n" + //
                        "               \\\"total_booths\\\": number,\\n" + //
                        "               \\\"main_aisles\\\": number,\\n" + //
                        "               \\\"special_areas\\\": [\\\"list\\\"]\\n" + //
                        "             }\\n" + //
                        "           }";
        
        // String sysMsg = "You are a someone who answers questions asked";
        // String userMsg = "What is the capital of Spain";

        String userMsg = "Analyze this exhibition floor plan and extract all booth information with coordinates.";  

        Map<String, Object> payload = new HashMap<>();
        Request httpRequest;
        // Prepare the payload for OpenAI API
        if (modelName.contains("claude")) {

            payload = createCaludePayload(sysMsg, userMsg, modelName, imageDataUrl);
            System.out.println("Utsav claude payload ==========>"+ objectMapper.writeValueAsString(payload));
            MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                objectMapper.writeValueAsString(payload),
                jsonMediaType
                );
            
            httpRequest = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", claudeApiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(requestBody)
                .build();
            
        } else {
            payload = createOpenAIPayload(sysMsg, userMsg, modelName, imageDataUrl);
            System.out.println("Utsav openai payload ==========>"+ objectMapper.writeValueAsString(payload));
            MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                objectMapper.writeValueAsString(payload),
                jsonMediaType
            );

            httpRequest = new Request.Builder()  
            .url("https://api.openai.com/v1/chat/completions")  
            .addHeader("Authorization", "Bearer " + openaiApiKey)  
            .post(requestBody)  
            .build(); 
        }
          

        try (Response response = httpClient.newCall(httpRequest).execute()) {  
            if (!response.isSuccessful()) {  
                throw new IOException("Unexpected code " + response);  
            }  
            String responseString = response.body().string();
            System.out.println("Utsav AI response ==========>"+ responseString);  
            // Parse the OpenAI response to extract the returned message content.  
            JsonNode root = objectMapper.readTree(responseString);
            String content = ""; 
            if (modelName.contains("claude")) {
                JsonNode contentNode = root.path("content"); 
                content = contentNode.get(0).path("text").asText();
                content = content.substring(
                    content.indexOf("{"),
                    content.lastIndexOf("}") + 1
                );
            } else {
                JsonNode choices = root.path("choices");  
                if (choices.isMissingNode() || choices.size() == 0) {  
                throw new RuntimeException("No choices returned from OpenAI");
                }  
                JsonNode message = choices.get(0).path("message");  
                content = message.path("content").asText(); 
            }
            System.out.println("\n \n \n \n Utsav AI response ==========>"+ content);
            System.out.println("\n \n \n \n");  
            // Convert the returned JSON string into our AnalysisResult object  
            AnalysisResult analysisResult = objectMapper.readValue(content, AnalysisResult.class);  
            return analysisResult;  
        }  
    } catch (Exception e) {  
        throw new RuntimeException("AI error: " + e.getMessage(), e);  
    }  
} 

public Map<String, Object> createOpenAIPayload(String sysMsg, String userMsg, String modelName, String imageDataUrl) {
    Map<String, Object> payload = new HashMap<>();  
        
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        
        List<Map<String, Object>> messages = new ArrayList<>();  
        Map<String, Object> systemMessage = new HashMap<>();  
        systemMessage.put("role", "system");  
        systemMessage.put("content", sysMsg);  
        messages.add(systemMessage);  

        
        // We pack the image data URL and the extracted text blocks in the user message content  
        List<Map<String, Object>> userContent = new ArrayList<>();
        Map<String, Object> userInstruction = new HashMap<>();  
        userInstruction.put("text", userMsg);
        userInstruction.put("type", "text");  
        userContent.add(userInstruction);
        
        Map<String, Object> imageurl = new HashMap<>();
        imageurl.put("url", imageDataUrl);
        imageurl.put("detail", "high");
        
        Map<String, Object> imageData = new HashMap<>();
        imageData.put("type", "image_url");
        imageData.put("image_url", imageurl);  
        userContent.add(imageData);
        

        Map<String, Object> userMessage = new HashMap<>();  
        userMessage.put("role", "user");  
        userMessage.put("content", userContent);  
        messages.add(userMessage);  

        payload.put("model", modelName); // change as necessary
        payload.put("response_format", responseFormat);
        payload.put("messages", messages);

    return payload;
}

public Map<String, Object> createCaludePayload(String sysMsg, String userMsg, String modelName, String imageDataUrl) {
    Map<String, Object> payload = new HashMap<>();  
        
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        
        List<Map<String, Object>> messages = new ArrayList<>();  
        
        // We pack the image data URL and the extracted text blocks in the user message content  
        List<Map<String, Object>> userContent = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", userMsg);
        userContent.add(textContent);
        
        // Add image content
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image");
        
        Map<String, Object> source = new HashMap<>();
        source.put("type", "url");
        source.put("url", imageDataUrl);
        
        imageContent.put("source", source);
        userContent.add(imageContent);
        

        Map<String, Object> userMessage = new HashMap<>();  
        userMessage.put("role", "user");  
        userMessage.put("content", userContent);  
        messages.add(userMessage);  

        payload.put("model", modelName);
        payload.put("max_tokens", 20000);
        payload.put("temperature", 1);
        payload.put("system", sysMsg);  
        payload.put("messages", messages);

    return payload;
}

// Call this method to upload if not already present.  
public HashMap<String, String> uploadImageToS3IfNotExists(MultipartFile file) throws Exception {  
    // Compute a unique hash for the image  
    String fileHash = calculateMD5(file);  
    // Optionally preserve extension from local file name
    String ext = getFileExtension(file.getOriginalFilename());
    String objectKey = "images/" + fileHash + ext;
    HashMap<String, String> extractedValue = new HashMap<>();
    extractedValue.put("objectKey", objectKey);
    extractedValue.put("fileHash", fileHash);
    extractedValue.put("ext", ext);
    
    // check if an object with that key exists  
    if (!doesObjectExist(objectKey)) {  
        // 3. If it doesn't exist, upload the image.  
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()  
                .bucket(bucketName)  
                .key(objectKey)
                .acl(ObjectCannedACL.PUBLIC_READ)  
                .build();  
        s3Client.putObject(putObjectRequest,software.amazon.awssdk.core.sync.RequestBody.fromInputStream(file.getInputStream() , file.getSize()));    
    } else {  
        System.out.println("Image already exists. Object key: " + objectKey);  
    }
    return extractedValue;
}

// Uses headObject to check if the object exists.  
private boolean doesObjectExist(String objectKey) {  
    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()  
            .bucket(bucketName)  
            .key(objectKey)  
            .build();  
    try {  
        HeadObjectResponse headObjectResponse = s3Client.headObject(headObjectRequest);  
        // If the call succeeds, the object exists.  
        return true;  
    } catch (S3Exception e) {  
        // When the object is not found, headObject throws an exception with a 404 status.  
        if (e.statusCode() == 404) {  
            return false;  
        } else {  
            throw e;  
        }  
    }  
} 

// Helper method to calculate MD5 hash of the file.  
private String calculateMD5(MultipartFile file) throws Exception {  
    MessageDigest md = MessageDigest.getInstance("MD5");  
    try (InputStream is = file.getInputStream();  
         DigestInputStream dis = new DigestInputStream(is, md)) {  
        byte[] buffer = new byte[1024];  
        // read the file completely to update the digest  
        while (dis.read(buffer) != -1) { }  
    }  
    byte[] digest = md.digest();  
    // Convert the byte array into a hex string  
    BigInteger bigInt = new BigInteger(1, digest);  
    String hashtext = bigInt.toString(16);  
    // Zero pad to ensure 32-character length for MD5  
    while(hashtext.length() < 32 ){  
        hashtext = "0" + hashtext;  
    }  
    return hashtext;  
}  

// Helper method to extract file extension (including the dot)  
private String getFileExtension(String fileName) {  
    int lastIndex = fileName.lastIndexOf('.');  
    if (lastIndex > 0 && lastIndex < fileName.length() - 1) {  
        return fileName.substring(lastIndex);  
    }  
    return "";  
}  

private void putRecordToDynamoDB(String id, String imageUrl, AnalysisResult result, String modelName) { 

    try {  
        Map<String, AttributeValue> item = new HashMap<>();  
        item.put("modelName", AttributeValue.builder().s(modelName).build());  
        item.put("floorplanId", AttributeValue.builder().s(id.replace("images/", "")).build());  
        item.put("timestamp", AttributeValue.builder().s(Instant.now().toString()).build());  
        item.put("imageUrl", AttributeValue.builder().s(imageUrl).build());  
        item.put("analysisResult", AttributeValue.builder().s(objectMapper.writeValueAsString(result)).build());  

        PutItemRequest request = PutItemRequest.builder()  
                .tableName(dynamoTable)  
                .item(item)  
                .build();  
        dynamoDbClient.putItem(request);  
    } catch (Exception e) {  
        throw new RuntimeException("DynamoDB error: " + e.getMessage(), e);  
    }  
}

private AnalysisResult getAnalysisResultFromDynamoDB(String floorplanId, String modelName) {  
    try {  
        Map<String, AttributeValue> key = new HashMap<>();  
        key.put("floorplanId", AttributeValue.builder().s(floorplanId.replace("images/", "")).build());  
        key.put("modelName", AttributeValue.builder().s(modelName).build());  
  
        // Use getItem to retrieve if exists  
        GetItemRequest request = GetItemRequest.builder()  
            .tableName(dynamoTable)  
            .key(key)  
            .build();  
        Map<String, AttributeValue> item = dynamoDbClient.getItem(request).item();  
        if (item == null || item.isEmpty()) return null; // Not found  
  
        String resultJson = item.get("analysisResult").s();  
        return objectMapper.readValue(resultJson, AnalysisResult.class);  
  
    } catch (Exception e) {  
        throw new RuntimeException("Error reading DynamoDB: " + e.getMessage(), e);  
    }  
} 

}
