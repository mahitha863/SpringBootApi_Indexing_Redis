package com.example.demo.beans;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
public class MessageQueueService {
	
	private RestHighLevelClient restHighLevelClient = 
			new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200)).setRequestConfigCallback(requestConfigBuilder->
			requestConfigBuilder.setConnectTimeout(5000).setSocketTimeout(5000)));
	
	public void addToMessageQueue(String messageData, boolean isDelete) {
		JSONObject obj = new JSONObject();
		obj.put("message", messageData);
		obj.put("isDelete", isDelete);

		// save plan to message queue "messageQueue" in redis
		JedisPool poolObj = new JedisPool("localhost", 6379);
		Jedis jedis = poolObj.getResource();
		jedis.lpush("messageQueue".getBytes(), obj.toString().getBytes(StandardCharsets.UTF_8));
		jedis.close();
		poolObj.close();
	}
	
	
	
	public boolean isIndexExist(String indexName) {
		boolean exists=false;
		try {
			exists = restHighLevelClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return exists;
	}

}
