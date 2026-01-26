package com.example.sft;



import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.billdeskpro.EmailController.MailSenderController;
import com.example.sft.constants.GlobalConstants;
import com.example.sft.templates.Templates;

@Component
public class Emails {
	
	private final Logger log= LogManager.getLogger(Emails.class);
	
	static Properties prop = Prop.getProp();

	@Autowired
	Templates templates;

	public synchronized void filesNotFound (ConcurrentMap<String, List<String>> missingFiles,ConcurrentMap<String, List<String>> transferedFiles,String depart) {
		

			try {
				
				String toEmail = prop.getProperty("email." + depart);
				String subject = GlobalConstants.filesSubject;
				
				List<String> failureFiles=missingFiles.get(depart);
				List<String> successFiles=transferedFiles.get(depart);
				
				String body = templates.filesBody(failureFiles,successFiles, GlobalConstants.batchDate);
				
				String[] args = { "NCDEX", null, null,
						GlobalConstants.emailConfigPath, "n", null,
						null, subject, body , toEmail};
				try {
					MailSenderController.main(args);
					
					System.out.println("Sucessfully created email for "+depart);
					log.info("Sucessfully created email for "+depart);
				} catch (Exception e) {
					System.out.println("error occured while mailing "+e.getMessage());
					log.info("error occured while mailing "+e.getMessage());
				}
			}
			catch(Exception e) {
				log.error(e);
			}

	}

	public  synchronized void connectionIssue() {
		try {
			

			String toEmail =GlobalConstants.emailAll;
			String subject=GlobalConstants.connectionSubject;
			String body = templates.connectionBody(GlobalConstants.batchDate);
		
			String[] args = { "NCDEX", null, null,
					GlobalConstants.emailConfigPath, "n", null,
					null, subject, body , toEmail};
			try {
					MailSenderController.main(args);
				} catch (Exception e) {
					System.out.println("error occured while mailing "+e.getMessage());
					log.info("error occured while mailing "+e.getMessage());
				}
			System.exit(1);
		}
		catch(Exception e) {
			System.out.println(e.getMessage());
			log.info(e);
		}
	}

}
