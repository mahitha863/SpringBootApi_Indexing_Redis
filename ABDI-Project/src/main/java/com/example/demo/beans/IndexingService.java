package com.example.demo.beans;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Configuration
public class IndexingService {
	
	private static final String redisHostName = "localhost";
	private static final Integer redisPortNum = 6379;
	private static JedisPool poolObj = new JedisPool(redisHostName, redisPortNum);
	
	private static RestHighLevelClient restHighLevelClient = 
			new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200)).setRequestConfigCallback(requestConfigBuilder->
			requestConfigBuilder.setConnectTimeout(5000).setSocketTimeout(5000)));
	
	private static final String indexName = "plan_index";
	
	
	
	@EventListener(ApplicationReadyEvent.class)
	public void indexing() {
		while(true) {
			Jedis jedis = poolObj.getResource();
			byte[] bytes = jedis.rpoplpush("messageQueue".getBytes(), "WorkingMQ".getBytes());
			if (bytes != null && bytes.length !=0) {
				JSONObject result = new JSONObject(new String(bytes));
				
				boolean isDelete = Boolean.parseBoolean(result.get("isDelete").toString());
				
				if(!isDelete) {
					if(!indexExists()) {
						createElasticIndexWithMapping();
					}
					JSONObject plan= new JSONObject(result.get("message").toString());
					IndexRequest request = new IndexRequest(indexName);
					if(plan.has("parent_id")) {
						request.routing(plan.getString("parent_id"));
						plan.remove("parent_id");
					}
					
					request.source(plan.toString(), XContentType.JSON);
					request.id(plan.getString("objectId"));
					try {
						restHighLevelClient.index(request, RequestOptions.DEFAULT);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}else {
					deleteDocument(result.get("message").toString());
				}
			}
			
			
		    jedis.close();
		}
	}
	
	
	private static void deleteDocument(String documentId) {
		DeleteRequest request = new DeleteRequest(indexName, documentId);
		try {
			restHighLevelClient.delete(request, RequestOptions.DEFAULT);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private static boolean indexExists() {
		boolean exists=false;
		try {
			exists = restHighLevelClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return exists;
	}
	
	
	private static void createElasticIndexWithMapping() {
		CreateIndexRequest request = new CreateIndexRequest(indexName);
		request.settings(Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 2));
		

		try {
			//create mapping for parent-child relationship
			File schemaFile = new File("src/main/resources/IndexMapping.json");
			JSONTokener schemaData = new JSONTokener(new FileInputStream(schemaFile));
			JSONObject jsonObject = new JSONObject(schemaData);
			String mapping = jsonObject.toString();
			request.mapping(new JSONObject(mapping).toString(), XContentType.JSON);
			restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
			
			
			
}
