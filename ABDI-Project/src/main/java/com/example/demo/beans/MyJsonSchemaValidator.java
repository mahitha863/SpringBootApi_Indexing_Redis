package com.example.demo.beans;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

@Service
public class MyJsonSchemaValidator {
	
	
	public void validateMySchema(String jsonData) throws FileNotFoundException, ValidationException {
		//validate plan
		File schemaFile = new File("src/main/resources/Schema.json");
		JSONTokener schemaData = new JSONTokener(new FileInputStream(schemaFile));
		JSONObject jsonSchema = new JSONObject(schemaData);
		
		JSONObject inputJsonObj = new JSONObject(jsonData);
		
		Schema schemaValidator = SchemaLoader.load(jsonSchema);
		schemaValidator.validate(inputJsonObj);
	}
	
	

}
