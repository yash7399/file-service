package com.example.sft;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.sft.constants.GlobalConstants;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@Component
public class SFTPConnection {

	private static final Logger log = LogManager.getLogger(SFTPConnection.class);
	
	static Properties prop= Prop.getProp();
	
	static Emails emails;

	public SFTPConnection(Emails emails) {
		SFTPConnection.emails=emails;
	}
	public static ChannelSftp connect(String serverId) {
		try {
			Session session = null;
			Channel channel = null;
			
			JSch jsch = new JSch();
			
			jsch.addIdentity(prop.getProperty(serverId+".privateKey"));
			
			session = jsch.getSession(prop.getProperty(serverId+".user"), prop.getProperty(serverId+".ip"), Integer.parseInt(prop.getProperty(serverId+".port")));
			
			session.setServerAliveInterval(300_000);
			session.setServerAliveCountMax(5);
			
			session.setConfig("StrictHostKeyChecking", "no");
			
			session.connect();

			channel = session.openChannel("sftp");
			channel.connect();
			
			System.out.println("NSE connected successfully ");
			log.info("NSE connected successfully ");
			
			return (ChannelSftp) channel;
		}
		catch(Exception e) {
			log.error("Unable to connect with NSE server "+e.getMessage());
			log.error(e);
			emails.connectionIssue();
		}
		return null;
		
	}
}
