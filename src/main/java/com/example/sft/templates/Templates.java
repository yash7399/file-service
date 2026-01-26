package com.example.sft.templates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.example.sft.constants.GlobalConstants;




@Component
public class Templates {
	
	private static final Logger log = LogManager.getLogger(Templates.class);

	
	public String filesBody(List<String> failureFiles,List<String> successFiles, String batchDate) {

        try {
            String template = Files.readString(
                    Path.of(GlobalConstants.filesBodyPath)
            );

            String failureFilesList = buildFileList(failureFiles);
            
            String successFilesList = buildFileList(successFiles);

            return template
                    .replace("${batchDate}", batchDate)
                    .replace("${failureFiles}", failureFilesList)
                    .replace("${successFiles}", successFilesList);

        } catch (Exception e) {
        	log.error(e);
        	e.printStackTrace();
            throw new RuntimeException(
                    "Failed to build email body", e
            );
        }
    }

    private String buildFileList(List<String> files) {
        StringBuilder sb = new StringBuilder();
        int i=1;
        if( files==null || files.size()==0 ) return "";
        for (String file : files) {
            sb.append("> ").append(file).append("\n");
        }
        return sb.toString();
    }
	
    
    public synchronized String connectionBody(String batchDate) throws Exception {
    	System.out.println(batchDate);
    	try {
            String template = Files.readString(
                    Path.of(GlobalConstants.connectionBodyPath)
            );
            return template
                    .replace("${batchDate}", batchDate);

        } catch (Exception e) {
            System.out.println("Error in making email body "+e.getMessage());
//            log.error()
            e.printStackTrace();
            throw e;
        }
//		return "";
    }
}
