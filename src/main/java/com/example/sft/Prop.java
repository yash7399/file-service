package com.example.sft;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import com.example.sft.Util;

import javax.management.RuntimeErrorException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Prop {
	private static Properties prop = new Properties();
	
	public static Properties init(String path) {
		    	
		    	try {
		    		System.out.println(path);
		    		InputStream input=new FileInputStream( path );
		    		
		    		prop.load(input);
		    	}
		    	catch(Exception e){
		    		
		    		System.out.println("Failed to load config"+ e.getMessage());
		    		
		    	}
				return prop;
	}
	
	public static Properties getProp() {
		return prop;
	}
}
