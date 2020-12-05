# SpringBootApi_Indexing_Redis
The project contains the following:
1. Spring boot based Rest-api to parse the given input json
2. Save the json payload in Redis
3. In Rest-API POST, GET, PATCH, PUT, DELETE are implemented
4. Rest API is secured with a JWT token
5. For Queueing, used Redis Queue
6. Created index in elasticsearch and stored the json pay load as documents having parent-child relationship

Servers used to run the project:
1. Redis
2. Elastic search
3. Kibana(for ES console)
