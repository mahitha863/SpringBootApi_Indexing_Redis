package com.example.demo.beans;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

@Component
public class JedisBean {
	
	private static final String redisHostName = "localhost";
	private static final Integer redisPortNum = 6379;
	private static final String sep = "_";
	//the jedis connection pool object..
	private static JedisPool poolObj = null;
	
	public JedisBean() {
		poolObj = new JedisPool(redisHostName, redisPortNum);
	}
	
	public String addPlanObject(JSONObject jsonObj, String key) {
		try {
			if(!isKeyExist(key) && addAllNestedObjects(jsonObj, key))
				return "Created";
			else
				return "alreadyExists";
		}catch(JedisException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean addAllNestedObjects(JSONObject jsonObj, String key) {
		try {
			Jedis jedis = poolObj.getResource();
//			Map<String, String> tempMap = new HashMap<String, String>();
			JSONObject forIndividualObj = new JSONObject();
			
			for(Object k:jsonObj.keySet()) {
				String attrKey = String.valueOf(k);
				Object attrVal = jsonObj.get(attrKey);
				if(attrVal instanceof JSONObject) {
					JSONObject nestedObj = (JSONObject) attrVal;
					String nestedKey = key + sep + attrKey;
					String nestedId = nestedObj.get("objectType") + sep + nestedObj.get("objectId");
					jedis.sadd(nestedKey, nestedId); //this is set add
					addAllNestedObjects(nestedObj, nestedId);
				}else if(attrVal instanceof JSONArray) {
					JSONArray nestedArr = (JSONArray) attrVal;
					String nestedKey = key + sep + attrKey;
					Iterator<Object> arrObjIterator = nestedArr.iterator();
					while(arrObjIterator.hasNext()) {
						JSONObject nestedObj = (JSONObject) arrObjIterator.next();
						String nestedId = nestedObj.get("objectType") + sep + nestedObj.get("objectId");
						jedis.sadd(nestedKey, nestedId); //this is set add
						addAllNestedObjects(nestedObj, nestedId);
					}
				}else {
					forIndividualObj.put(attrKey, attrVal);
//					tempMap.put(attrKey,  (String) attrVal);
				}
			}
			
			
			jedis.set(key, forIndividualObj.toString());
//			jedis.hmset(key, tempMap);
			jedis.close();
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	
	public String getPlanObject(String key) {
		JSONObject jsonObj = getAllNestedObjects(key);
		if(jsonObj!=null) {
//			jsonObj.remove("eTag");
			return jsonObj.toString();
		}else {
			return null;
		}
	}
	
	private JSONObject getAllNestedObjects(String key) {
		try {
			Jedis jedis = poolObj.getResource();
			JSONObject jsonObj = new JSONObject();
			Set<String> keys = jedis.keys(key + sep + "*"); //returns all the keys that matches this pattern, * means any
			
			for(String k : keys) {
				Set<String> jsonKeySet = jedis.smembers(k); //returns all set members
				if(jsonKeySet.size()>1) { //if jsonarray goes here
					JSONArray jsonArr = new JSONArray();
					Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
					while(jsonKeySetIterator.hasNext()) {
						jsonArr.put(getAllNestedObjects(jsonKeySetIterator.next()));
					}
					jsonObj.put(k.substring(k.lastIndexOf(sep) + 1), jsonArr);
				}else {
					Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
					JSONObject nestedObject = null;
					while(jsonKeySetIterator.hasNext()) {
						nestedObject = getAllNestedObjects(jsonKeySetIterator.next());
					}
					jsonObj.put(k.substring(k.lastIndexOf(sep) + 1), nestedObject);
				}
			}
			
			/*Map<String,String> simpleMap = jedis.hgetAll(key);
			for(String simpleKey : simpleMap.keySet()) {
				jsonObj.put(simpleKey, simpleMap.get(simpleKey));
			}*/
			JSONObject simpleJsonObjs = new JSONObject(jedis.get(key));
			for(Object kin : simpleJsonObjs.keySet()) {
				jsonObj.put((String)kin, simpleJsonObjs.get((String) kin));
			}
			
			jedis.close();
			return jsonObj;
		}catch(JedisException e) {
			System.out.println("Error in this catch");
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean updatePatchPlanObject(JSONObject jsonObj) {
		try {
			Jedis jedis = poolObj.getResource();
			String key = jsonObj.get("objectType") + sep + jsonObj.get("objectId");
			
			/*Map<String,String> simpleObjMap = jedis.hgetAll(key);
			if(simpleObjMap.isEmpty()) {
				simpleObjMap = new HashMap<String,String>();
			}*/
			JSONObject simpleJsonObj = new JSONObject();
			if(jedis.get(key)!=null && !jedis.get(key).isEmpty()) {
				simpleJsonObj = new JSONObject(jedis.get(key));
			}
			
			for(Object k : jsonObj.keySet()) {
				String attrKey = String.valueOf(k);
				Object attrVal = jsonObj.get(attrKey);
				if(attrVal instanceof JSONObject) {
					JSONObject nestedObj = (JSONObject) attrVal;
					String nestedKey = key + sep + attrKey;
					String nestedId = nestedObj.get("objectType") + sep + nestedObj.get("objectId");
					jedis.sadd(nestedKey, nestedId);
					updatePatchPlanObject(nestedObj);
				}else if(attrVal instanceof JSONArray) {
					JSONArray nestedArr = (JSONArray) attrVal;
					Iterator<Object> arrObjIterator = nestedArr.iterator();
					String nestedKey = key + sep + attrKey;
					while(arrObjIterator.hasNext()) {
						JSONObject nestedObj = (JSONObject) arrObjIterator.next();
						String nestedId = nestedObj.get("objectType") + sep + nestedObj.get("objectId");
						jedis.sadd(nestedKey, nestedId);
						updatePatchPlanObject(nestedObj);
					}
				}else {
//					simpleObjMap.put(attrKey, String.valueOf(attrVal));
					simpleJsonObj.put(attrKey, attrVal);
				}
			}
			
			jedis.set(key, simpleJsonObj.toString());
//			jedis.hmset(key, simpleObjMap);
			jedis.close();
			
			return true;
		}catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean deletePlanObject(String key) {
		return deleteAllNestedObjects(key);
	}
	
	private boolean deleteAllNestedObjects(String key) {
		try {
			Jedis jedis = poolObj.getResource();
			
			Set<String> allKeys = jedis.keys(key + sep + "*");
			for(String k : allKeys) {
				Set<String> jsonKeySet = jedis.smembers(k);
				for(String nestedKey : jsonKeySet) {
					deleteAllNestedObjects(nestedKey);
				}
				jedis.del(k);
			}
			
			jedis.del(key);
			jedis.close();
			return true;
		}catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public String addEtag(JSONObject jsonObj, String key) {
		try {
			String newEtag = DigestUtils.md5Hex(jsonObj.toString().getBytes());
			Jedis jedis = poolObj.getResource();
			jedis.set(key+"eTag", newEtag);
//			jedis.hset(key, "eTag", newEtag);
			jedis.close();
			return newEtag;
		} catch(JedisException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public String getEtag(String key) {
		try {
			Jedis jedis = poolObj.getResource();
			String etag = jedis.get(key+"eTag");
//			String etag = jedis.hget(key, "eTag");
			jedis.close();
			return etag;
		} catch(JedisException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean isKeyExist(String key) {
		try {
			Jedis jedisCh = poolObj.getResource();
			if(jedisCh.exists(key) &&  !jedisCh.keys(key).isEmpty()) {
				jedisCh.close();
				return true;
			}else {
				return false;
			}
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}

}
