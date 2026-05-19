package io.github.daixueyun3377.agentmark.core.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.daixueyun3377.agentmark.core.model.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 将调用追踪记录写入 JSON 文件。
 */
public class TraceWriter {

    private static final Logger log = LoggerFactory.getLogger(TraceWriter.class);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String storagePath;
    private final ObjectMapper mapper;

    public TraceWriter(String storagePath) {
        this.storagePath = storagePath;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);

        // 确保目录存在
        File dir = new File(storagePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 异步写入 trace 文件。文件名格式：{traceId}_{timestamp}.json
     */
    public void write(TraceRecord record) {
        String fileName = record.getTraceId() + "_" + LocalDateTime.now().format(FILE_FMT) + ".json";
        File file = new File(storagePath, fileName);

        try {
            mapper.writeValue(file, record);
            log.debug("Trace written: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write trace file: {}", file.getAbsolutePath(), e);
        }
    }
}
