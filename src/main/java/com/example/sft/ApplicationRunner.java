package com.example.sft;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

import javax.management.RuntimeErrorException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.example.sft.config.FileConfig;
import com.example.sft.constants.GlobalConstants;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.jcraft.jsch.ChannelSftp;

@Component
public class ApplicationRunner implements CommandLineRunner {

	private static final Logger log = LogManager.getLogger(ApplicationRunner.class);

	private static String batchDate;
	private static String configPath;

	@Autowired
	Util util;

	@Autowired
	Emails emails;

	@Autowired
	FileTransferService File_Transfer_Test;

	private final ConcurrentMap<String, List<String>> missingFiles = new ConcurrentHashMap<>();

	private final ConcurrentMap<String, List<String>> transferedFiles = new ConcurrentHashMap<>();

	private List<String> departments = new ArrayList();

	public static String getBatchDate() {
		return batchDate;
	}
	
	public static String getConfigPath() {
		return configPath;
	}
	
	
//	@Override
//	public void run(String...args) {
//		this.batchDate = args[0];
//		this.configPath=args[2];
//		System.out.println(configPath);
//		Prop.init(configPath);
//	
////		System.out.println(prop.getProperty("01.ip"));
//		emails.connectionIssue();
//	}

	@Override
	public void run(String... args) {
		
		if(args.length<3) {
			log.info("Please enter correct arguments ");
			System.exit(1);
		}
		
		this.batchDate = args[0];
		
		String connectionCode=args[1];
		
		this.configPath=args[2];
		Prop.init(configPath);
		Properties prop=Prop.getProp();
		
		Path path = Paths.get(prop.getProperty("path.log4j2"));
		
		if (!Files.exists(path)) {
			throw new RuntimeErrorException(null, "Log4j2.xml file does not exists " + path);
		}
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		
		URI configUri = Path.of(prop.getProperty("path.log4j2")).toUri();
		
		context.setConfigLocation(configUri);
		
		
		

		log.info("*********** APPLICATION STARTED ***********");
		System.out.println("*********** APPLICATION STARTED ***********");
		
		int noOfThreads;
		if(GlobalConstants.no_of_threads==0) {
			noOfThreads=1;
			log.info("Give proper no of threads");
		}
		else {
			noOfThreads=GlobalConstants.no_of_threads;
		}
		ExecutorService threadPool = Executors.newFixedThreadPool(noOfThreads);

		try {
			
			Map<String,Map<String, List<FileConfig>>> departmentFiles = util.getMap();
			
			for (Map.Entry<String, Map<String, List<FileConfig>>> entry : departmentFiles.entrySet()) {
				
				String department = entry.getKey();
				Map<String, List<FileConfig>> records = entry.getValue();
				departments.add(department);
				
				threadPool.submit(() -> processDepartment(connectionCode , department, records, args));
			}

			threadPool.shutdown();

			while (!threadPool.awaitTermination(5, TimeUnit.MINUTES)) {
				log.info("Waiting for thread for ending task");
			}
			log.info("*********** APPLICATION FINISHED ***********");

			System.exit(1);

		} catch (Exception e) {

			System.err.println(" UNEXPECTED ERROR: " + e.getMessage());
			log.error("Unexpected error", e);

			emails.connectionIssue();
			System.exit(1);
		}
	}

	private void processDepartment(String connectionCode,String department, Map<String, List<FileConfig>> connections, String... args) {
		
		Properties prop=Prop.getProp();
		
		DiskShare share = null;
		
		for(Map.Entry<String, List<FileConfig>> entry:connections.entrySet()) {
			
			ChannelSftp sftp=null;
			if(connectionCode.equals("00")  || connectionCode.equals(entry.getKey())) {

				 sftp = SFTPConnection.connect(entry.getKey());
			}
			else {
				return;
			}
			Session session = NASConnection.connect();
			
			log.info("Department execution started:" + department);
			System.out.println("Department execution started:" + department);
			
			try {
				share = (DiskShare) session.connectShare(entry.getValue().get(0).getShare());
			} catch (Exception e) {
				System.out.println("Share " + entry.getValue().get(0).getShare() + " is not connected");
				log.info("Share " + entry.getValue().get(0).getShare() + " is not connected");
				emails.connectionIssue();
			}
			
			for (FileConfig record : entry.getValue()) {
				
				String fileName = util.getFileNameWithDate(record.getFilename(), record.getTime(), args[0]);
				
				if(fileName==record.getFilename()) {
					log.info("Cannot update Date in file name ");
					continue;
				}
				System.out.println("Transfering file "+fileName);
				log.info("Transfering file "+fileName);
				try {
					File_Transfer_Test.transfer(sftp,entry.getKey(), share, fileName, record.getShare(), record.getSource(),
							record.getDestination(), record.getTime(), department, missingFiles, transferedFiles);
					
				} catch (Exception e) {
					
					log.error("File transfer failed for {} in department {}", fileName, department, e);
				}
			}
		}

		
		emails.filesNotFound(missingFiles, transferedFiles, department);
		

		String triggerFolder = prop.getProperty("trigger.folder." + department);

		try {
			if(triggerFolder!=null) {
				
				Trigger.makeTriggerFile(share, triggerFolder);
				System.out.println("Sucessfully created trigger file for " + department);
				log.info("Sucessfully created trigger file for " + department);
			}
			else {
				log.info("Trigger folder is null. Cannot create trigger file ");
			}
		} catch (Exception e) {
			System.out.println("Cannot create trigger file for department " + department);
			log.info("Cannot create trigger file for department " + department);
			e.printStackTrace();
		}
	}
}