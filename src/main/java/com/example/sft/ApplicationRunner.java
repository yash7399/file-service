package com.ncdex.filetransfer;

import java.io.IOException;
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

import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.jcraft.jsch.ChannelSftp;
import com.ncdex.filetransfer.config.FileConfig;
import com.ncdex.filetransfer.connections.NASConnection;
import com.ncdex.filetransfer.connections.SFTPConnection;
import com.ncdex.filetransfer.constants.GlobalConstants;
import com.ncdex.filetransfer.emails.Emails;
import com.ncdex.filetransfer.utils.Prop;
import com.ncdex.filetransfer.utils.Trigger;
import com.ncdex.filetransfer.utils.Utils;

@Component
public class ApplicationRunner implements CommandLineRunner {

	private static final Logger log = LogManager.getLogger(ApplicationRunner.class);

	private static String batchDate;
//	private static String configPath;

	@Autowired
	Utils util;

	@Autowired
	Emails emails;

	@Autowired
	FileTransferService File_Transfer_Test;

	private final static ConcurrentMap<String, List<String>> missingFiles = new ConcurrentHashMap<>();

	private final static ConcurrentMap<String, List<String>> transferedFiles = new ConcurrentHashMap<>();

	private static List<String> departments = new ArrayList();

	public static String getBatchDate() {
		return batchDate;
	}

	public static ConcurrentMap<String, List<String>> getMissingFiles() {
		return missingFiles;
	}

	public static ConcurrentMap<String, List<String>> getTransferedFiles() {
		return transferedFiles;
	}

	public static List<String> getDepartments() {
		return departments;
	}

//	@Override
//	public void run(String... args) throws Exception {
//		if (args.length < 2) {
//			System.exit(1);
//		}
//
//		this.batchDate = args[0];
//
//		String connectionCode = args[1];
//
//		String configpath = null;
//
//		for (String arg : args) {
//			if (arg.startsWith("--spring.config.location=")) {
//
//				configpath = arg.split("=", 2)[1];
//				System.out.println(configpath);
//				break;
//			}
//
//		}
//
//		if (configpath == null) {
//			System.out.print("NO config path");
//			System.exit(1);
//		}
//
//		Prop.init(configpath);
//		Properties prop = Prop.getProp();
//
//		Path path = Paths.get(prop.getProperty("path.log4j2"));
//
//		if (!Files.exists(path)) {
//			throw new RuntimeErrorException(null, "Log4j2.xml file does not exists " + path);
//		}
//		LoggerContext context = (LoggerContext) LogManager.getContext(false);
//
//		URI configUri = Path.of(prop.getProperty("path.log4j2")).toUri();
//
//		context.setConfigLocation(configUri);
//
//		departments.add("Enforcement");
//
//		emails.connectionIssue();
//
//	}

	@Override
	public void run(String... args) {
		
		if(args.length<2) {
			System.exit(1);
		}
		
		this.batchDate = args[0];
		
		String connectionCode=args[1];
		
		
		String configpath=null;
		
		for(String arg:args) {
			if(arg.startsWith("--spring.config.location=")) {
				configpath=arg.split("=",2)[1];
				System.out.println(configpath);
				break;
			}
					
		}
		
		
		if(configpath==null) {
			System.out.print("NO config path");
			System.exit(1);
		}
		
		Prop.init(configpath);
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
			log.info("Give no of threads by default taking 1");
		}
		else {
			noOfThreads=GlobalConstants.no_of_threads;
		}
		ExecutorService threadPool = Executors.newFixedThreadPool(noOfThreads);

		try {
			
			Map<String,Map<String, List<FileConfig>>> departmentFiles = util.getMap();
			
			for (Map.Entry<String, Map<String, List<FileConfig>>> entry : departmentFiles.entrySet()) {
				departments.add(entry.getKey());
			}
			
			for (Map.Entry<String, Map<String, List<FileConfig>>> entry : departmentFiles.entrySet()) {
				
				String department = entry.getKey();
				Map<String, List<FileConfig>> records = entry.getValue();
				
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
		
		
		for(Map.Entry<String, List<FileConfig>> entry:connections.entrySet()) {
			
			ChannelSftp sftp=null;
			
			Session session=null;
			DiskShare share = null;
			
			if(connectionCode.equals("00")  || connectionCode.equals(entry.getKey())) {

				 sftp = SFTPConnection.connect(entry.getKey());
			}
			else {
				return;
			}
			session = NASConnection.connect();
			
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
					File_Transfer_Test.transfer(entry.getKey(),sftp,entry.getKey(), share, fileName, record.getShare(), record.getSource(),
							record.getDestination(), record.getTime(), department, missingFiles, transferedFiles);
					
				} catch (Exception e) {
					
					log.error("File transfer failed for {} in department {}", fileName, department, e);
				}
			}
			String triggerFolder = prop.getProperty("trigger.folder." + department);
			
			try {
				if(triggerFolder!=null) {
					Trigger.makeTriggerFile(share, triggerFolder,entry.getKey());
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
			finally {
				try {
					share.close();
					session.close();
					sftp.disconnect();
					log.info("Every session is closed");
				} catch (IOException e) {
					log.error("Error while closing sessions");
				}
			}
		}
		emails.filesNotFound(missingFiles, transferedFiles, department,false);

	}
}
