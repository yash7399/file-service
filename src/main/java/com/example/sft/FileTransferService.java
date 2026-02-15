package com.ncdex.filetransfer;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.management.RuntimeErrorException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.ncdex.filetransfer.connections.NASConnection;
import com.ncdex.filetransfer.connections.SFTPConnection;
import com.ncdex.filetransfer.constants.GlobalConstants;
import com.ncdex.filetransfer.emails.Emails;
import com.ncdex.filetransfer.utils.Prop;

@Component
public class FileTransferService {

	@Autowired
	Emails emails;

	Properties prop = null;

	private static final Logger log = LogManager.getLogger(FileTransferService.class);

	public void transfer(String serverCode, ChannelSftp sftp, String connectionCode, DiskShare share, String fileName,
			String smbShareName, String sourcePath, String destinationPath, int time, String department,
			Map<String, List<String>> missingFiles, Map<String, List<String>> transferedFiles) throws Exception {

		try {
			System.out.println(sourcePath);
			sftp.stat(sourcePath);
		} catch (Exception e) {
			missingFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(fileName);
			System.out.println("Missing or failed to transfer " + fileName);
			log.info("Missing or failed to transfer " + fileName);

			System.out.println(e);
			e.printStackTrace();
			return;
		}

		if (fileName.contains("N.")) {
			System.out.println(fileName);
			transferMultipleFiles(serverCode, sftp, connectionCode, share, smbShareName, fileName, sourcePath,
					destinationPath, time, department, missingFiles, transferedFiles);
		} else {
			System.out.println(fileName + " from single one");
			transferSingleFile(serverCode, sftp, connectionCode, share, smbShareName, fileName, sourcePath,
					destinationPath, time, department, missingFiles, transferedFiles);
		}
	}

	private void transferSingleFile(String serverCode, ChannelSftp sftp, String connectionCode, DiskShare share,
			String smbShareName, String fileName, String sourcePath, String destinationPath, int time,
			String department, Map<String, List<String>> missingFiles, Map<String, List<String>> transferedFiles) {

		String sftpFilePath = sourcePath + "/" + fileName;

		try {
			sftp.stat(sftpFilePath);

			copyFile(serverCode, sftp, connectionCode, share, smbShareName, fileName, sftpFilePath, destinationPath,
					time, department, missingFiles, transferedFiles);

			System.out.println(fileName + " transferred successfully");
			log.info(fileName + " Transfered sucessfully");
			transferedFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(fileName);
		}

		catch (Exception e) {
			missingFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(fileName);
			System.out.println("Missing or failed to transfer " + fileName);
			log.info("Missing or failed to transfer " + fileName);
		}

	}

	private void transferMultipleFiles(String serverCode, ChannelSftp sftp, String connectionCode, DiskShare share,
			String smbShareName, String templateFileName, String sourcePath, String destinationPath, int time,
			String department, Map<String, List<String>> missingFiles, Map<String, List<String>> transferedFiles)
			throws SftpException {

		try {

			String basename = templateFileName.substring(0, templateFileName.lastIndexOf('_'));

			String extension = templateFileName.substring(templateFileName.indexOf('.') + 1);

			Pattern pattern = buildPattern(templateFileName);

			if (pattern == null) {
				return;
			}

			String glob = basename + "_*" + "." + extension;

			Vector<ChannelSftp.LsEntry> files = sftp.ls(sourcePath + "/" + glob);

			int count = 0;
			if (files.size() > 0) {

				for (ChannelSftp.LsEntry entry : files) {

					if (entry.getAttrs().isDir()) {
						continue;
					}

					String currentFileName = entry.getFilename();

					if (pattern.matcher(currentFileName).matches()) {
						String sftpFilePath = sourcePath + "/" + currentFileName;
						try {

							copyFile(serverCode, sftp, connectionCode, share, smbShareName, currentFileName,
									sftpFilePath, destinationPath, time, department, missingFiles, transferedFiles);

							System.out.println(currentFileName + " transferred successfully");
							log.info(currentFileName + " Transfered sucessfully");
							transferedFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(currentFileName);
							count++;
						} catch (Exception e) {
							System.out
									.println("Failed to transfer file" + currentFileName + " due to " + e.getMessage());
							log.info("Failed to transfer file" + currentFileName + " due to " + e.getMessage());
							missingFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(sftpFilePath);
						}
					}
				}
			}

			if (count == 0) {
				missingFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(templateFileName);
				log.info(templateFileName + " added to missing files ");
			}
		} catch (SftpException e) {
			if (e.id == ChannelSftp.SSH_FX_PERMISSION_DENIED) {
				System.out.println("Error in transfering file : " + e.getMessage());
				log.error("Error in transfering file : " + e.getMessage());
				missingFiles.computeIfAbsent(department, k -> new ArrayList<>()).add(templateFileName);
				throw e;
			} else {
				log.error(e);
				emails.connectionIssue();
			}
		}
	}

	private void copyFile(String serverCode, ChannelSftp sftp, String connectionCode, DiskShare share,
			String smbShareName, String fileName, String sftpFilePath, String destinationPath, int time,
			String department, Map<String, List<String>> missingFiles, Map<String, List<String>> transferedFiles)
			throws Exception {

		System.out.println("Inside copy file");

		String[] folders = destinationPath.split("/");
		String currentPath = "";
		for (String folder : folders) {
			currentPath = currentPath.isEmpty() ? folder : currentPath + "/" + folder;
			try {
				share.mkdir(currentPath);
			} catch (SMBApiException e) {
				if (e.getStatus() != NtStatus.STATUS_OBJECT_NAME_COLLISION) {
					log.info("Error in making folder " + e);
				}
			}
		}

		String localTempDir = GlobalConstants.local_folder_temporary;
		if (localTempDir == null) {
			log.info("Local folder is null");
			return;
		}

		String localFilePath = localTempDir + "//" + fileName;

		boolean downloaded = true;

		try {
			java.nio.file.Files.createDirectories(java.nio.file.Paths.get(localTempDir));
		} catch (IOException dirEx) {
			System.err.println("Failed to create local temp directory: " + dirEx.getMessage());
			log.error("Failed to create local temp directory: " + dirEx.getMessage(), dirEx);
		}

		try {

			System.out.println(sftpFilePath);

			try (InputStream inputStream = sftp.get(sftpFilePath);
					OutputStream localOutputStream =new FileOutputStream(localFilePath,false) ) {

				byte[] buffer = new byte[2*1024*1024];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					localOutputStream.write(buffer, 0, bytesRead);
				}
				localOutputStream.flush();
				downloaded = true;
				System.out.println("Downloaded to local: " + localFilePath);

				    log.info(fileName + " downloaded locally to  " + localFilePath);

			}

//			ChannelSftp sftp2 = SFTPConnection.connect(serverCode);
//
//			System.out.println(localFilePath);
//
//			System.out.println("******************************************");
//			System.out.println("Password " + getProperty(serverCode + ".password"));
//			System.out.println("user " + getProperty(serverCode + ".user"));
//			System.out.println("ip " + getProperty(serverCode + ".ip"));
//			System.out.println("port " + getProperty(serverCode + ".port"));
//			System.out.println(destinationPath);
//
//			String remotePath = getProperty(serverCode + ".user") + "@" + getProperty(serverCode + ".ip") + ":"
//					+ sftpFilePath;
//
//			ProcessBuilder pb = new ProcessBuilder("scp", // Use scp for direct file transfers
//					"-i", getProperty(serverCode + ".privateKey"), "-P", getProperty(serverCode + ".port"), "-o",
//					"StrictHostKeyChecking=no", "-o", "BatchMode=yes", 
//					remotePath, localFilePath);
//
//			
//			Process process = pb.start();
//
//
//			InputStream errorStream = process.getErrorStream();
//			InputStream inputStream = process.getInputStream();
//
//
//			int exitCode = process.waitFor();
//
//			if (exitCode != 0) {
//				// Read error only if it failed to save memory
//				String stderr = new String(errorStream.readAllBytes());
//				log.error("Failed to download. Exit Code: " + exitCode + " Error: " + stderr);
//				throw new Exception("Transfer failed for " + sftpFilePath);
//			} else {
//				log.info("File" + sftpFilePath + " has been downloaded the local folder");
//			}
//			
////			System.exit(1);
//
//			Session smb = NASConnection.connect();
//			
//			ProcessBuilder pb3 = new ProcessBuilder(
//					"mkdir","-p","/nseit"+destinationPath
//					);
//
//			pb3.redirectErrorStream(true);
//
//			Process process3 = pb3.start();
//
//			int exitCode3 = process3.waitFor();
//
////			String stderr=new String(process.getErrorStream().readAllBytes());
//			String sout3 = new String(process3.getInputStream().readAllBytes());
//			System.out.println(sout3);
//			
//			if (exitCode3 != 0) {
//				log.error("Failed to download file from local to smb");
//				log.error(sout3);
//			}
//			
//			System.out.println("*************cp one1****************************");
//			
//			System.out.println("local file path "+localFilePath);
//			System.out.println("Destination path "+destinationPath);
//			
//			System.out.println("Command is "+"cp "+localFilePath + " " + "/nseit"+destinationPath);
//
//			ProcessBuilder pb2 = new ProcessBuilder(
//					"cp",localFilePath,"/nseit"+destinationPath
//					);
//
//			pb2.redirectErrorStream(true);
//			
//			pb2.inheritIO();
//
//			Process process2 = pb2.start();
//
//			int exitCode2 = process2.waitFor();
//
//			String stderr2=new String(process2.getErrorStream().readAllBytes());
//			String sout2 = new String(process2.getInputStream().readAllBytes());
//			System.out.println(sout2);
//			System.out.println(stderr2);
//			if (exitCode2 != 0) {
//				
//				log.error("Failed to download file from local to smb");
//				log.error(sout2);
//
//			}
//			else {
//				log.info("downloaded to the ");
//			}
//
			if (downloaded) {
				try (InputStream localInputStream = new FileInputStream(localFilePath);

						com.hierynomus.smbj.share.File smbFile = share.openFile(destinationPath + "\\" + fileName,
								EnumSet.of(AccessMask.FILE_WRITE_DATA),
								EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL), EnumSet.noneOf(SMB2ShareAccess.class),
								SMB2CreateDisposition.FILE_OVERWRITE_IF, null);

						OutputStream smbOutputStream =smbFile.getOutputStream()) {
					byte[] buffer = new byte[1024 * 1024];
					int bytesRead;
					while ((bytesRead = localInputStream.read(buffer)) != -1) {
						smbOutputStream.write(buffer, 0, bytesRead);
					}
					smbOutputStream.flush();

					System.out.println("Uploaded to SMB: " + destinationPath + "\\" + fileName);

					log.info("Uploaded to SMB: " + destinationPath + "\\" + fileName);

				}
			}
			deleteLocal(localFilePath);

		}
//		catch (SftpException e) {
//			
//			deleteLocal(localFilePath);
//			
//			if (e.id == ChannelSftp.SSH_FX_PERMISSION_DENIED) {
//				System.out.println("Error in transfering file : " + e.getMessage());
//				log.error("Error in transfering file : " + e.getMessage());
//				throw e;
//			}
//			else {
//				log.error(e);
//				sftp=SFTPConnection.connect(connectionCode);
//				Session smb=NASConnection.connect();
//				share=(DiskShare) smb.connectShare(smbShareName);
//			}
//		} 
		catch (IOException e) {

			deleteLocal(localFilePath);

			String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

			if (msg.contains("access is denied") || msg.contains("permission denied") || msg.contains("being used")) {
				System.out.println("Error in transfering file :  " + e.getMessage());
				log.error("Error in transfering file : " + e.getMessage());
				throw new Exception("Error in transfering file : " + e.getMessage());

			} else if (msg.contains("disk full") || msg.contains("no space left") || msg.contains("not enough space")
					|| msg.contains("status_disk_fullY")) {
				log.error("Disk size full");
				log.error(e);
				emails.connectionIssue();
			} else {
				log.error(e);
				Session smb = NASConnection.connect();
				share = (DiskShare) smb.connectShare(smbShareName);
			}

		} catch (Exception e) {
			deleteLocal(localFilePath);
			log.info(e);
			e.printStackTrace();
			throw e;
		}

	}

	private Pattern buildPattern(String fileName) {

		String extension = fileName.substring(fileName.indexOf('.') + 1);
		int digitCount = fileName.indexOf('.') - fileName.lastIndexOf('_') - 1;

		// Only allow N or NN
		if (digitCount > 2 || digitCount <= 0) {
			return null;
		}

		String digitRegex = "[0-9]{" + digitCount + "}";
		String baseName = fileName.substring(0, fileName.lastIndexOf('_'));

		return Pattern.compile("^" + baseName + "_" + digitRegex + "\\." + extension + "$");
	}

	public static int getBufferSize(long fileSize) {
		if (fileSize >= 2L * 1024 * 1024 * 1024)
			return 2 * 1024 * 1024;
		if (fileSize >= 512L * 1024 * 1024)
			return 1 * 1024 * 1024;
		if (fileSize >= 128L * 1024 * 1024)
			return 512 * 1024 * 1024;
		return 254 * 1024;
	}

	public static void deleteLocal(String localFilePath) {
		try {
			java.nio.file.Path p = java.nio.file.Paths.get(localFilePath);
			java.nio.file.Files.deleteIfExists(p);
			System.out.println("Deleted local temp file: " + localFilePath);
			log.info("Deleted local temp file: " + localFilePath);

		} catch (Exception e) {
			System.err.println("Failed to delete local temp file: " + e.getMessage());
			log.error("Failed to delete local temp file: " + e.getMessage());
			log.error(e);
		}
	}

	public String getProperty(String propName) {
		try {

			if (prop == null) {
				prop = Prop.getProp();
			}
			return prop.getProperty(propName);
		} catch (Exception e) {
			log.error(e);
		}
		return propName;
	}
}
