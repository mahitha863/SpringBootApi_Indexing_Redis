package com.example.demo.controller;

import java.io.FileNotFoundException;
import java.text.ParseException;

import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.beans.GetAndValidateToken;
import com.example.demo.beans.JedisBean;
import com.example.demo.beans.MyJsonSchemaValidator;
import com.nimbusds.jose.JOSEException;

@RestController
public class PlanController {
	
	@Autowired
	private JedisBean jedisObj;
	
	@Autowired
	private MyJsonSchemaValidator validateSchem;
	
	@Autowired
	private GetAndValidateToken tokenService;
	
	@RequestMapping(path="/")
	public String home() {
		return "INFO7255 ABDI Project";
	}
	
	@PostMapping(path="/plan", consumes = "application/json", produces = "application/json")
	public ResponseEntity<Object> createPlan(@RequestBody String planBodyStr, 
			@RequestHeader HttpHeaders headers) throws FileNotFoundException, ParseException, JOSEException{
		
		String returnValue = tokenService.authorizeToken(headers);
        if ((returnValue != "Valid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());
		
		JSONObject inputJsonObj = new JSONObject(planBodyStr);
		try {
			validateSchem.validateMySchema(planBodyStr);
		}catch(ValidationException e) {
			String res = "{\"Status\": \"Failed\",\"Message\": \"Validation of input JSON failed.\",\"Error\": \"" + e.getMessage()+ "\"}";
			//e.getErrorMessage() ; e.getPointerToViolation()
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject(res).toString());
		}
		
		String key = inputJsonObj.getString("objectType") + "_" + inputJsonObj.getString("objectId");
		String created = jedisObj.addPlanObject(inputJsonObj, key);
		if(created.equalsIgnoreCase("alreadyExists")) {
			String res = "{\"Status\": \"Failed\",\"Message\": \"A plan with objectId already exists.\"}";
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject(res).toString());
		}
		
		
		String eTag = jedisObj.addEtag(inputJsonObj, key);
		
		
		String res = "{\"status\": \"Success\", \"message\": \"Created data with objectId: " + inputJsonObj.get("objectId") + "\" }";
		return ResponseEntity.ok().eTag(eTag).body(new JSONObject(res).toString());
	}
	
	@GetMapping(path="/{type}/{objectId}", produces = "application/json")
	public ResponseEntity<Object> retrievePlan(@RequestHeader HttpHeaders headers, 
			@PathVariable("type") String type, @PathVariable("objectId") String id ) throws ParseException, JOSEException{
		
		String returnValue = tokenService.authorizeToken(headers);
        if ((returnValue != "Valid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());
		
		String key = type + "_" + id;
		
		if (!jedisObj.isKeyExist(key)) {
			String res = "{\"status\": \"Not Found\",\"Message\": \"Plan with objectId does not exist.\"}";
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject(res).toString());
        }
		
		String savedEtag = null;
		if(type.equals("plan")) { //checks if etag and if-none-match are equal
			savedEtag = jedisObj.getEtag(key);
			String etag = headers.getFirst("If-None-Match");
			if(etag!=null && etag.equals(savedEtag)) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(savedEtag).build();
			}
		}
		
		String plan = jedisObj.getPlanObject(key);
		
		if(type.equals("plan")) {
			return ResponseEntity.ok().eTag(savedEtag).body(plan);
		}
		return ResponseEntity.ok().body(plan);
		
	}
	
	@PatchMapping(path="plan/{objectId}", consumes = "application/json", produces = "application/json")
	public ResponseEntity<Object> patchPlan(@RequestBody String planBodyStr, @RequestHeader HttpHeaders headers, 
			@PathVariable("objectId") String objectId) throws ParseException, JOSEException {
		
		String returnValue = tokenService.authorizeToken(headers);
        if ((returnValue != "Valid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());
		
		JSONObject inputJsonObj = new JSONObject(planBodyStr);
		String key = "plan_" + objectId;
		
		//check if objectId exists or not
		if(!jedisObj.isKeyExist(key)) {
			String res = "{\"status\": \"Not Found\",\"Message\": \"Plan with objectId does not exist.\"}";
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject(res).toString());
        }
		
		//check etag preconditions
		String savedEtag = jedisObj.getEtag(key);
		String userEtag = headers.getFirst("If-Match");
		if(userEtag==null) {
			String res = "{\"status\": \"Etag Not Found\",\"Message\": \"Please provide the etag information.\"}";
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject(res).toString());
		}
		else if(!userEtag.equals(savedEtag)) {
			String res = "{\"status\": \"Etag not correct\",\"Message\": \"Data has been changed. Please get the latest Etag information and then try to update.\"}";
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(new JSONObject(res).toString());
		}
		
		if(jedisObj.updatePatchPlanObject(inputJsonObj)) {
			String planAfterUpdate = jedisObj.getPlanObject(key);
			
			//validate the json after update (commented bcoz validation may not be needed as patch itself mean changing the payload)
			/*try {
				validateSchem.validateMySchema(planAfterUpdate);
			}catch(ValidationException e) {
				String res = "{\"Status\": \"Failed\",\"Message\": \"Validation of JSON failed with the patch.\",\"Error\": \"" + e.getMessage()+ "\"}";
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject(res).toString());
			}*/
			
			//create a new etag after update with patch
			String eTag = jedisObj.addEtag(new JSONObject(planAfterUpdate), key);
			String res = "{\"status\": \"Success\", \"message\": \"Updated data for objectId: " + objectId + "\" }";
			return ResponseEntity.ok().eTag(eTag).body(new JSONObject(res).toString());
		} else {
			String res = "{\"Status\": \"Failed\",\"Message\": \"Patch update failed.\"}";
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JSONObject(res).toString());
		}
		
	}
	
	@PutMapping(path="/plan/{objectId}", consumes = "application/json", produces = "application/json")
	public ResponseEntity<Object> putPlan(@RequestBody String planBodyStr, @RequestHeader HttpHeaders headers, 
			@PathVariable("objectId") String objectId) throws FileNotFoundException, ParseException, JOSEException {
		
		String returnValue = tokenService.authorizeToken(headers);
        if ((returnValue != "Valid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());
		
		String key = "plan_" + objectId;
		
		//check if objectId exists or not
		if(!jedisObj.isKeyExist(key)) {
			String res = "{\"status\": \"Not Found\",\"Message\": \"Plan with objectId does not exist.\"}";
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject(res).toString());
        }
		
		//check etag preconditions
		String savedEtag = jedisObj.getEtag(key);
		String userEtag = headers.getFirst("If-Match");
		if(userEtag==null) {
			String res = "{\"status\": \"Etag Not Found\",\"Message\": \"Please provide the etag information.\"}";
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new JSONObject(res).toString());
		}
		else if(!userEtag.equals(savedEtag)) {
			String res = "{\"status\": \"Etag not correct\",\"Message\": \"Data has been changed. Please get the latest Etag information and then try to update.\"}";
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(new JSONObject(res).toString());
		}
		
		
		JSONObject inputJsonObj = new JSONObject(planBodyStr);
		try {
			validateSchem.validateMySchema(planBodyStr);
		}catch(ValidationException e) {
			String res = "{\"Status\": \"Failed\",\"Message\": \"Validation of input JSON failed.\",\"Error\": \"" + e.getMessage()+ "\"}";
			//e.getErrorMessage() ; e.getPointerToViolation()
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject(res).toString());
		}
		
		
		if(jedisObj.deletePlanObject(key) && jedisObj.addAllNestedObjects(inputJsonObj, key)) {
			String eTag = jedisObj.addEtag(inputJsonObj, key);

			String res = "{\"status\": \"Success\", \"message\": \"Updated data with objectId: " + objectId + "\" }";
			return ResponseEntity.ok().eTag(eTag).body(new JSONObject(res).toString());
		}else {
			String res = "{\"Status\": \"Failed\",\"Message\": \"Put update failed.\"}";
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JSONObject(res).toString());
		}
	}
	
	@DeleteMapping(path="/plan/{objectId}", produces = "application/json")
	public ResponseEntity<Object> deletePlan(@PathVariable String objectId, @RequestHeader HttpHeaders headers) throws ParseException, JOSEException{
		
		String returnValue = tokenService.authorizeToken(headers);
        if ((returnValue != "Valid Token"))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Authetication Error: ", returnValue).toString());
		
		String key = "plan_" + objectId;
		if(!jedisObj.isKeyExist(key)) {
			String res = "{\"status\": \"Not Found\",\"Message\": \"Plan with objectId does not exist.\"}";
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject(res).toString());
        }
		if(jedisObj.deletePlanObject(key)) {
			String res = "{\"status\": \"Success\", \"message\": \"Deleted data with objectId: " + objectId + "\" }";
			return ResponseEntity.ok().body(new JSONObject(res).toString());
		}else {
			String res = "{\"status\": \"Failed\", \"message\": \"Data with objectId: " + objectId + " not deleted successfully\" }";
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject(res).toString());
		}
	}
	
	@GetMapping(path="/token", produces = "application/json")
	public ResponseEntity<Object> getToken() throws JOSEException{
		
		String token = tokenService.getToken();
		
		String res = "{\"status\": \"Success\", \"token\":  \"" + token + "\" }";
		
		return ResponseEntity.status(HttpStatus.CREATED).body(new JSONObject(res).toString());
	}

}
