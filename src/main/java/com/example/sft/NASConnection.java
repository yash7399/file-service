package com.example.sft;

import com.example.sft.constants.GlobalConstants;
//import com.example.sft.util.FatalShutdownHandler;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;


import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class NASConnection {

	static SMBClient client;
	
	static Emails emails;

	public NASConnection(Emails emails) {
		NASConnection.emails=emails;
	}
	
	static {
		SmbConfig config = SmbConfig.builder().withTimeout(60, TimeUnit.MINUTES).withSoTimeout(60, TimeUnit.MINUTES).build();
		client= new SMBClient(config);
	}
	
	private static final Logger log = LogManager.getLogger(NASConnection.class);

	public static Session connect()  {
		
		try {
			
			Connection connection = client.connect(GlobalConstants.dcHost);

			AuthenticationContext auth = new AuthenticationContext(GlobalConstants.dcUser,
					GlobalConstants.dcPass.toCharArray(), "");

			Session session = connection.authenticate(auth);

			System.out.println("Connected to DC NAS successfully!");
			log.info("Connected to DC NAS successfully!");

			return session;

		} catch (Exception e) {
			System.out.println("DC connection failed");
			e.printStackTrace();
			log.info("DC connection failed");
			System.out.println("Trying to connect with DR");
			log.info("Trying to connect with DR");
			try {
				Connection connection = client.connect(GlobalConstants.drHost);

				AuthenticationContext auth = new AuthenticationContext(GlobalConstants.drUser,
						GlobalConstants.drPass.toCharArray(), "");

				Session session = connection.authenticate(auth);
				System.out.println("Connected to DR NAS successfully!");
				log.info("Connected to DR NAS successfully!");

				return session;
			}

			catch(Exception ex) {
				log.error("Unable to connect with NAS server"+e.getMessage());
				log.error(e);
				System.out.println("Unable to connect with NAS server");
				emails.connectionIssue();
			}
		}
		return null;

	}

}