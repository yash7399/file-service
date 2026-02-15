package com.ncdex.filetransfer.connections;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.ncdex.filetransfer.emails.Emails;
import com.ncdex.filetransfer.utils.Prop;

@Component
public class SFTPConnection {

	private static final Logger log = LogManager.getLogger(SFTPConnection.class);

	static Properties prop = null;

	static Emails emails;

	public SFTPConnection(Emails emails) {
		SFTPConnection.emails = emails;
	}

	public static ChannelSftp connect(String serverId) {
		try {
			
			Session session = null;
			ChannelSftp channel = null;

			JSch jsch = new JSch();

			jsch.addIdentity(getProperty(serverId + ".privateKey"));

			session = jsch.getSession(getProperty(serverId + ".user"), getProperty(serverId + ".ip"),
					Integer.parseInt(getProperty(serverId + ".port")));

			
//			session.setPassword(getProperty(serverId + ".password"));
			
			session.setServerAliveInterval(60_000);
			session.setServerAliveCountMax(5);

			Properties cfg = new Properties();
			cfg.put("StrictHostKeyChecking", "no");
			cfg.put("compression.s2c", "none");
			cfg.put("compression.c2s", "none");
			cfg.put("max_input_buffer_size", "262144");
			session.setConfig(cfg);

			

			session.connect();

			channel = (ChannelSftp) session.openChannel("sftp");

			channel.connect();

			channel.setBulkRequests(256);

			System.out.println("NSE connected successfully ");
			log.info("NSE connected successfully ");

			return channel;
		} catch (Exception e) {
			System.out.println("here");
			System.out.println("here");
			log.error("Unable to connect with NSE server " + e.getMessage());
			log.error(e);
			System.out.println("here 2");
			emails.connectionIssue();
		}
		return null;

	}

	static public String getProperty(String propName) {
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
